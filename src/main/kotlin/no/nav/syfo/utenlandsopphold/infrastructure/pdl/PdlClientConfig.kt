package no.nav.syfo.utenlandsopphold.infrastructure.pdl

import no.nav.syfo.utenlandsopphold.getEnvVar

/**
 * Konfigurasjon for [PdlClient].
 *
 * @param baseUrl Base-URL til PDL, f.eks. `https://pdl-api.dev-fss-pub.nais.io/graphql`.
 * @param clientId Client ID til PDL, brukes av en [no.nav.syfo.common.token.SystemTokenProvider]
 * for å hente system-token.
 */
data class PdlClientConfig(
    val baseUrl: String,
    val clientId: String,
) {
    companion object {
        fun fromEnv(): PdlClientConfig =
            PdlClientConfig(
                baseUrl = getEnvVar("PDL_URL"),
                clientId = getEnvVar("PDL_SCOPE"),
            )
    }
}
