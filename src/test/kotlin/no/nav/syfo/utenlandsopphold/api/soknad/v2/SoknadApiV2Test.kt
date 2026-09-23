package no.nav.syfo.utenlandsopphold.api.soknad.v2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.util.applyCommonJacksonConfig
import no.nav.syfo.utenlandsopphold.UserConstants
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.BrevService
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.Behandling
import no.nav.syfo.utenlandsopphold.domain.BehandlingsUtfall
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellArsak
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.VedtaksUtfall
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockTilgangskontrollClient
import no.nav.syfo.utenlandsopphold.testutil.TEST_AZURE_APP_CLIENT_ID
import no.nav.syfo.utenlandsopphold.testutil.TestTransactionManager
import no.nav.syfo.utenlandsopphold.testutil.generateJWT
import no.nav.syfo.utenlandsopphold.testutil.wellKnownInternalAzureAD
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val SOKNADER_QUERY_PATH = "/api/v2/soknader/query"
private const val VEDTAK_PATH = "/api/v2/soknader/%s/vedtak"
private const val HENLEGGELSE_PATH = "/api/v2/soknader/%s/henleggelse"
private const val IKKE_AKTUELL_PATH = "/api/v2/soknader/%s/ikke-aktuell"

class SoknadApiV2Test {
    private val repository = mockk<ISoknadRepository>()
    private val brevServiceMock = mockk<BrevService>(relaxed = true)

    private val soknad =
        Soknad(
            id = UUID.randomUUID(),
            eksternId = UUID.randomUUID(),
            personident = UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL,
            soktePerioder = listOf(Periode(fom = LocalDate.of(2026, 4, 1), tom = LocalDate.of(2026, 4, 10))),
            innsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
        )

    private val document =
        listOf(
            DocumentComponent(
                type = DocumentComponentType.HEADER_H1,
                title = "Vedtak",
                texts = listOf("Søknaden din er innvilget"),
            ),
        )

    private fun stubHentSoknadOgLagreBehandling(lagretSoknad: (Soknad) -> Unit = {}) {
        every { repository.hentSoknad(any()) } returns soknad
        every { repository.hentSoknadForUpdate(any(), any()) } returns soknad
        every { repository.lagreBehandling(any(), any()) } answers {
            val behandletSoknad = secondArg<Soknad>()
            lagretSoknad(behandletSoknad)
            behandletSoknad
        }
    }

