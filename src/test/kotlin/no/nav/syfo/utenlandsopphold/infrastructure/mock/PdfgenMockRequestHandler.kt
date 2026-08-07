package no.nav.syfo.utenlandsopphold.infrastructure.mock

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import no.nav.syfo.utenlandsopphold.infrastructure.pdfgen.PdfClient

val PDF_INNVILGET_BYTES = "pdf-innvilget".toByteArray()
val PDF_DELVIS_INNVILGET_BYTES = "pdf-delvis-innvilget".toByteArray()
val PDF_AVSLAG_BYTES = "pdf-avslag".toByteArray()

/**
 * Mock-handler for ispdfgen. Ruter på path slik at ulike vedtaksutfall (innvilget, delvis
 * innvilget, avslag) gir hver sin PDF, i tråd med [PdfClient] sin utfall-baserte URL-velging.
 */
fun MockRequestHandleScope.mockPdfgenRequestHandler(request: HttpRequestData): HttpResponseData {
    val bytes =
        when (request.url.encodedPath) {
            PdfClient.VEDTAK_INNVILGET_PDF_PATH -> PDF_INNVILGET_BYTES
            PdfClient.VEDTAK_DELVIS_INNVILGET_PDF_PATH -> PDF_DELVIS_INNVILGET_BYTES
            PdfClient.VEDTAK_AVSLAG_PDF_PATH -> PDF_AVSLAG_BYTES
            else -> error("Unhandled pdfgen request to ${request.url}")
        }

    return respond(
        content = bytes,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/pdf"),
    )
}
