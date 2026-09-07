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
import no.nav.syfo.utenlandsopphold.domain.behandlingsutfallDocument
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.veileder
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test

class JournalforBehandlingsutfallServiceTest {
    private val testPersonident = Personident("11111111111")

    private val repositoryMock = mockk<ISoknadRepository>()
    private val pdlClientMock = mockk<IPdlClient>()
    private val pdfClientMock = mockk<IPdfClient>()
    private val journalforingServiceMock = mockk<IJournalforingService>()
    private val distribusjonServiceMock = mockk<IDistribusjonService>()

    private val service =
        JournalforBehandlingsutfallService(
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

    private fun soknadMedBehandlingsutfall(utfall: Utfall = Utfall.Innvilget): Soknad =
        lagSoknad().registrerBehandlingsutfall(
            utfall = utfall,
            fattetAv = veileder,
            now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            document = behandlingsutfallDocument,
            begrunnelse = if (utfall == Utfall.Innvilget) null else "begrunnelse",
        )

    @Test
    fun `journalfører og oppdaterer ikke-journalførte vedtak ved innvilgelse`() =
        runTest {
            val soknad = soknadMedBehandlingsutfall(utfall = Utfall.Innvilget)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), Utfall.Innvilget, any()) } returns
                byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforBehandlingsutfall()

            coVerify(exactly = 1) { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), Utfall.Innvilget, any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any()) }
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
            val soknad = soknadMedBehandlingsutfall(utfall = delvisInnvilget)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), delvisInnvilget, any()) } returns
                byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforBehandlingsutfall()

            coVerify(exactly = 1) { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), delvisInnvilget, any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalfører og oppdaterer ikke-journalførte vedtak ved avslag`() =
        runTest {
            val soknad = soknadMedBehandlingsutfall(utfall = Utfall.Avslag)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), Utfall.Avslag, any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforBehandlingsutfall()

            coVerify(exactly = 1) { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), Utfall.Avslag, any()) }
            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `feil for ett vedtak stopper ikke journalføring av de andre`() =
        runTest {
            val soknadSomFeiler = soknadMedBehandlingsutfall()
            val soknadSomLykkes = soknadMedBehandlingsutfall()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokarkiv er nede")),
                    Result.success(JournalpostId("999")),
                )

            service.journalforBehandlingsutfall()

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
            val soknadUtenBehandlingsutfall = lagSoknad()
            val soknadMedBehandlingsutfall = soknadMedBehandlingsutfall()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns
                listOf(soknadUtenBehandlingsutfall, soknadMedBehandlingsutfall)
            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforBehandlingsutfall()

            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(
                    soknadMedBehandlingsutfall.behandlingsutfall!!.behandlingsutfallId,
                    any(),
                    any(),
                )
            }
            verify(exactly = 1) { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) }
        }

    @Test
    fun `journalfører enkelt vedtak direkte, uten å gå via getIkkeJournalforteSoknader`() =
        runTest {
            val soknad = soknadMedBehandlingsutfall()

            every { repositoryMock.setBehandlingsutfallJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBehandlingsutfallPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any()) } returns Result.success(JournalpostId("999"))

            service.journalforBehandlingsutfall(soknad)

            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setBehandlingsutfallJournalfort(soknad.behandlingsutfall!!.behandlingsutfallId, JournalpostId("999"), any())
            }
            verify(exactly = 0) { repositoryMock.getIkkeJournalforteSoknader(any()) }
        }

    @Test
    fun `distribuerer og oppdaterer journalfort, ikke-distribuert vedtak`() =
        runTest {
            val soknad =
                soknadMedBehandlingsutfall().journalforBehandlingsutfall(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBehandlingsutfall(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerBehandlingsutfall()

            coVerify(exactly = 1) { distribusjonServiceMock.distribuer(any(), any()) }
            verify(exactly = 1) { repositoryMock.setBehandlingsutfallDistribuert(soknad.behandlingsutfall!!.behandlingsutfallId, any()) }
        }

    @Test
    fun `distribuerer vedtak om innvilgelse med distribusjonstype VEDTAK`() =
        runTest {
            val soknad =
                soknadMedBehandlingsutfall(utfall = Utfall.Innvilget)
                    .journalforBehandlingsutfall(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBehandlingsutfall(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerBehandlingsutfall()

            coVerify(exactly = 1) {
                distribusjonServiceMock.distribuer(JournalpostId("999"), Distribusjonstype.VEDTAK)
            }
        }

    @Test
    fun `distribuerer henlagt vedtak med distribusjonstype VIKTIG`() =
        runTest {
            val soknad =
                soknadMedBehandlingsutfall(utfall = Utfall.Henlagt)
                    .journalforBehandlingsutfall(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBehandlingsutfall(any()) } returns listOf(soknad)
            every { repositoryMock.setBehandlingsutfallDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerBehandlingsutfall()

            coVerify(exactly = 1) {
                distribusjonServiceMock.distribuer(JournalpostId("999"), Distribusjonstype.VIKTIG)
            }
        }

    @Test
    fun `feil for ett vedtak stopper ikke distribusjon av de andre`() =
        runTest {
            val soknadSomFeiler =
                soknadMedBehandlingsutfall().journalforBehandlingsutfall(JournalpostId("111"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
            val soknadSomLykkes =
                soknadMedBehandlingsutfall().journalforBehandlingsutfall(JournalpostId("222"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBehandlingsutfall(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setBehandlingsutfallDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokdistfordeling er nede")),
                    Result.success("bestilling-1"),
                )

            service.distribuerBehandlingsutfall()

            verify(
                exactly = 1,
            ) { repositoryMock.setBehandlingsutfallDistribuert(soknadSomLykkes.behandlingsutfall!!.behandlingsutfallId, any()) }
            verify(
                exactly = 0,
            ) { repositoryMock.setBehandlingsutfallDistribuert(soknadSomFeiler.behandlingsutfall!!.behandlingsutfallId, any()) }
        }
}
