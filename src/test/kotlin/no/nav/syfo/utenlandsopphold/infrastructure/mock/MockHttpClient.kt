package no.nav.syfo.utenlandsopphold.infrastructure.mock

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.common.mock.tilgangskontroll.mockTilgangskontrollRequestHandler
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.common.util.applyCommonJacksonConfig

const val ISTILGANGSKONTROLL_HOST = "istilgangskontroll"

/**
 * HTTP-klient med [MockEngine] som ruter kall til de eksterne tjenestene appen bruker til
 * tilhørende mock-handlere.
 */
fun mockHttpClient() =
    HttpClient(MockEngine) {
        install(ContentNegotiation) {
            jackson { applyCommonJacksonConfig() }
        }
        engine {
            addHandler { request ->
                when (request.url.host) {
                    ISTILGANGSKONTROLL_HOST ->
                        mockTilgangskontrollRequestHandler(request, mockTilgangDetailsPerNavident)

                    else -> error("Unhandled request to ${request.url}")
                }
            }
        }
    }

/**
 * [TilgangskontrollClient] koblet mot [mockHttpClient]. OBO-token-provideren ekko-er innkommende
 * token slik at NAVident-claimet bevares fram til mock-handleren.
 */
fun mockTilgangskontrollClient() =
    TilgangskontrollClient(
        oboTokenProvider = { _, token -> token },
        clientConfig = ClientConfig(baseUrl = "http://$ISTILGANGSKONTROLL_HOST", clientId = "istilgangskontroll-client-id"),
        httpClient = mockHttpClient(),
    )
