package no.nav.syfo.utenlandsopphold.infrastructure.tilgangskontroll

import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.utenlandsopphold.getEnvVar

/**
 * Konfigurasjon for [no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient] mot
 * istilgangskontroll.
 */
object TilgangskontrollClientConfig {
    /**
     * Leser [ClientConfig] for istilgangskontroll fra NAIS-injiserte miljøvariabler
     * `ISTILGANGSKONTROLL_URL` og `ISTILGANGSKONTROLL_CLIENT_ID`. Sistnevnte brukes av
     * [no.nav.syfo.common.token.azuread.AzureAdClient] for å hente OBO-token på vegne av
     * innlogget veileder.
     */
    fun fromEnv(): ClientConfig =
        ClientConfig(
            baseUrl = getEnvVar("ISTILGANGSKONTROLL_URL"),
            clientId = getEnvVar("ISTILGANGSKONTROLL_CLIENT_ID"),
        )
}
