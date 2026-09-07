package no.nav.syfo.utenlandsopphold.infrastructure.pdfgen

import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.behandlingsutfallDocument
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_AVSLAG_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_DELVIS_INNVILGET_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_HENLAGT_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_INNVILGET_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockPdfClient
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfClientTest {
    private val testPersonident = Personident("11111111111")
    private val pdfClient = mockPdfClient()

    @Test
    fun `henter innvilget-pdf ved innvilgelse`() =
        runTest {
            val pdf =
                pdfClient.createBehandlingsutfallPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    utfall = Utfall.Innvilget,
                    documentComponents = behandlingsutfallDocument,
                )

            assertTrue(PDF_INNVILGET_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter delvis-innvilget-pdf ved delvis innvilgelse`() =
        runTest {
            val delvisInnvilget =
                Utfall.DelvisInnvilget(
                    innvilgedePerioder = listOf(Periode(fom = LocalDate.of(2026, 1, 1), tom = LocalDate.of(2026, 1, 5))),
                )

            val pdf =
                pdfClient.createBehandlingsutfallPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    utfall = delvisInnvilget,
                    documentComponents = behandlingsutfallDocument,
                )

            assertTrue(PDF_DELVIS_INNVILGET_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter avslag-pdf ved avslag`() =
        runTest {
            val pdf =
                pdfClient.createBehandlingsutfallPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    utfall = Utfall.Avslag,
                    documentComponents = behandlingsutfallDocument,
                )

            assertTrue(PDF_AVSLAG_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter henlagt-pdf ved henleggelse`() =
        runTest {
            val pdf =
                pdfClient.createBehandlingsutfallPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    utfall = Utfall.Henlagt,
                    documentComponents = behandlingsutfallDocument,
                )

            assertTrue(PDF_HENLAGT_BYTES.contentEquals(pdf))
        }
}
