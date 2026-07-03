package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.syfo.common.util.applyCommonJacksonConfig
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val SOKNADER_QUERY_PATH = "/api/v1/soknader/query"

class SoknadApiTest {
    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            apiModule(
                applicationState = ApplicationState(),
                database = NoopDatabase,
            )
        }
        return createClient {
            install(ContentNegotiation) {
                jackson { applyCommonJacksonConfig() }
            }
        }
    }

    @Test
    fun `query returnerer 200 med mocket soknadshistorikk`() =
        testApplication {
            val client = setupApiAndClient()

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = "11111111111"))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<SoknaderResponseDTO>()
            assertTrue(body.soknader.isNotEmpty(), "svar skal inneholde minst en soknad")

            val raatekst = response.bodyAsText()
            assertTrue(
                raatekst.contains("\"innsendtTidspunkt\":\"2026-03-01T09:00:00Z\""),
                "Instant skal serialiseres som ISO-8601",
            )
        }

    @Test
    fun `query med ugyldig personident gir 400`() =
        testApplication {
            val client = setupApiAndClient()

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = "ikke-gyldig"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `query med ugyldig json gir 400`() =
        testApplication {
            val client = setupApiAndClient()

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("""{ dette er ikke gyldig json """)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}

private object NoopDatabase : DatabaseInterface {
    override val connection get() = throw NotImplementedError("Ikke i bruk i denne testen")
}
