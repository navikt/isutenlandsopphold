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
import no.nav.syfo.utenlandsopphold.domain.Brevtype
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.brevDocument
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.veileder
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test

class BrevServiceTest {
    private val testPersonident = Personident("11111111111")

    private val repositoryMock = mockk<ISoknadRepository>()
    private val pdlClientMock = mockk<IPdlClient>()
    private val pdfClientMock = mockk<IPdfClient>()
    private val journalforingServiceMock = mockk<IJournalforingService>()
    private val distribusjonServiceMock = mockk<IDistribusjonService>()

    private val service =
        BrevService(
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

    private fun behandletSoknad(utfall: Utfall = Utfall.Innvilget): Soknad =
        lagSoknad().behandle(
            utfall = utfall,
            behandletAv = veileder,
            now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            document = brevDocument,
            begrunnelse = if (utfall == Utfall.Innvilget) null else "begrunnelse",
        )

    @Test
    fun `journalfører og oppdaterer ikke-journalførte brev ved innvilgelse`() =
        runTest {
            val soknad = behandletSoknad(utfall = Utfall.Innvilget)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBrevJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBrevPdf(testPersonident, any(), Brevtype.VEDTAK_INNVILGET, any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforBrev()

            coVerify(exactly = 1) { pdfClientMock.createBrevPdf(testPersonident, any(), Brevtype.VEDTAK_INNVILGET, any()) }
            coVerify(exactly = 1) {
                journalforingServiceMock.journalfor(testPersonident, any(), any(), JournalforingDokumenttype.VEDTAK)
            }
            verify(exactly = 1) {
                repositoryMock.setBrevJournalfort(soknad.behandling!!.brev.brevId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalfører og oppdaterer ikke-journalførte brev ved delvis innvilgelse`() =
        runTest {
            val delvisInnvilget =
                Utfall.DelvisInnvilget(
                    innvilgedePerioder = listOf(Periode(fom = LocalDate.of(2026, 1, 5), tom = LocalDate.of(2026, 1, 7))),
                )
            val soknad = behandletSoknad(utfall = delvisInnvilget)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBrevJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBrevPdf(testPersonident, any(), Brevtype.VEDTAK_DELVIS_INNVILGET, any()) } returns
                byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforBrev()

            coVerify(exactly = 1) { pdfClientMock.createBrevPdf(testPersonident, any(), Brevtype.VEDTAK_DELVIS_INNVILGET, any()) }
            coVerify(exactly = 1) {
                journalforingServiceMock.journalfor(testPersonident, any(), any(), JournalforingDokumenttype.VEDTAK)
            }
            verify(exactly = 1) {
                repositoryMock.setBrevJournalfort(soknad.behandling!!.brev.brevId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalfører og oppdaterer ikke-journalførte brev ved avslag`() =
        runTest {
            val soknad = behandletSoknad(utfall = Utfall.Avslag)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBrevJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBrevPdf(testPersonident, any(), Brevtype.VEDTAK_AVSLAG, any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforBrev()

            coVerify(exactly = 1) { pdfClientMock.createBrevPdf(testPersonident, any(), Brevtype.VEDTAK_AVSLAG, any()) }
            coVerify(exactly = 1) {
                journalforingServiceMock.journalfor(testPersonident, any(), any(), JournalforingDokumenttype.VEDTAK)
            }
            verify(exactly = 1) {
                repositoryMock.setBrevJournalfort(soknad.behandling!!.brev.brevId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalfører henleggelse med dokumenttype HENLEGGELSE`() =
        runTest {
            val soknad = behandletSoknad(utfall = Utfall.Henlagt)

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknad)
            every { repositoryMock.setBrevJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBrevPdf(testPersonident, any(), Brevtype.HENLEGGELSE, any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforBrev()

            coVerify(exactly = 1) {
                journalforingServiceMock.journalfor(
                    testPersonident,
                    any(),
                    any(),
                    JournalforingDokumenttype.HENLEGGELSE,
                )
            }
            verify(exactly = 1) {
                repositoryMock.setBrevJournalfort(soknad.behandling!!.brev.brevId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `feil for ett brev stopper ikke journalføring av de andre`() =
        runTest {
            val soknadSomFeiler = behandletSoknad()
            val soknadSomLykkes = behandletSoknad()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setBrevJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBrevPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokarkiv er nede")),
                    Result.success(JournalpostId("999")),
                )

            service.journalforBrev()

            verify(exactly = 1) { repositoryMock.setBrevJournalfort(soknadSomLykkes.behandling!!.brev.brevId, any(), any()) }
            verify(exactly = 0) { repositoryMock.setBrevJournalfort(soknadSomFeiler.behandling!!.brev.brevId, any(), any()) }
        }

    @Test
    fun `ubehandlet søknad journalføres ikke, men stopper ikke andre`() =
        runTest {
            val ubehandletSoknad = lagSoknad()
            val behandletSoknad = behandletSoknad()

            every { repositoryMock.getIkkeJournalforteSoknader(any()) } returns listOf(ubehandletSoknad, behandletSoknad)
            every { repositoryMock.setBrevJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBrevPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforBrev()

            verify(exactly = 1) { repositoryMock.setBrevJournalfort(behandletSoknad.behandling!!.brev.brevId, any(), any()) }
            verify(exactly = 1) { repositoryMock.setBrevJournalfort(any(), any(), any()) }
        }

    @Test
    fun `journalfører enkelt brev direkte, uten å gå via getIkkeJournalforteSoknader`() =
        runTest {
            val soknad = behandletSoknad()

            every { repositoryMock.setBrevJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createBrevPdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforBrev(soknad)

            coVerify(exactly = 1) {
                journalforingServiceMock.journalfor(testPersonident, any(), any(), JournalforingDokumenttype.VEDTAK)
            }
            verify(exactly = 1) {
                repositoryMock.setBrevJournalfort(soknad.behandling!!.brev.brevId, JournalpostId("999"), any())
            }
            verify(exactly = 0) { repositoryMock.getIkkeJournalforteSoknader(any()) }
        }

    @Test
    fun `distribuerer og oppdaterer journalført, ikke-distribuert brev`() =
        runTest {
            val soknad =
                behandletSoknad().journalforBrev(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBrev(any()) } returns listOf(soknad)
            every { repositoryMock.setBrevDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerBrev()

            coVerify(exactly = 1) { distribusjonServiceMock.distribuer(any(), any()) }
            verify(exactly = 1) { repositoryMock.setBrevDistribuert(soknad.behandling!!.brev.brevId, any()) }
        }

    @Test
    fun `distribuerer vedtaksbrev med distribusjonstype VEDTAK`() =
        runTest {
            val soknad =
                behandletSoknad(utfall = Utfall.Innvilget)
                    .journalforBrev(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBrev(any()) } returns listOf(soknad)
            every { repositoryMock.setBrevDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerBrev()

            coVerify(exactly = 1) {
                distribusjonServiceMock.distribuer(JournalpostId("999"), Distribusjonstype.VEDTAK)
            }
        }

    @Test
    fun `distribuerer henleggelsesbrev med distribusjonstype VIKTIG`() =
        runTest {
            val soknad =
                behandletSoknad(utfall = Utfall.Henlagt)
                    .journalforBrev(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBrev(any()) } returns listOf(soknad)
            every { repositoryMock.setBrevDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerBrev()

            coVerify(exactly = 1) {
                distribusjonServiceMock.distribuer(JournalpostId("999"), Distribusjonstype.VIKTIG)
            }
        }

    @Test
    fun `feil for ett brev stopper ikke distribusjon av de andre`() =
        runTest {
            val soknadSomFeiler =
                behandletSoknad().journalforBrev(JournalpostId("111"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
            val soknadSomLykkes =
                behandletSoknad().journalforBrev(JournalpostId("222"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getSoknaderMedIkkeDistribuerteBrev(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setBrevDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokdistfordeling er nede")),
                    Result.success("bestilling-1"),
                )

            service.distribuerBrev()

            verify(exactly = 1) { repositoryMock.setBrevDistribuert(soknadSomLykkes.behandling!!.brev.brevId, any()) }
            verify(exactly = 0) { repositoryMock.setBrevDistribuert(soknadSomFeiler.behandling!!.brev.brevId, any()) }
        }
}
