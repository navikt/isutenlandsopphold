package no.nav.syfo.utenlandsopphold.infrastructure.journalforing

import no.nav.syfo.utenlandsopphold.getEnvVar

/**
 * Konfigurasjon for [no.nav.syfo.common.journalforing.client.DokarkivClient] og
 * [JournalforingService].
 *
 * @param baseUrl Base-URL til dokarkiv.
 * @param clientId Client ID til dokarkiv, brukes av en [no.nav.syfo.common.token.SystemTokenProvider]
 * for å hente system-token.
 * @param isRetryEnabled Skal kun være true i prod (se README/naiserator for hvilket miljø
 * som setter denne) — i dev-gcp mangler mange brukere aktør-id i dokarkiv, og vi ønsker
 * ikke å spamme dokarkiv med retries for disse.
 */
data class DokarkivClientConfig(
    val baseUrl: String,
    val clientId: String,
    val isRetryEnabled: Boolean,
) {
    companion object {
        /**
         * Leser [DokarkivClientConfig] fra miljøvariabler `DOKARKIV_URL`,
         * `DOKARKIV_SCOPE` og `JOURNALFORING_RETRY_ENABLED`.
         */
        fun fromEnv(): DokarkivClientConfig =
            DokarkivClientConfig(
                baseUrl = getEnvVar("DOKARKIV_URL"),
                clientId = getEnvVar("DOKARKIV_SCOPE"),
                isRetryEnabled = getEnvVar("JOURNALFORING_RETRY_ENABLED", "false").toBoolean(),
            )
    }
}
