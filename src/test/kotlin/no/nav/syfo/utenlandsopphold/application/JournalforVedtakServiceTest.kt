package no.nav.syfo.utenlandsopphold.application

import io.mockk.Runs
import io.mockk.clearMocks
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
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import kotlin.test.Test

class JournalforVedtakServiceTest {
    private val testPersonident = Personident("11111111111")

    private val repositoryMock = mockk<ISoknadRepository>()
    private val pdlClientMock = mockk<IPdlClient>()
    private val pdfClientMock = mockk<IPdfClient>()
    private val journalforingServiceMock = mockk<IJournalforingService>()
    private val distribusjonServiceMock = mockk<IDistribusjonService>()

    private val service =
        JournalforVedtakService(
            soknadRepository = repositoryMock,
            personInfoClient = pdlClientMock,
            pdfClient = pdfClientMock,
            journalforingService = journalforingServiceMock,
            distribusjonService = distribusjonServiceMock,
        )

    @BeforeEach
    fun resetMocks() {
        clearMocks(repositoryMock, pdlClientMock, pdfClientMock, journalforingServiceMock, distribusjonServiceMock)
    }

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

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setVedtakJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createVedtakPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforVedtak()

            coVerify(exactly = 1) { pdfClientMock.createVedtakPdf(testPersonident, any(), any(), any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setVedtakJournalfort(soknad.vedtak!!.vedtakId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `feil for ett vedtak stopper ikke journalføring av de andre`() =
        runTest {
            val soknadSomFeiler = soknadMedVedtak()
            val soknadSomLykkes = soknadMedVedtak()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setVedtakJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createVedtakPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokarkiv er nede")),
                    Result.success(JournalpostId("999")),
                )

            service.journalforVedtak()

            verify(exactly = 1) { repositoryMock.setVedtakJournalfort(soknadSomLykkes.vedtak!!.vedtakId, any(), any()) }
            verify(exactly = 0) { repositoryMock.setVedtakJournalfort(soknadSomFeiler.vedtak!!.vedtakId, any(), any()) }
        }

    @Test
    fun `søknad uten vedtak journalføres ikke, men stopper ikke andre`() =
        runTest {
            val soknadUtenVedtak = lagSoknad()
            val soknadMedVedtak = soknadMedVedtak()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknadUtenVedtak, soknadMedVedtak)
            every { repositoryMock.setVedtakJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createVedtakPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforVedtak()

            verify(exactly = 1) { repositoryMock.setVedtakJournalfort(soknadMedVedtak.vedtak!!.vedtakId, any(), any()) }
            verify(exactly = 1) { repositoryMock.setVedtakJournalfort(any(), any(), any()) }
        }

    @Test
    fun `journalfører enkelt vedtak direkte, uten å gå via getIkkeJournalforteSoknader`() =
        runTest {
            val soknad = soknadMedVedtak()

            every { repositoryMock.setVedtakJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createVedtakPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforVedtak(soknad)

            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setVedtakJournalfort(soknad.vedtak!!.vedtakId, JournalpostId("999"), any())
            }
            verify(exactly = 0) { repositoryMock.getIkkeJournalforteSoknader(any()) }
        }

    @Test
    fun `distribuerer og oppdaterer journalfort, ikke-distribuert vedtak`() =
        runTest {
            val soknad =
                soknadMedVedtak().let {
                    it.journalforVedtak(JournalpostId("999"), Instant.parse("2026-01-11T08:00:00Z"))
                }

            every { repositoryMock.getSoknaderMedIkkeDistribuerteVedtak() } returns listOf(soknad)
            every { repositoryMock.setVedtakDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any()) } returns Result.success("bestilling-1")

            service.distribuerVedtak()

            coVerify(exactly = 1) { distribusjonServiceMock.distribuer(any()) }
            verify(exactly = 1) { repositoryMock.setVedtakDistribuert(soknad.vedtak!!.vedtakId, any()) }
        }

    @Test
    fun `feil for ett vedtak stopper ikke distribusjon av de andre`() =
        runTest {
            val soknadSomFeiler =
                soknadMedVedtak().journalforVedtak(JournalpostId("111"), Instant.parse("2026-01-11T08:00:00Z"))
            val soknadSomLykkes =
                soknadMedVedtak().journalforVedtak(JournalpostId("222"), Instant.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteVedtak() } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setVedtakDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokdistfordeling er nede")),
                    Result.success("bestilling-1"),
                )

            service.distribuerVedtak()

            verify(exactly = 1) { repositoryMock.setVedtakDistribuert(soknadSomLykkes.vedtak!!.vedtakId, any()) }
            verify(exactly = 0) { repositoryMock.setVedtakDistribuert(soknadSomFeiler.vedtak!!.vedtakId, any()) }
        }
}
