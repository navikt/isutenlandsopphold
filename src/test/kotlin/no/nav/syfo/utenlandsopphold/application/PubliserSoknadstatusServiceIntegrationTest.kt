package no.nav.syfo.utenlandsopphold.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.infrastructure.database.JdbcTransactionManager
import no.nav.syfo.utenlandsopphold.infrastructure.database.TestDatabase
import no.nav.syfo.utenlandsopphold.infrastructure.database.dropData
import no.nav.syfo.utenlandsopphold.infrastructure.database.repository.SoknadRepository
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.Soknadstatus
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PubliserSoknadstatusServiceIntegrationTest {
    private val database = TestDatabase()
    private val repository = SoknadRepository(database = database)
    private val transactionManager = JdbcTransactionManager(database = database)
    private val soknadstatusProducerMock = mockk<ISoknadstatusProducer>()

    private val service =
        PubliserSoknadstatusService(
            soknadRepository = repository,
            soknadstatusProducer = soknadstatusProducerMock,
        )

    @BeforeEach
    fun resetMocks() {
        clearMocks(soknadstatusProducerMock)
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
}
