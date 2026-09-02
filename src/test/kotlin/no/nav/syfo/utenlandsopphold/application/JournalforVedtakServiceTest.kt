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
import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.vedtakDocument
import no.nav.syfo.utenlandsopphold.domain.veileder
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.time.OffsetDateTime
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

    private fun soknadMedVedtak(utfall: Utfall = Utfall.Innvilget): Soknad =
        lagSoknad().fattVedtak(
            utfall = utfall,
            fattetAv = veileder,
            now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            document = vedtakDocument,
            begrunnelse = if (utfall == Utfall.Innvilget) null else "begrunnelse",
        )

    private fun soknadMedHenleggelse(): Soknad =
        lagSoknad().henlegg(
            fattetAv = veileder,
            now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            document = vedtakDocument,
            begrunnelse = "Trukket av søker",
        )

    @Test
    fun `journalfører og oppdaterer ikke-journalførte vedtak ved innvilgelse`() =
        runTest {
            val soknad = soknadMedVedtak(utfall = Utfall.Innvilget)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createPdf(testPersonident, any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforVedtak()

            coVerify(exactly = 1) { pdfClientMock.createPdf(testPersonident, any(), any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalfører og oppdaterer ikke-journalførte vedtak ved delvis innvilgelse`() =
        runTest {
            val delvisInnvilget =
                Utfall.DelvisInnvilget(
                    innvilgedePerioder = listOf(Periode(fom = LocalDate.of(2026, 1, 5), tom = LocalDate.of(2026, 1, 7))),
                )
            val soknad = soknadMedVedtak(utfall = delvisInnvilget)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createPdf(testPersonident, any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforVedtak()

            coVerify(exactly = 1) { pdfClientMock.createPdf(testPersonident, any(), any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalfører og oppdaterer ikke-journalførte vedtak ved avslag`() =
        runTest {
            val soknad = soknadMedVedtak(utfall = Utfall.Avslag)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createPdf(testPersonident, any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforVedtak()

            coVerify(exactly = 1) { pdfClientMock.createPdf(testPersonident, any(), any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalfører og oppdaterer ikke-journalført henleggelse`() =
        runTest {
            val soknad = soknadMedHenleggelse()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createPdf(testPersonident, any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforVedtak()

            coVerify(exactly = 1) { pdfClientMock.createPdf(testPersonident, any(), any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `feil for ett vedtak stopper ikke journalføring av de andre`() =
        runTest {
            val soknadSomFeiler = soknadMedVedtak()
            val soknadSomLykkes = soknadMedVedtak()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createPdf(testPersonident, any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokarkiv er nede")),
                    Result.success(JournalpostId("999")),
                )

            service.journalforVedtak()

            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknadSomLykkes.behandlingsutfall!!.behandlingsutfallId, any(), any())
            }
            verify(exactly = 0) {
                repositoryMock.setBehandlingsutfallJournalfort(soknadSomFeiler.behandlingsutfall!!.behandlingsutfallId, any(), any())
            }
        }

    @Test
    fun `søknad uten vedtak journalføres ikke, men stopper ikke andre`() =
        runTest {
            val soknadUtenVedtak = lagSoknad()
            val soknadMedVedtak = soknadMedVedtak()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknadUtenVedtak, soknadMedVedtak)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createPdf(testPersonident, any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforVedtak()

            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknadMedVedtak.behandlingsutfall!!.behandlingsutfallId, any(), any())
            }
            verify(exactly = 1) { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) }
        }

    @Test
    fun `journalfører enkelt vedtak direkte, uten å gå via getIkkeJournalforteSoknader`() =
        runTest {
            val soknad = soknadMedVedtak()

            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createPdf(testPersonident, any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforVedtak(soknad)

            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
            verify(exactly = 0) { repositoryMock.getIkkeJournalforteSoknader(any()) }
        }

    @Test
    fun `distribuerer og oppdaterer journalfort, ikke-distribuert vedtak med distribusjonstype VEDTAK`() =
        runTest {
            val soknad =
                soknadMedVedtak().journalforBehandlingsutfall(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBehandlingsutfall(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerVedtak()

            coVerify(exactly = 1) { distribusjonServiceMock.distribuer(any(), Distribusjonstype.VEDTAK) }
            verify(exactly = 1) { repositoryMock.setBehandlingsutfallDistribuert(soknad.behandlingsutfall!!.behandlingsutfallId, any()) }
        }

    @Test
    fun `distribuerer henleggelse med distribusjonstype VIKTIG`() =
        runTest {
            val soknad =
                soknadMedHenleggelse().journalforBehandlingsutfall(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBehandlingsutfall(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerVedtak()

            coVerify(exactly = 1) { distribusjonServiceMock.distribuer(any(), Distribusjonstype.VIKTIG) }
            verify(exactly = 1) { repositoryMock.setBehandlingsutfallDistribuert(soknad.behandlingsutfall!!.behandlingsutfallId, any()) }
        }

    @Test
    fun `feil for ett vedtak stopper ikke distribusjon av de andre`() =
        runTest {
            val soknadSomFeiler =
                soknadMedVedtak().journalforBehandlingsutfall(JournalpostId("111"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
            val soknadSomLykkes =
                soknadMedVedtak().journalforBehandlingsutfall(JournalpostId("222"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBehandlingsutfall(any()) } returns
                listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setBehandlingsutfallDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokdistfordeling er nede")),
                    Result.success("bestilling-1"),
                )

            service.distribuerVedtak()

            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallDistribuert(soknadSomLykkes.behandlingsutfall!!.behandlingsutfallId, any())
            }
            verify(exactly = 0) {
                repositoryMock.setBehandlingsutfallDistribuert(soknadSomFeiler.behandlingsutfall!!.behandlingsutfallId, any())
            }
        }
}
