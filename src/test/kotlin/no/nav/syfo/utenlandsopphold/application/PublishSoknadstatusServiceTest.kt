package no.nav.syfo.utenlandsopphold.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.infrastructure.database.JdbcTransactionManager
import no.nav.syfo.utenlandsopphold.infrastructure.database.TestDatabase
import no.nav.syfo.utenlandsopphold.infrastructure.database.dropData
import no.nav.syfo.utenlandsopphold.infrastructure.database.repository.SoknadRepository
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.Soknadstatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishSoknadstatusServiceTest {
    private val database = TestDatabase()
    private val repository = SoknadRepository(database = database)
    private val transactionManager = JdbcTransactionManager(database = database)
    private val soknadstatusProducerMock = mockk<ISoknadstatusProducer>()

    private val service =
        PublishSoknadstatusService(
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

    private fun soknad(personident: Personident = Personident("11111111111")) = lagSoknad().copy(personident = personident)

    private fun lagreBehandletSoknad(soknad: Soknad): Soknad {
        repository.lagreMottattSoknad(soknad)
        val behandletSoknad =
            soknad.fattVedtak(
                utfall = Utfall.Innvilget,
                behandletAv = Navident("Z999999"),
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
            repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = behandletSoknad.behandling))
        }
    }

    @Test
    fun `publishMottatteSoknader publiserer og markerer soknad som publisert i databasen`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)

        every { soknadstatusProducerMock.publish(any()) } returns Result.success(Unit)

        val resultater = service.publishMottatteSoknader()

        assertEquals(1, resultater.size)
        assertTrue(resultater.single().isSuccess)
        assertTrue(repository.getUnpublishedSoknader().isEmpty())
        verify(exactly = 1) {
            soknadstatusProducerMock.publish(match { it.uuid == soknad.eksternId && it.status == Soknadstatus.MOTTATT })
        }
    }

    @Test
    fun `publishMottatteSoknader publiserer ikke soknad på nytt etter den er markert som publisert`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        every { soknadstatusProducerMock.publish(any()) } returns Result.success(Unit)
        service.publishMottatteSoknader()

        val andreKjoring = service.publishMottatteSoknader()

        assertTrue(andreKjoring.isEmpty())
        verify(exactly = 1) { soknadstatusProducerMock.publish(any()) }
    }

    @Test
    fun `publishMottatteSoknader markerer ikke soknad som publisert når produsenten feiler`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        every { soknadstatusProducerMock.publish(any()) } returns Result.failure(RuntimeException("kafka er nede"))

        val resultater = service.publishMottatteSoknader()

        assertTrue(resultater.single().isFailure)
        assertEquals(1, repository.getUnpublishedSoknader().size)
    }

    @Test
    fun `publishBehandledeSoknader publiserer og markerer behandling som publisert i databasen`() {
        val behandletSoknad = lagreBehandletSoknad(soknad())
        repository.setSoknadPublished(behandletSoknad.id, OffsetDateTime.now())
        every { soknadstatusProducerMock.publish(any()) } returns Result.success(Unit)

        val resultater = service.publishBehandledeSoknader()

        assertEquals(1, resultater.size)
        assertTrue(resultater.single().isSuccess)
        assertTrue(repository.getSoknaderMedUnpublishedBehandling().isEmpty())
        verify(exactly = 1) {
            soknadstatusProducerMock.publish(match { it.status == Soknadstatus.BEHANDLET && it.behandling != null })
        }
    }

    @Test
    fun `publishBehandledeSoknader publiserer ikke behandling for soknad som ikke er MOTTATT-publisert`() {
        lagreBehandletSoknad(soknad())
        every { soknadstatusProducerMock.publish(any()) } returns Result.success(Unit)

        val resultater = service.publishBehandledeSoknader()

        assertTrue(resultater.isEmpty())
        verify(exactly = 0) { soknadstatusProducerMock.publish(any()) }
    }

    @Test
    fun `feil for en soknad stopper ikke publisering av MOTTATT for de andre`() {
        val soknadSomFeiler = soknad()
        val soknadSomLykkes = soknad()
        repository.lagreMottattSoknad(soknadSomFeiler)
        repository.lagreMottattSoknad(soknadSomLykkes)

        every { soknadstatusProducerMock.publish(match { it.uuid == soknadSomFeiler.eksternId }) } returns
            Result.failure(RuntimeException("kafka er nede"))
        every { soknadstatusProducerMock.publish(match { it.uuid == soknadSomLykkes.eksternId }) } returns
            Result.success(Unit)

        val resultater = service.publishMottatteSoknader()

        assertEquals(1, resultater.count { it.isFailure })
        assertEquals(1, resultater.count { it.isSuccess })
        val upubliserte = repository.getUnpublishedSoknader()
        assertEquals(1, upubliserte.size)
        assertEquals(soknadSomFeiler.id, upubliserte.single().id)
    }

    @Test
    fun `feil for en soknad stopper ikke publisering av BEHANDLET for de andre`() {
        val soknadSomFeiler = lagreBehandletSoknad(soknad())
        val soknadSomLykkes = lagreBehandletSoknad(soknad(Personident("22222222222")))
        repository.setSoknadPublished(soknadSomFeiler.id, OffsetDateTime.now())
        repository.setSoknadPublished(soknadSomLykkes.id, OffsetDateTime.now())

        every { soknadstatusProducerMock.publish(match { it.uuid == soknadSomFeiler.eksternId }) } returns
            Result.failure(RuntimeException("kafka er nede"))
        every { soknadstatusProducerMock.publish(match { it.uuid == soknadSomLykkes.eksternId }) } returns
            Result.success(Unit)

        val resultater = service.publishBehandledeSoknader()

        assertEquals(1, resultater.count { it.isFailure })
        assertEquals(1, resultater.count { it.isSuccess })
        val upubliserte = repository.getSoknaderMedUnpublishedBehandling()
        assertEquals(1, upubliserte.size)
        assertEquals(soknadSomFeiler.id, upubliserte.single().id)
    }
}
