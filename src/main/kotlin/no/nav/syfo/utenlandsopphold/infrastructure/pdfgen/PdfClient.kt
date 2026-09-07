package no.nav.syfo.utenlandsopphold.infrastructure.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.syfo.common.http.defaultHttpClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.IPdfClient
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.sanitizeForPdfGen
import java.time.LocalDate

/**
 * Client for ispdfgen — genererer PDF-en for et vedtak om utenlandsopphold ut fra
 * dokumentkomponentene lagret på vedtaket.
 *
 * ispdfgen kjøres intra-cluster, så [defaultHttpClient] (uten utgående proxy) brukes.
 * Ingen autentisering kreves per no. — kun NAIS-nettverkspolicy (accessPolicy) mot ispdfgen.
 *
 * @param config [PdfClientConfig] med base-URL til ispdfgen.
 */
class PdfClient(
    private val config: PdfClientConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
) : IPdfClient {
    override suspend fun createVedtakPdf(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        utfall: Utfall,
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate,
    ): ByteArray {
        val request =
            VedtakPdfModel(
                mottakerFodselsnummer = mottakerFodselsnummer.value,
                mottakerNavn = mottakerNavn,
                documentComponents = documentComponents.sanitizeForPdfGen(),
                datoSendt = datoSendt,
            )
        val url = getVedtakPdfUrl(utfall)

        val response =
            httpClient.post(url) {
                accept(ContentType.Application.Pdf)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

        return response.body()
    }

    private fun getVedtakPdfUrl(utfall: Utfall): String =
        when (utfall) {
            Utfall.Innvilget -> "${config.baseUrl}$VEDTAK_INNVILGET_PDF_PATH"
            is Utfall.DelvisInnvilget -> "${config.baseUrl}$VEDTAK_DELVIS_INNVILGET_PDF_PATH"
            Utfall.Avslag -> "${config.baseUrl}$VEDTAK_AVSLAG_PDF_PATH"
        }

    override suspend fun createHenleggelsePdf(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate,
    ): ByteArray {
        val request =
            HenleggelsePdfModel(
                mottakerFodselsnummer = mottakerFodselsnummer.value,
                mottakerNavn = mottakerNavn,
                documentComponents = documentComponents.sanitizeForPdfGen(),
                datoSendt = datoSendt,
            )
        val url = "${config.baseUrl}$HENLEGGELSE_PDF_PATH"

        val response =
            httpClient.post(url) {
                accept(ContentType.Application.Pdf)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

        return response.body()
    }

    companion object {
        // Malen (template) må være registrert for isutenlandsopphold i ispdfgen.
        const val VEDTAK_INNVILGET_PDF_PATH: String = "/api/v1/genpdf/isutenlandsopphold/vedtak-innvilget"
        const val VEDTAK_DELVIS_INNVILGET_PDF_PATH: String = "/api/v1/genpdf/isutenlandsopphold/vedtak-delvis-innvilget"
        const val VEDTAK_AVSLAG_PDF_PATH: String = "/api/v1/genpdf/isutenlandsopphold/vedtak-avslag"
        const val HENLEGGELSE_PDF_PATH: String = "/api/v1/genpdf/isutenlandsopphold/henleggelse"
    }
}