    private fun ApplicationTestBuilder.setupApiAndClient(
        tilgangskontrollClient: TilgangskontrollClient = mockTilgangskontrollClient(),
    ): HttpClient {
        val soknadService =
            SoknadService(
                soknadRepository = repository,
                transactionManager = TestTransactionManager,
                brevService = brevServiceMock,
            )
        application {
            apiModule(
                applicationState = ApplicationState(),
                database = NoopDatabaseV2,
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

    private fun HttpRequestBuilder.somSaksbehandlerMedSkrivetilgang(body: Any) {
        bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `query returnerer søknad med behandling`() =
        testApplication {
            val behandletSoknad =
                soknad.fattVedtak(
                    utfall = VedtaksUtfall.AVSLAG,
                    innvilgedePerioder = emptyList(),
                    begrunnelse = "Oppholdet er ikke forenlig med aktivitetsplikten",
                    behandletAv = Navident(UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG),
                    now = OffsetDateTime.parse("2026-03-02T09:00:00Z"),
                    document = document,
                )
            every {
                repository.hentSoknader(UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL)
            } returns listOf(behandletSoknad)
            val client = setupApiAndClient()

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    bearerAuth(generateJWT())
                    contentType(ContentType.Application.Json)
                    setBody(
                        SoknaderQueryV2DTO(
                            personident = UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL.value,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val behandling =
                assertIs<AvslagBehandlingV2DTO>(
                    response
                        .body<SoknaderResponseV2DTO>()
                        .soknader
                        .single()
                        .behandling,
                )
            assertEquals(UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG, behandling.behandletAv)
        }

    @Test
    fun `query-endepunktet krever tilgang til personen`() =
        testApplication {
            val client = setupApiAndClient()

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_LESETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody(
                        SoknaderQueryV2DTO(
                            personident = UserConstants.PERSON_VEILEDERE_IKKE_HAR_TILGANG_TIL.value,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Forbidden, response.status, "query skal kreve tilgang til personen")
        }

    @Test
    fun `JSON-en for behandling har utfall som diskriminator for utfallstype og bare feltene som gjelder utfallet`() =
        testApplication {
            val behandletSoknad =
                soknad.fattVedtak(
                    utfall = VedtaksUtfall.AVSLAG,
                    innvilgedePerioder = emptyList(),
                    begrunnelse = "Oppholdet er ikke forenlig med aktivitetsplikten",
                    behandletAv = Navident(UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG),
                    now = OffsetDateTime.parse("2026-03-02T09:00:00Z"),
                    document = document,
                )
            every {
                repository.hentSoknader(UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL)
            } returns listOf(behandletSoknad)
            val client = setupApiAndClient()

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    bearerAuth(generateJWT())
                    contentType(ContentType.Application.Json)
                    setBody(
                        SoknaderQueryV2DTO(
                            personident = UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL.value,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val behandling =
                jacksonObjectMapper()
                    .readTree(response.bodyAsText())
                    .path("soknader")
                    .single()
                    .path("behandling")

            assertEquals("AVSLAG", behandling.path("utfall").asText())
            assertEquals(
                setOf("utfall", "behandletAv", "behandletTidspunkt", "begrunnelse"),
                behandling.fieldNames().asSequence().toSet(),
            )
        }

    @Test
    fun `vedtak lagrer behandling med utfall fra forespørselen`() =
        testApplication {
            var lagret: Soknad? = null
            stubHentSoknadOgLagreBehandling { lagret = it }
            val client = setupApiAndClient()

            val response =
                client.post(VEDTAK_PATH.format(soknad.id)) {
                    somSaksbehandlerMedSkrivetilgang(
                        VedtakPostV2DTO(
                            utfall = VedtaksUtfall.INNVILGET,
                            document = document,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val behandling = assertNotNull(assertNotNull(lagret).behandling)
            assertEquals(BehandlingsUtfall.Innvilget(soknad.soktePerioder), behandling.utfall)
            assertEquals(
                Navident(UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG),
                behandling.behandletAv,
            )
            assertIs<InnvilgetBehandlingV2DTO>(
                response
                    .body<SoknadResponseV2DTO>()
                    .soknad.behandling,
            )
        }

    @Test
    fun `henleggelse lagrer behandling med utfall henlagt`() =
        testApplication {
            var lagret: Soknad? = null
            stubHentSoknadOgLagreBehandling { lagret = it }
            val client = setupApiAndClient()

            val response =
                client.post(HENLEGGELSE_PATH.format(soknad.id)) {
                    somSaksbehandlerMedSkrivetilgang(
                        HenleggelsePostV2DTO(
                            document = document,
                            begrunnelse = "Søker har trukket søknaden",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val behandling = assertNotNull(assertNotNull(lagret).behandling)
            assertEquals(BehandlingsUtfall.Henlagt("Søker har trukket søknaden"), behandling.utfall)
            assertEquals(document, assertNotNull(behandling.brev).document)
        }

    @Test
    fun `ikke-aktuell lagrer behandling med årsak og uten brev`() =
        testApplication {
            var lagret: Soknad? = null
            stubHentSoknadOgLagreBehandling { lagret = it }
            val client = setupApiAndClient()

            val response =
                client.post(IKKE_AKTUELL_PATH.format(soknad.id)) {
                    somSaksbehandlerMedSkrivetilgang(
                        IkkeAktuellPostV2DTO(arsak = IkkeAktuellArsakV2DTO.BEHANDLET_I_INFOTRYGD),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val behandling: Behandling = assertNotNull(assertNotNull(lagret).behandling)
            assertEquals(BehandlingsUtfall.IkkeAktuell(IkkeAktuellArsak.BEHANDLET_I_INFOTRYGD), behandling.utfall)
            assertNull(behandling.brev)

            val behandlingDTO =
                assertIs<IkkeAktuellBehandlingV2DTO>(response.body<SoknadResponseV2DTO>().soknad.behandling)
            assertEquals(IkkeAktuellArsakV2DTO.BEHANDLET_I_INFOTRYGD, behandlingDTO.ikkeAktuellArsak)
            assertEquals(SoknadStatusV2DTO.IKKE_AKTUELL, response.body<SoknadResponseV2DTO>().soknad.status)
        }

    @Test
    fun `ukjent enum-verdi i forespørselen gir 400`() =
        testApplication {
            stubHentSoknadOgLagreBehandling()
            val client = setupApiAndClient()

            val response =
                client.post(IKKE_AKTUELL_PATH.format(soknad.id)) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody("""{"arsak":"FANTES_IKKE"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `alle skriveendepunkter krever skrivetilgang`() =
        testApplication {
            stubHentSoknadOgLagreBehandling()
            val client = setupApiAndClient()

            for ((path, body) in skriveendepunkter()) {
                val response =
                    client.post(path.format(soknad.id)) {
                        bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_LESETILGANG))
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }

                assertEquals(
                    HttpStatusCode.Forbidden,
                    response.status,
                    "$path skal kreve skrivetilgang",
                )
            }
        }

    /**
     * Bør utvides hvis det kommer flere endepunkter som krever skrivetilgang.
     */
    private fun skriveendepunkter(): List<Pair<String, Any>> =
        listOf(
            VEDTAK_PATH to
                VedtakPostV2DTO(
                    utfall = VedtaksUtfall.INNVILGET,
                    innvilgedePerioder = emptyList(),
                    document = document,
                ),
            HENLEGGELSE_PATH to
                HenleggelsePostV2DTO(
                    document = document,
                    begrunnelse = "Søker har trukket søknaden",
                ),
            IKKE_AKTUELL_PATH to IkkeAktuellPostV2DTO(arsak = IkkeAktuellArsakV2DTO.DUPLIKAT),
        )

    /**
     * Autentiseringen settes med én `authenticate`-blokk rundt hele API-et i ApiModule,
     * ikke per rute. Derfor er ett endepunkt nok til å dekke den.
     */
    @Test
    fun `apiet krever token`() =
        testApplication {
            val client = setupApiAndClient()

            val response =
                client.post(SOKNADER_QUERY_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryV2DTO(personident = soknad.personident.value))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    /**
     * Behandlingsendepunktene bruker en delt hjelper for å hente søknaden,
     * så holder å teste for et endepunkt.
     */
    @Test
    fun `behandlingsendepunkt gir 404 for søknad som ikke finnes`() =
        testApplication {
            every { repository.hentSoknad(any()) } returns null
            val client = setupApiAndClient()

            val response =
                client.post(VEDTAK_PATH.format(UUID.randomUUID())) {
                    somSaksbehandlerMedSkrivetilgang(
                        VedtakPostV2DTO(utfall = VedtaksUtfall.INNVILGET, document = document),
                    )
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}

private object NoopDatabaseV2 : DatabaseInterface {
    override val connection get() = throw NotImplementedError("Ikke i bruk i denne testen")
}
