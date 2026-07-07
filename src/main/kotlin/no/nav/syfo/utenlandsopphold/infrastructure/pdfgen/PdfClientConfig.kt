package no.nav.syfo.utenlandsopphold.infrastructure.pdfgen

import no.nav.syfo.utenlandsopphold.getEnvVar

/**
 * Konfigurasjon for [PdfClient].
 *
 * @param baseUrl Base-URL til ispdfgen, f.eks. `http://ispdfgen`.
 */
data class PdfClientConfig(
    val baseUrl: String,
) {
    companion object {
        /**
         * Leser [PdfClientConfig] fra NAIS-injisert miljøvariabel `ISPDFGEN_URL`.
         */
        fun fromEnv(): PdfClientConfig = PdfClientConfig(baseUrl = getEnvVar("ISPDFGEN_URL"))
    }
}
