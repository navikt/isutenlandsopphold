package no.nav.syfo.utenlandsopphold.application

import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.henleggelseDocument
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.veileder
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.UtenlandsoppholdBrevkode
import org.junit.jupiter.api.BeforeEach
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertFalse

class JournalforHenleggelseServiceTest {
    private val testPersonident = Personident("11111111111")

    private val repositoryMock = mockk<ISoknadRepository>()
    private val pdlClientMock = mockk<IPdlClient>()
    private val pdfClientMock = mockk<IPdfClient>()
    private val journalforingServiceMock = mockk<IJournalforingService>()
    private val distribusjonServiceMock = mockk<IDistribusjonService>()

    private val service =
        JournalforHenleggelseService(
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

    private fun soknadMedHenleggelse(): Soknad =
        lagSoknad().henlegg(
            begrunnelse = "Søker har trukket søknaden",
            henlagtAv = veileder,
            now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            document = henleggelseDocument,
        )

    @Test
    fun `journalfører og oppdaterer ikke-journalforte henleggelser`() =
        runTest {
            val soknad = soknadMedHenleggelse()

            every { repositoryMock.getIkkeJournalforteHenleggelser(any()) } returns listOf(soknad)
            every { repositoryMock.setHenleggelseJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createHenleggelsePdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery {
                journalforingServiceMock.journalfor(testPersonident, any(), any(), UtenlandsoppholdBrevkode.HENLEGGELSE, any())
            } returns Result.success(JournalpostId("999"))

            service.journalforHenleggelse()

            coVerify(exactly = 1) { pdfClientMock.createHenleggelsePdf(testPersonident, any(), any(), any()) }
            coVerify(exactly = 1) {
                journalforingServiceMock.journalfor(testPersonident, any(), any(), UtenlandsoppholdBrevkode.HENLEGGELSE, any())
            }
            verify(exactly = 1) {
                repositoryMock.setHenleggelseJournalfort(soknad.henleggelse!!.henleggelseId, JournalpostId("999"), any())
            }
        }

    @Test
    fun `journalføring av henleggelse bruker ikke tittel eller brevkode som nevner vedtak`() =
        runTest {
            val soknad = soknadMedHenleggelse()
            val tittelSlot = slot<String>()

            every { repositoryMock.setHenleggelseJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createHenleggelsePdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery {
                journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), capture(tittelSlot))
            } returns Result.success(JournalpostId("999"))

            service.journalforHenleggelse(soknad)

            assertFalse(tittelSlot.captured.contains("vedtak", ignoreCase = true))
            assertFalse(UtenlandsoppholdBrevkode.HENLEGGELSE.value.contains("VEDTAK", ignoreCase = true))
        }

    @Test
    fun `feil for en henleggelse stopper ikke journalføring av de andre`() =
        runTest {
            val soknadSomFeiler = soknadMedHenleggelse()
            val soknadSomLykkes = soknadMedHenleggelse()

            every { repositoryMock.getIkkeJournalforteHenleggelser(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setHenleggelseJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createHenleggelsePdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokarkiv er nede")),
                    Result.success(JournalpostId("999")),
                )

            service.journalforHenleggelse()

            verify(exactly = 1) {
                repositoryMock.setHenleggelseJournalfort(soknadSomLykkes.henleggelse!!.henleggelseId, any(), any())
            }
            verify(exactly = 0) {
                repositoryMock.setHenleggelseJournalfort(soknadSomFeiler.henleggelse!!.henleggelseId, any(), any())
            }
        }

    @Test
    fun `journalfører enkel henleggelse direkte, uten å gå via getIkkeJournalforteHenleggelser`() =
        runTest {
            val soknad = soknadMedHenleggelse()

            every { repositoryMock.setHenleggelseJournalfort(any(), any(), any()) } just Runs
            coEvery { pdlClientMock.getNavn(testPersonident) } returns "Ola Nordmann"
            coEvery { pdfClientMock.createHenleggelsePdf(testPersonident, any(), any(), any()) } returns byteArrayOf(1, 2, 3)
            coEvery { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) } returns
                Result.success(JournalpostId("999"))

            service.journalforHenleggelse(soknad)

            coVerify(exactly = 1) { journalforingServiceMock.journalfor(testPersonident, any(), any(), any(), any()) }
            verify(exactly = 1) {
                repositoryMock.setHenleggelseJournalfort(soknad.henleggelse!!.henleggelseId, JournalpostId("999"), any())
            }
            verify(exactly = 0) { repositoryMock.getIkkeJournalforteHenleggelser(any()) }
        }

    @Test
    fun `distribuerer og oppdaterer journalfort, ikke-distribuert henleggelse med distribusjonstype VIKTIG`() =
        runTest {
            val soknad =
                soknadMedHenleggelse().journalforHenleggelse(JournalpostId("999"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getHenleggelserMedIkkeDistribuert(any()) } returns listOf(soknad)
            every { repositoryMock.setHenleggelseDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returns Result.success("bestilling-1")

            service.distribuerHenleggelse()

            coVerify(exactly = 1) { distribusjonServiceMock.distribuer(JournalpostId("999"), Distribusjonstype.VIKTIG) }
            verify(exactly = 1) { repositoryMock.setHenleggelseDistribuert(soknad.henleggelse!!.henleggelseId, any()) }
        }

    @Test
    fun `feil for en henleggelse stopper ikke distribusjon av de andre`() =
        runTest {
            val soknadSomFeiler =
                soknadMedHenleggelse()
                    .journalforHenleggelse(JournalpostId("111"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
            val soknadSomLykkes =
                soknadMedHenleggelse()
                    .journalforHenleggelse(JournalpostId("222"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

            every { repositoryMock.getHenleggelserMedIkkeDistribuert(any()) } returns listOf(soknadSomFeiler, soknadSomLykkes)
            every { repositoryMock.setHenleggelseDistribuert(any(), any()) } just Runs
            coEvery { distribusjonServiceMock.distribuer(any(), any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("dokdistfordeling er nede")),
                    Result.success("bestilling-1"),
                )

            service.distribuerHenleggelse()

            verify(exactly = 1) { repositoryMock.setHenleggelseDistribuert(soknadSomLykkes.henleggelse!!.henleggelseId, any()) }
            verify(exactly = 0) { repositoryMock.setHenleggelseDistribuert(soknadSomFeiler.henleggelse!!.henleggelseId, any()) }
        }
}
