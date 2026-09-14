package no.nav.syfo.utenlandsopphold.infrastructure.pdfgen

import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Brevtype
import no.nav.syfo.utenlandsopphold.domain.brevDocument
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_AVSLAG_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_DELVIS_INNVILGET_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_HENLAGT_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_INNVILGET_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockPdfClient
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfClientTest {
    private val testPersonident = Personident("11111111111")
    private val pdfClient = mockPdfClient()

    @Test
    fun `henter innvilget-pdf ved innvilgelse`() =
        runTest {
            val pdf =
                pdfClient.createBrevPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    brevtype = Brevtype.VEDTAK_INNVILGET,
                    documentComponents = brevDocument,
                )

            assertTrue(PDF_INNVILGET_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter delvis-innvilget-pdf ved delvis innvilgelse`() =
        runTest {
            val pdf =
                pdfClient.createBrevPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    brevtype = Brevtype.VEDTAK_DELVIS_INNVILGET,
                    documentComponents = brevDocument,
                )

            assertTrue(PDF_DELVIS_INNVILGET_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter avslag-pdf ved avslag`() =
        runTest {
            val pdf =
                pdfClient.createBrevPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    brevtype = Brevtype.VEDTAK_AVSLAG,
                    documentComponents = brevDocument,
                )

            assertTrue(PDF_AVSLAG_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter henlagt-pdf ved henleggelse`() =
        runTest {
            val pdf =
                pdfClient.createBrevPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    brevtype = Brevtype.HENLEGGELSE,
                    documentComponents = brevDocument,
                )

            assertTrue(PDF_HENLAGT_BYTES.contentEquals(pdf))
        }
}
