package no.nav.syfo.utenlandsopphold.application

import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.vedtakDocument
import no.nav.syfo.utenlandsopphold.domain.veileder
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.SoknadstatusRecord
import org.junit.jupiter.api.BeforeEach
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertTrue

class PubliserSoknadstatusServiceTest {
    private val repositoryMock = mockk<ISoknadRepository>()
    private val soknadstatusProducerMock = mockk<ISoknadstatusProducer>()

    private val service =
        PubliserSoknadstatusService(
            soknadRepository = repositoryMock,
            soknadstatusProducer = soknadstatusProducerMock,
        )

    @BeforeEach
    fun resetMocks() {
        clearMocks(repositoryMock, soknadstatusProducerMock)
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
    fun `publiserMottatteSoknader publiserer og oppdaterer upubliserte soknader`() {
        val soknad = lagSoknad()

        every { repositoryMock.getUpubliserteSoknader() } returns listOf(soknad)
        every { repositoryMock.setSoknadPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        val resultater = service.publiserMottatteSoknader()

        assertTrue(resultater.single().isSuccess)
        verify(exactly = 1) { soknadstatusProducerMock.publiser(SoknadstatusRecord.fromSoknad(soknad)) }
        verify(exactly = 1) { repositoryMock.setSoknadPublisert(soknad.id, any()) }
    }

    @Test
    fun `feil for en soknad stopper ikke publisering av MOTTATT for de andre`() {
        val soknadSomFeiler = lagSoknad()
        val soknadSomLykkes = lagSoknad()

        every { repositoryMock.getUpubliserteSoknader() } returns listOf(soknadSomFeiler, soknadSomLykkes)
        every { repositoryMock.setSoknadPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returnsMany
            listOf(
                Result.failure(RuntimeException("kafka er nede")),
                Result.success(Unit),
            )

        val resultater = service.publiserMottatteSoknader()

        assertTrue(resultater[0].isFailure)
        assertTrue(resultater[1].isSuccess)
        verify(exactly = 1) { repositoryMock.setSoknadPublisert(soknadSomLykkes.id, any()) }
        verify(exactly = 0) { repositoryMock.setSoknadPublisert(soknadSomFeiler.id, any()) }
    }

    @Test
    fun `publiserBehandledeSoknader publiserer og oppdaterer soknader med upublisert vedtak`() {
        val soknad = soknadMedVedtak()

        every { repositoryMock.getSoknaderMedUpublisertVedtak() } returns listOf(soknad)
        every { repositoryMock.setVedtakPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        val resultater = service.publiserBehandledeSoknader()

        assertTrue(resultater.single().isSuccess)
        verify(exactly = 1) { soknadstatusProducerMock.publiser(SoknadstatusRecord.fromSoknadMedVedtak(soknad)) }
        verify(exactly = 1) { repositoryMock.setVedtakPublisert(soknad.vedtak!!.vedtakId, any()) }
    }

    @Test
    fun `feil for en soknad stopper ikke publisering av BEHANDLET for de andre`() {
        val soknadSomFeiler = soknadMedVedtak()
        val soknadSomLykkes = soknadMedVedtak()

        every { repositoryMock.getSoknaderMedUpublisertVedtak() } returns listOf(soknadSomFeiler, soknadSomLykkes)
        every { repositoryMock.setVedtakPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returnsMany
            listOf(
                Result.failure(RuntimeException("kafka er nede")),
                Result.success(Unit),
            )

        service.publiserBehandledeSoknader()

        verify(exactly = 1) { repositoryMock.setVedtakPublisert(soknadSomLykkes.vedtak!!.vedtakId, any()) }
        verify(exactly = 0) { repositoryMock.setVedtakPublisert(soknadSomFeiler.vedtak!!.vedtakId, any()) }
    }

    @Test
    fun `soknad uten vedtak publiseres ikke som BEHANDLET, men stopper ikke andre`() {
        val soknadUtenVedtak = lagSoknad()
        val soknadMedVedtak = soknadMedVedtak()

        every { repositoryMock.getSoknaderMedUpublisertVedtak() } returns listOf(soknadUtenVedtak, soknadMedVedtak)
        every { repositoryMock.setVedtakPublisert(any(), any()) } just Runs
        every { soknadstatusProducerMock.publiser(any()) } returns Result.success(Unit)

        service.publiserBehandledeSoknader()

        verify(exactly = 1) { repositoryMock.setVedtakPublisert(soknadMedVedtak.vedtak!!.vedtakId, any()) }
        verify(exactly = 1) { repositoryMock.setVedtakPublisert(any(), any()) }
    }
}
