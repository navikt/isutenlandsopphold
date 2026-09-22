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
import io.mockk.clearMocks
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
import no.nav.syfo.utenlandsopphold.application.Transaction
import no.nav.syfo.utenlandsopphold.application.TransactionManager
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
import no.nav.syfo.utenlandsopphold.testutil.generateJWT
import no.nav.syfo.utenlandsopphold.testutil.wellKnownInternalAzureAD
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach
    fun resetMocks() {
        clearMocks(repository)
    }

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
                transactionManager = TestTransactionManagerV2,
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
    fun `vedtak godtar innvilgedePerioder som utelatt, null og tom liste`() =
        testApplication {
            stubHentSoknadOgLagreBehandling()
            val client = setupApiAndClient()
            val utenPerioder = mapOf("utfall" to "INNVILGET", "document" to document)
            val former =
                mapOf(
                    "utelatt" to utenPerioder,
                    "null" to utenPerioder + ("innvilgedePerioder" to null),
                    "tom liste" to utenPerioder + ("innvilgedePerioder" to emptyList<Any>()),
                )

            for ((navn, body) in former) {
                val response =
                    client.post(VEDTAK_PATH.format(soknad.id)) {
                        somSaksbehandlerMedSkrivetilgang(body)
                    }

                assertEquals(HttpStatusCode.OK, response.status, "$navn skal godtas")
            }
        }

    /**
     * Utfallsfeltet er typet som [VedtaksUtfall], så disse verdiene kan ikke uttrykkes i Kotlin.
     * En klient kan likevel sende dem på wire, og da skal Jackson gi 400 og ikke 500.
     */
    @Test
    fun `vedtak avviser utfall som ikke er et vedtaksutfall`() =
        testApplication {
            stubHentSoknadOgLagreBehandling()
            val client = setupApiAndClient()

            for (ugyldigUtfall in listOf("HENLAGT", "IKKE_AKTUELL", "TULL")) {
                val response =
                    client.post(VEDTAK_PATH.format(soknad.id)) {
                        somSaksbehandlerMedSkrivetilgang(
                            mapOf(
                                "utfall" to ugyldigUtfall,
                                "innvilgedePerioder" to emptyList<Any>(),
                                "document" to document,
                            ),
                        )
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status, "$ugyldigUtfall skal gi 400")
            }
        }

    @Test
    fun `henleggelse lagrer behandling med utfall henlagt uten utfallsfelt i forespørselen`() =
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
    fun `ikke-aktuell med ukjent årsak gir 400`() =
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

    /**
     * Tilgangssjekken står eksplisitt i hver rutehandler, ikke i en delt hjelper. Denne
     * testen er garantien for at ingen av dem mangler den, og må utvides når et nytt
     * skriveendepunkt kommer til.
     */
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

    @Test
    fun `alle skriveendepunkter krever token`() =
        testApplication {
            stubHentSoknadOgLagreBehandling()
            val client = setupApiAndClient()

            for ((path, body) in skriveendepunkter()) {
                val response =
                    client.post(path.format(soknad.id)) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }

                assertEquals(
                    HttpStatusCode.Unauthorized,
                    response.status,
                    "$path skal kreve token",
                )
            }
        }

    @Test
    fun `alle skriveendepunkter gir 404 for søknad som ikke finnes`() =
        testApplication {
            every { repository.hentSoknad(any()) } returns null
            val client = setupApiAndClient()

            for ((path, body) in skriveendepunkter()) {
                val response =
                    client.post(path.format(UUID.randomUUID())) {
                        somSaksbehandlerMedSkrivetilgang(body)
                    }

                assertEquals(
                    HttpStatusCode.NotFound,
                    response.status,
                    "$path skal gi 404 for ukjent søknad",
                )
            }
        }

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

    @Test
    fun `ikke-aktuell på allerede behandlet søknad gir 409`() =
        testApplication {
            val behandletSoknad =
                soknad.fattVedtak(
                    utfall = VedtaksUtfall.INNVILGET,
                    innvilgedePerioder = emptyList(),
                    begrunnelse = null,
                    behandletAv = Navident(UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG),
                    now = OffsetDateTime.parse("2026-03-02T09:00:00Z"),
                    document = document,
                )
            every { repository.hentSoknad(any()) } returns behandletSoknad
            every { repository.hentSoknadForUpdate(any(), any()) } returns behandletSoknad
            val client = setupApiAndClient()

            val response =
                client.post(IKKE_AKTUELL_PATH.format(soknad.id)) {
                    somSaksbehandlerMedSkrivetilgang(
                        IkkeAktuellPostV2DTO(arsak = IkkeAktuellArsakV2DTO.DUPLIKAT),
                    )
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
        }
}

private object NoopDatabaseV2 : DatabaseInterface {
    override val connection get() = throw NotImplementedError("Ikke i bruk i denne testen")
}

private object TestTransactionV2 : Transaction

private object TestTransactionManagerV2 : TransactionManager {
    override fun <T> inTransaction(block: (Transaction) -> T): T = block(TestTransactionV2)
}
