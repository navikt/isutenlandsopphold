package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.applyCommonJacksonConfig
import no.nav.syfo.utenlandsopphold.UserConstants
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockTilgangskontrollClient
import no.nav.syfo.utenlandsopphold.testutil.TEST_AZURE_APP_CLIENT_ID
import no.nav.syfo.utenlandsopphold.testutil.bearerToken
import no.nav.syfo.utenlandsopphold.testutil.generateJWT
import no.nav.syfo.utenlandsopphold.testutil.wellKnownInternalAzureAD
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val SOKNADER_QUERY_PATH = "/api/v1/soknader/query"

class SoknadApiTest {
    private fun ApplicationTestBuilder.setupApiAndClient(
        soknadService: SoknadService,
        tilgangskontrollClient: TilgangskontrollClient = mockTilgangskontrollClient(),
    ): HttpClient {
        application {
            apiModule(
                applicationState = ApplicationState(),
                database = NoopDatabase,
                soknadService = soknadService,
                tilgangskontrollClient = tilgangskontrollClient,
                azureAppClientId = TEST_AZURE_APP_CLIENT_ID,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD(),
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
                    personident = UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL,
                    soktePerioder =
                        listOf(
                            Periode(fom = LocalDate.of(2026, 4, 1), tom = LocalDate.of(2026, 4, 10)),
                        ),
                    innsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
                )
            val client = setupApiAndClient(soknadServiceReturning(listOf(soknad)))

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    bearerAuth(bearerToken())
                    bearerAuth(generateJWT())
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL.value))
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
                    bearerAuth(generateJWT())
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
                    bearerAuth(generateJWT())
                    contentType(ContentType.Application.Json)
                    setBody("""{ dette er ikke gyldig json """)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `query uten tilgang til person gir 403`() =
        testApplication {
            val client = setupApiAndClient(soknadServiceReturning(emptyList()))

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    bearerAuth(bearerToken())
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = UserConstants.PERSON_VEILEDERE_IKKE_HAR_TILGANG_TIL.value))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `query uten token gir 400`() =
        testApplication {
            val client = setupApiAndClient(soknadServiceReturning(emptyList()))

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL.value))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `query uten token gir 401`() =
        testApplication {
            val client = setupApiAndClient(soknadServiceReturning(emptyList()))

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = "11111111111"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `query med token med feil audience gir 401`() =
        testApplication {
            val client = setupApiAndClient(soknadServiceReturning(emptyList()))

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    bearerAuth(generateJWT(audience = "en-annen-app"))
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = "11111111111"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}

private fun soknadServiceReturning(soknader: List<Soknad>): SoknadService =
    SoknadService(
        soknadRepository =
            object : ISoknadRepository {
                override fun hentSoknader(personident: Personident): List<Soknad> = soknader

                override fun getIkkeJournalforteSoknader(): List<Soknad> = emptyList()

                override fun setVedtakJournalfort(
                    vedtakId: UUID,
                    journalpostId: JournalpostId,
                    journalfortTidspunkt: Instant,
                ) = Unit

                override fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat = LagreMottattSoknadResultat.LAGRET

                override fun getSoknaderMedIkkeDistribuerteVedtak(): List<Soknad> = emptyList()

                override fun setVedtakDistribuert(
                    vedtakId: UUID,
                    distribuertTidspunkt: Instant,
                ) = Unit
            },
    )

private object NoopDatabase : DatabaseInterface {
    override val connection get() = throw NotImplementedError("Ikke i bruk i denne testen")
}
