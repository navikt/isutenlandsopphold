package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.applyCommonJacksonConfig
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val SOKNADER_QUERY_PATH = "/api/v1/soknader/query"

class SoknadApiTest {
    private fun ApplicationTestBuilder.setupApiAndClient(soknadService: SoknadService): HttpClient {
        application {
            apiModule(
                applicationState = ApplicationState(),
                database = NoopDatabase,
                soknadService = soknadService,
            )
        }
        return createClient {
            install(ContentNegotiation) {
                jackson { applyCommonJacksonConfig() }
            }
        }
    }

    @Test
    fun `query returnerer 200 med soknadshistorikk fra service`() =
        testApplication {
            val soknad =
                Soknad(
                    id = UUID.randomUUID(),
                    eksternId = UUID.randomUUID(),
                    personident = Personident("11111111111"),
                    soktePerioder =
                        listOf(
                            Periode(fom = LocalDate.of(2026, 4, 1), tom = LocalDate.of(2026, 4, 10)),
                        ),
                    innsendtTidspunkt = Instant.parse("2026-03-01T09:00:00Z"),
                )
            val client = setupApiAndClient(soknadServiceReturning(listOf(soknad)))

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = "11111111111"))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<SoknaderResponseDTO>()
            assertEquals(1, body.soknader.size)
            assertEquals(soknad.id.toString(), body.soknader.single().soknadId)

            val raatekst = response.bodyAsText()
            assertTrue(
                raatekst.contains("\"innsendtTidspunkt\":\"2026-03-01T09:00:00Z\""),
                "Instant skal serialiseres som ISO-8601",
            )
        }

    @Test
    fun `query med ugyldig personident gir 400`() =
        testApplication {
            val client = setupApiAndClient(soknadServiceReturning(emptyList()))

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
            val client = setupApiAndClient(soknadServiceReturning(emptyList()))

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody("""{ dette er ikke gyldig json """)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}

private fun soknadServiceReturning(soknader: List<Soknad>): SoknadService =
    SoknadService(
        soknadRepository =
            object : ISoknadRepository {
                override fun hentSoknader(personident: Personident): List<Soknad> = soknader

                override fun lagreMottattSoknad(soknad: Soknad): Soknad = soknad
            },
    )

private object NoopDatabase : DatabaseInterface {
    override val connection get() = throw NotImplementedError("Ikke i bruk i denne testen")
}
