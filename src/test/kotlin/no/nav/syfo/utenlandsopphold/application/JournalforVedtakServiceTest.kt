package no.nav.syfo.utenlandsopphold.application

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.vedtakDocument
import no.nav.syfo.utenlandsopphold.domain.veileder
import java.time.Instant
import kotlin.test.Test

class JournalforVedtakServiceTest {
    // Alle søknader fra lagSoknad() bruker dette personidentet. MockK sin any()-matcher kan ikke
    // reflekteres fram for Personident siden verdiklassen validerer input i en init-blokk, så vi
    // matcher på den faktiske verdien i stedet.
    private val testPersonident = Personident("11111111111")

    private fun soknadMedVedtak(): Soknad =
        lagSoknad().fattVedtak(
            utfall = Utfall.Innvilget,
            fattetAv = veileder,
            now = Instant.parse("2026-01-10T12:00:00Z"),
            document = vedtakDocument,
        )

    @Test
    fun `journalfører og oppdaterer u-journalført vedtak`() =
        runTest {
            val soknad = soknadMedVedtak()
            val repository = mockk<ISoknadRepository>()
            val pdlClient = mockk<IPdlClient>()
            val pdfClient = mockk<IPdfClient>()
            val journalforingService = mockk<IJournalforingService>()
            val distribusjonService = mockk<IDistribusjonService>()

            every { repository.getIkkeJournalforteSoknader() } returns listOf(soknad)
            every { repository.setVedtakJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClient.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClient.createVedtakPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingService.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = pdlClient,
                    pdfClient = pdfClient,
                    journalforingService = journalforingService,
                    distribusjonService = distribusjonService,
                )

            service.journalforVedtak()

            coVerify(exactly = 1) { pdfClient.createVedtakPdf(testPersonident, any(), any(), any()) }
            coVerify(exactly = 1) { journalforingService.journalfor(testPersonident, any(), any()) }
            verify(exactly = 1) {
                repository.setVedtakJournalfort(soknad.vedtak!!.vedtakId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `feil for ett vedtak stopper ikke journalføring av de andre`() =
        runTest {
            val soknadSomFeiler = soknadMedVedtak()
            val soknadSomLykkes = soknadMedVedtak()
            val repository = mockk<ISoknadRepository>()
            val pdlClient = mockk<IPdlClient>()
            val pdfClient = mockk<IPdfClient>()
            val journalforingService = mockk<IJournalforingService>()
            val distribusjonService = mockk<IDistribusjonService>()

            every { repository.getIkkeJournalforteSoknader() } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repository.setVedtakJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClient.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClient.createVedtakPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingService.journalfor(testPersonident, any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokarkiv er nede")),
                    Result.success(JournalpostId("999")),
                )

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = pdlClient,
                    pdfClient = pdfClient,
                    journalforingService = journalforingService,
                    distribusjonService = distribusjonService,
                )

            service.journalforVedtak()

            verify(exactly = 1) { repository.setVedtakJournalfort(soknadSomLykkes.vedtak!!.vedtakId, any(), any()) }
            verify(exactly = 0) { repository.setVedtakJournalfort(soknadSomFeiler.vedtak!!.vedtakId, any(), any()) }
        }

    @Test
    fun `søknad uten vedtak journalføres ikke, men stopper ikke andre`() =
        runTest {
            val soknadUtenVedtak = lagSoknad()
            val soknadMedVedtak = soknadMedVedtak()
            val repository = mockk<ISoknadRepository>()
            val pdlClient = mockk<IPdlClient>()
            val pdfClient = mockk<IPdfClient>()
            val journalforingService = mockk<IJournalforingService>()
            val distribusjonService = mockk<IDistribusjonService>()

            every { repository.getIkkeJournalforteSoknader() } returns listOf(soknadUtenVedtak, soknadMedVedtak)
            every { repository.setVedtakJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClient.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClient.createVedtakPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingService.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = pdlClient,
                    pdfClient = pdfClient,
                    journalforingService = journalforingService,
                    distribusjonService = distribusjonService,
                )

            service.journalforVedtak()

            verify(exactly = 1) { repository.setVedtakJournalfort(soknadMedVedtak.vedtak!!.vedtakId, any(), any()) }
            verify(exactly = 1) { repository.setVedtakJournalfort(any(), any(), any()) }
        }

    @Test
    fun `distribuerer og oppdaterer journalfort, ikke-distribuert vedtak`() =
        runTest {
            val soknad =
                soknadMedVedtak().let {
                    it.journalforVedtak(JournalpostId("999"), Instant.parse("2026-01-11T08:00:00Z"))
                }
            val repository = mockk<ISoknadRepository>()
            val pdlClient = mockk<IPdlClient>()
            val pdfClient = mockk<IPdfClient>()
            val journalforingService = mockk<IJournalforingService>()
            val distribusjonService = mockk<IDistribusjonService>()

            every { repository.getSoknaderMedIkkeDistribuerteVedtak() } returns listOf(soknad)
            every { repository.setVedtakDistribuert(any(), any()) } just Runs
            coEvery { distribusjonService.distribuer(any()) } returns Result.success("bestilling-1")

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = pdlClient,
                    pdfClient = pdfClient,
                    journalforingService = journalforingService,
                    distribusjonService = distribusjonService,
                )

            service.distribuerVedtak()

            coVerify(exactly = 1) { distribusjonService.distribuer(any()) }
            verify(exactly = 1) { repository.setVedtakDistribuert(soknad.vedtak!!.vedtakId, any()) }
        }

    @Test
    fun `feil for ett vedtak stopper ikke distribusjon av de andre`() =
        runTest {
            val soknadSomFeiler =
                soknadMedVedtak().journalforVedtak(JournalpostId("111"), Instant.parse("2026-01-11T08:00:00Z"))
            val soknadSomLykkes =
                soknadMedVedtak().journalforVedtak(JournalpostId("222"), Instant.parse("2026-01-11T08:00:00Z"))
            val repository = mockk<ISoknadRepository>()
            val pdlClient = mockk<IPdlClient>()
            val pdfClient = mockk<IPdfClient>()
            val journalforingService = mockk<IJournalforingService>()
            val distribusjonService = mockk<IDistribusjonService>()

            every { repository.getSoknaderMedIkkeDistribuerteVedtak() } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repository.setVedtakDistribuert(any(), any()) } just Runs
            coEvery { distribusjonService.distribuer(any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokdistfordeling er nede")),
                    Result.success("bestilling-1"),
                )

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = pdlClient,
                    pdfClient = pdfClient,
                    journalforingService = journalforingService,
                    distribusjonService = distribusjonService,
                )

            service.distribuerVedtak()

            verify(exactly = 1) { repository.setVedtakDistribuert(soknadSomLykkes.vedtak!!.vedtakId, any()) }
            verify(exactly = 0) { repository.setVedtakDistribuert(soknadSomFeiler.vedtak!!.vedtakId, any()) }
        }
}
