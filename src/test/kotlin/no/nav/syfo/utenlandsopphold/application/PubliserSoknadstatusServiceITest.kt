package no.nav.syfo.utenlandsopphold.application

import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.vedtakDocument
import no.nav.syfo.utenlandsopphold.domain.veileder
import no.nav.syfo.utenlandsopphold.infrastructure.database.JdbcTransactionManager
import no.nav.syfo.utenlandsopphold.infrastructure.database.TestDatabase
import no.nav.syfo.utenlandsopphold.infrastructure.database.dropData
import no.nav.syfo.utenlandsopphold.infrastructure.database.repository.SoknadRepository
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.Soknadstatus
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.SoknadstatusRecord
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tester PubliserSoknadstatusService, både med mocket ISoknadRepository (rene enhetstester av
 * orkestreringslogikken) og mot en ekte database (via SoknadRepository/TestDatabase) med kun
 * Kafka-produsenten mocket, for å verifisere samspillet med reelle spørringer.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PubliserSoknadstatusServiceITest {
    private val database = TestDatabase()
    private val repository = SoknadRepository(database = database)
    private val transactionManager = JdbcTransactionManager(database = database)
    private val soknadstatusProducerMock = mockk<ISoknadstatusProducer>()

    private val service =
        PubliserSoknadstatusService(
            soknadRepository = repository,
            soknadstatusProducer = soknadstatusProducerMock,
        )

    private val repositoryMock = mockk<ISoknadRepository>()
    private val serviceMedMocketRepository =
        PubliserSoknadstatusService(
            soknadRepository = repositoryMock,
            soknadstatusProducer = soknadstatusProducerMock,
        )

    @BeforeEach
    fun resetMocks() {
        clearMocks(repositoryMock, soknadstatusProducerMock)
    }

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @AfterAll
    fun stop() {
        database.stop()
    }

    private fun soknad(): Soknad =
        Soknad(
            id = UUID.randomUUID(),
            eksternId = UUID.randomUUID(),
            personident = Personident("11111111111"),
            soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10))),
            innsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
        )

    private fun lagreSoknadMedVedtak(soknad: Soknad): Soknad {
        repository.lagreMottattSoknad(soknad)
        val soknadMedVedtak =
            soknad.fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = Navident("Z999999"),
                now = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
                document =
                    listOf(
                        DocumentComponent(
                            type = DocumentComponentType.HEADER_H1,
                            title = "Vedtak",
                            texts = listOf("Søknaden din er innvilget"),
                        ),
                    ),
                begrunnelse = null,
            )
        return transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = soknadMedVedtak.vedtak))
        }
    }

    private fun soknadMedVedtak(): Soknad =
        lagSoknad().fattVedtak(
            utfall = Utfall.Innvilget,
            fattetAv = veileder,
            now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            document = vedtakDocument,
            begrunnelse = null,
        )

    @Test
    fun `publiserMottatteSoknader publiserer og markerer soknad som publisert i databasen`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)

        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        val resultater = service.publiserMottatteSoknader()

        assertEquals(1, resultater.size)
        assertTrue(resultater.single().isSuccess)
        assertTrue(repository.getUpubliserteSoknader().isEmpty())
        verify(exactly = 1) {
            soknadstatusProducerMock.publiser(match { it.uuid == soknad.eksternId && it.status == Soknadstatus.MOTTATT })
        }
    }

    @Test
    fun `publiserMottatteSoknader publiserer ikke soknad på nytt etter den er markert som publisert`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)
        service.publiserMottatteSoknader()

        val andreKjoring = service.publiserMottatteSoknader()

        assertTrue(andreKjoring.isEmpty())
        verify(exactly = 1) { soknadstatusProducerMock.publiser(any()) }
    }

    @Test
    fun `publiserMottatteSoknader markerer ikke soknad som publisert når produsenten feiler`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        every { soknadstatusProducerMock.publiser(any()) } returns Result.failure(RuntimeException("kafka er nede"))

        val resultater = service.publiserMottatteSoknader()

        assertTrue(resultater.single().isFailure)
        assertEquals(1, repository.getUpubliserteSoknader().size)
    }

    @Test
    fun `publiserBehandledeSoknader publiserer og markerer vedtak som publisert i databasen`() {
        val soknadMedVedtak = lagreSoknadMedVedtak(soknad())
        repository.setSoknadPublisert(soknadMedVedtak.id, OffsetDateTime.now())
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        val resultater = service.publiserBehandledeSoknader()

        assertEquals(1, resultater.size)
        assertTrue(resultater.single().isSuccess)
        assertTrue(repository.getSoknaderMedUpublisertVedtak().isEmpty())
        verify(exactly = 1) {
            soknadstatusProducerMock.publiser(match { it.status == Soknadstatus.BEHANDLET && it.vedtak != null })
        }
    }

    @Test
    fun `publiserBehandledeSoknader publiserer ikke vedtak for soknad som ikke er MOTTATT-publisert`() {
        lagreSoknadMedVedtak(soknad())
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        val resultater = service.publiserBehandledeSoknader()

        assertTrue(resultater.isEmpty())
        verify(exactly = 0) { soknadstatusProducerMock.publiser(any()) }
    }

    @Test
    fun `publiserMottatteSoknader publiserer og oppdaterer upubliserte soknader (mocket repository)`() {
        val soknad = lagSoknad()

        every { repositoryMock.getUpubliserteSoknader() } returns listOf(soknad)
        every { repositoryMock.setSoknadPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        val resultater = serviceMedMocketRepository.publiserMottatteSoknader()

        assertTrue(resultater.single().isSuccess)
        verify(exactly = 1) { soknadstatusProducerMock.publiser(SoknadstatusRecord.fromSoknad(soknad)) }
        verify(exactly = 1) { repositoryMock.setSoknadPublisert(soknad.id, any()) }
    }

    @Test
    fun `feil for en soknad stopper ikke publisering av MOTTATT for de andre (mocket repository)`() {
        val soknadSomFeiler = lagSoknad()
        val soknadSomLykkes = lagSoknad()

        every { repositoryMock.getUpubliserteSoknader() } returns listOf(soknadSomFeiler, soknadSomLykkes)
        every { repositoryMock.setSoknadPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returnsMany
            listOf(
                Result.failure(RuntimeException("kafka er nede")),
                Result.success(Unit),
            )

        val resultater = serviceMedMocketRepository.publiserMottatteSoknader()

        assertTrue(resultater[0].isFailure)
        assertTrue(resultater[1].isSuccess)
        verify(exactly = 1) { repositoryMock.setSoknadPublisert(soknadSomLykkes.id, any()) }
        verify(exactly = 0) { repositoryMock.setSoknadPublisert(soknadSomFeiler.id, any()) }
    }

    @Test
    fun `publiserBehandledeSoknader publiserer og oppdaterer soknader med upublisert vedtak (mocket repository)`() {
        val soknad = soknadMedVedtak()

        every { repositoryMock.getSoknaderMedUpublisertVedtak() } returns listOf(soknad)
        every { repositoryMock.setVedtakPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        val resultater = serviceMedMocketRepository.publiserBehandledeSoknader()

        assertTrue(resultater.single().isSuccess)
        verify(exactly = 1) { soknadstatusProducerMock.publiser(SoknadstatusRecord.fromSoknadMedVedtak(soknad)) }
        verify(exactly = 1) { repositoryMock.setVedtakPublisert(soknad.vedtak!!.vedtakId, any()) }
    }

    @Test
    fun `feil for en soknad stopper ikke publisering av BEHANDLET for de andre (mocket repository)`() {
        val soknadSomFeiler = soknadMedVedtak()
        val soknadSomLykkes = soknadMedVedtak()

        every { repositoryMock.getSoknaderMedUpublisertVedtak() } returns listOf(soknadSomFeiler, soknadSomLykkes)
        every { repositoryMock.setVedtakPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returnsMany
            listOf(
                Result.failure(RuntimeException("kafka er nede")),
                Result.success(Unit),
            )

        serviceMedMocketRepository.publiserBehandledeSoknader()

        verify(exactly = 1) { repositoryMock.setVedtakPublisert(soknadSomLykkes.vedtak!!.vedtakId, any()) }
        verify(exactly = 0) { repositoryMock.setVedtakPublisert(soknadSomFeiler.vedtak!!.vedtakId, any()) }
    }

    @Test
    fun `soknad uten vedtak publiseres ikke som BEHANDLET, men stopper ikke andre (mocket repository)`() {
        val soknadUtenVedtak = lagSoknad()
        val soknadMedVedtak = soknadMedVedtak()

        every { repositoryMock.getSoknaderMedUpublisertVedtak() } returns listOf(soknadUtenVedtak, soknadMedVedtak)
        every { repositoryMock.setVedtakPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        serviceMedMocketRepository.publiserBehandledeSoknader()

        verify(exactly = 1) { repositoryMock.setVedtakPublisert(soknadMedVedtak.vedtak!!.vedtakId, any()) }
        verify(exactly = 1) { repositoryMock.setVedtakPublisert(any(), any()) }
    }
}
