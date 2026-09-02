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
val PDF_HENLEGGELSE_BYTES = "pdf-henleggelse".toByteArray()

/**
 * Mock-handler for ispdfgen. Ruter på path slik at ulike behandlingsutfall (vedtak: innvilget,
 * delvis innvilget, avslag; og henleggelse) gir hver sin PDF, i tråd med [PdfClient] sin
 * behandlingsutfall-baserte URL-velging.
 */
fun MockRequestHandleScope.mockPdfgenRequestHandler(request: HttpRequestData): HttpResponseData {
    val bytes =
        when (request.url.encodedPath) {
            PdfClient.VEDTAK_INNVILGET_PDF_PATH -> PDF_INNVILGET_BYTES
            PdfClient.VEDTAK_DELVIS_INNVILGET_PDF_PATH -> PDF_DELVIS_INNVILGET_BYTES
            PdfClient.VEDTAK_AVSLAG_PDF_PATH -> PDF_AVSLAG_BYTES
            PdfClient.HENLEGGELSE_PDF_PATH -> PDF_HENLEGGELSE_BYTES
            else -> error("Unhandled pdfgen request to ${request.url}")
        }

    return respond(
        content = bytes,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/pdf"),
    )
}
