package no.nav.syfo.utenlandsopphold.infrastructure.distribusjon

import no.nav.syfo.utenlandsopphold.getEnvVar

/**
 * Konfigurasjon for [no.nav.syfo.common.distribusjon.client.DokdistfordelingClient] og
 * [DistribusjonService].
 *
 * @param baseUrl Base-URL til dokdistfordeling.
 * @param clientId Scope til dokdistfordeling, brukt av [no.nav.syfo.common.token.azuread.AzureAdClient]
 * for å hente system-token.
 */
data class DokdistfordelingClientConfig(
    val baseUrl: String,
    val clientId: String,
) {
    companion object {
        /**
         * Leser [DokdistfordelingClientConfig] fra NAIS-injiserte miljøvariabler
         * `DOKDISTFORDELING_URL` og `DOKDISTFORDELING_SCOPE`.
         */
        fun fromEnv(): DokdistfordelingClientConfig =
            DokdistfordelingClientConfig(
                baseUrl = getEnvVar("DOKDISTFORDELING_URL"),
                clientId = getEnvVar("DOKDISTFORDELING_SCOPE"),
            )
    }
}
