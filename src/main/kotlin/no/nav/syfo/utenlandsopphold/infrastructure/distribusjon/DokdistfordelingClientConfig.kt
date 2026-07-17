package no.nav.syfo.utenlandsopphold.infrastructure.distribusjon

import no.nav.syfo.utenlandsopphold.getEnvVar

/**
 * Konfigurasjon for [no.nav.syfo.common.distribusjon.client.DokdistfordelingClient] og
 * [DistribusjonService].
 *
 * @param baseUrl Base-URL til dokdistfordeling.
 * @param clientId Client ID til dokdistfordeling, brukes av en [no.nav.syfo.common.token.SystemTokenProvider]
 * for å hente system-token.
 */
data class DokdistfordelingClientConfig(
    val baseUrl: String,
    val clientId: String,
) {
    companion object {
        fun fromEnv(): DokdistfordelingClientConfig =
            DokdistfordelingClientConfig(
                baseUrl = getEnvVar("DOKDISTFORDELING_URL"),
                clientId = getEnvVar("DOKDISTFORDELING_SCOPE"),
            )
    }
}
