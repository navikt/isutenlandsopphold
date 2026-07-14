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
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.applyCommonJacksonConfig
import no.nav.syfo.utenlandsopphold.UserConstants
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.application.Transaction
import no.nav.syfo.utenlandsopphold.application.TransactionIsolation
import no.nav.syfo.utenlandsopphold.application.TransactionManager
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockTilgangskontrollClient
import no.nav.syfo.utenlandsopphold.testutil.TEST_AZURE_APP_CLIENT_ID
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
const val SOKNAD_VEDTAK_PATH = "/api/v1/soknader/%s/vedtak"

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
                    bearerAuth(generateJWT())
                    contentType(ContentType.Application.Json)
                    setBody(SoknaderQueryDTO(personident = UserConstants.PERSON_VEILEDERE_IKKE_HAR_TILGANG_TIL.value))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
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

    @Test
    fun `vedtak uten tilgang til person gir 403`() =
        testApplication {
            val soknadId = UUID.randomUUID()
            val soknad =
                Soknad(
                    id = soknadId,
                    eksternId = UUID.randomUUID(),
                    personident = UserConstants.PERSON_VEILEDERE_IKKE_HAR_TILGANG_TIL,
                    soktePerioder = listOf(Periode(fom = LocalDate.of(2026, 4, 1), tom = LocalDate.of(2026, 4, 10))),
                    innsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
                )
            val client = setupApiAndClient(soknadServiceCreatingVedtak(soknad) { _ -> error("Skal ikke kalles") })

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(soknadId)) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody(validSoknadVedtakPostDTO())
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `vedtak uten token gir 401`() =
        testApplication {
            val client =
                setupApiAndClient(soknadServiceCreatingVedtak(ubruktSoknad) { _ -> error("Skal ikke kalles") })

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(UUID.randomUUID())) {
                    contentType(ContentType.Application.Json)
                    setBody(validSoknadVedtakPostDTO())
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `vedtak med token med feil audience gir 401`() =
        testApplication {
            val client =
                setupApiAndClient(soknadServiceCreatingVedtak(ubruktSoknad) { _ -> error("Skal ikke kalles") })

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(UUID.randomUUID())) {
                    bearerAuth(generateJWT(audience = "en-annen-app"))
                    contentType(ContentType.Application.Json)
                    setBody(validSoknadVedtakPostDTO())
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `vedtak med ugyldig soknadId gir 400`() =
        testApplication {
            val client =
                setupApiAndClient(soknadServiceCreatingVedtak(ubruktSoknad) { _ -> error("Skal ikke kalles") })

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format("ikke-gyldig-uuid")) {
                    bearerAuth(generateJWT())
                    contentType(ContentType.Application.Json)
                    setBody(validSoknadVedtakPostDTO())
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `vedtak med ukjent soknadId gir 404`() =
        testApplication {
            val client = setupApiAndClient(soknadServiceReturning(emptyList()))

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(UUID.randomUUID())) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody(validSoknadVedtakPostDTO())
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `vedtak med ugyldig utfall gir 400`() =
        testApplication {
            val client =
                setupApiAndClient(soknadServiceCreatingVedtak(ubruktSoknad) { _ -> error("Skal ikke kalles") })

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(UUID.randomUUID())) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody(
                        SoknadVedtakPostDTO(
                            utfall = "GODKJENT",
                            innvilgetePerioder = emptyList(),
                            document =
                                listOf(
                                    DocumentComponent(
                                        type = DocumentComponentType.HEADER_H1,
                                        title = "Vedtak",
                                        texts = listOf("Søknaden din er innvilget"),
                                    ),
                                ),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `vedtak med tom document gir 400`() =
        testApplication {
            val client =
                setupApiAndClient(soknadServiceCreatingVedtak(ubruktSoknad) { _ -> error("Skal ikke kalles") })

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(UUID.randomUUID())) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody(
                        SoknadVedtakPostDTO(
                            utfall = "INNVILGET",
                            innvilgetePerioder = emptyList(),
                            document = emptyList(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `vedtak returnerer 200 med oppdatert soknad fra service`() =
        testApplication {
            val soknadId = UUID.randomUUID()
            val innvilgetePerioder = listOf(Periode(fom = LocalDate.of(2026, 4, 1), tom = LocalDate.of(2026, 4, 10)))
            var lagretSoknad: Soknad? = null

            val mottattSoknad =
                Soknad(
                    id = soknadId,
                    eksternId = UUID.randomUUID(),
                    personident = Personident("11111111111"),
                    soktePerioder = innvilgetePerioder,
                    innsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
                )

            val client =
                setupApiAndClient(
                    soknadServiceCreatingVedtak(mottattSoknad) { soknadMedVedtak ->
                        lagretSoknad = soknadMedVedtak
                    },
                )

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(soknadId.toString())) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody(
                        SoknadVedtakPostDTO(
                            utfall = "INNVILGET",
                            innvilgetePerioder = innvilgetePerioder.map { PeriodeDTO(fom = it.fom, tom = it.tom) },
                            document =
                                listOf(
                                    DocumentComponent(
                                        type = DocumentComponentType.HEADER_H1,
                                        title = "Vedtak",
                                        texts = listOf("Søknaden din er innvilget"),
                                    ),
                                ),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(soknadId, lagretSoknad?.id)
            assertEquals(Utfall.Innvilget, lagretSoknad?.vedtak?.utfall)
            assertEquals(innvilgetePerioder, lagretSoknad?.vedtak?.innvilgetePerioder)

            val body = response.body<SoknadVedtakResponseDTO>()
            assertEquals(soknadId.toString(), body.soknad.soknadId)
            assertEquals(SoknadStatusDTO.INNVILGET, body.soknad.status)
        }

    @Test
    fun `vedtak på soknad som allerede har vedtak gir 409`() =
        testApplication {
            val soknadId = UUID.randomUUID()
            val innvilgetePerioder = listOf(Periode(fom = LocalDate.of(2026, 4, 1), tom = LocalDate.of(2026, 4, 10)))
            val soknadMedVedtak =
                Soknad(
                    id = soknadId,
                    eksternId = UUID.randomUUID(),
                    personident = UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL,
                    soktePerioder = innvilgetePerioder,
                    innsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
                    vedtak =
                        Vedtak(
                            utfall = Utfall.Innvilget,
                            fattetAv = Navident(UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG),
                            fattetTidspunkt = Instant.parse("2026-03-02T09:00:00Z"),
                            innvilgetePerioder = innvilgetePerioder,
                            document = validSoknadVedtakPostDTO().document,
                        ),
                )
            val client = setupApiAndClient(soknadServiceCreatingVedtak(soknadMedVedtak))

            val response =
                client.post(SOKNAD_VEDTAK_PATH.format(soknadId.toString())) {
                    bearerAuth(generateJWT(navIdent = UserConstants.VEILEDER_IDENT_MED_SKRIVETILGANG))
                    contentType(ContentType.Application.Json)
                    setBody(validSoknadVedtakPostDTO())
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
        }
}

private fun soknadServiceReturning(soknader: List<Soknad>): SoknadService =
    SoknadService(
        soknadRepository =
            object : ISoknadRepository {
                override fun hentSoknad(soknadId: UUID): Soknad? = null

                override fun hentSoknadForUpdate(
                    transaction: Transaction,
                    soknadId: UUID,
                ): Soknad? = null

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

                override fun lagreVedtak(
                    transaction: Transaction,
                    soknadMedVedtak: Soknad,
                ): Soknad = throw NotImplementedError("Ikke i bruk i denne testen")
            },
        transactionManager = TestTransactionManager,
    )

private val ubruktSoknad =
    Soknad(
        eksternId = UUID.randomUUID(),
        personident = Personident("11111111111"),
        soktePerioder = listOf(Periode(fom = LocalDate.of(2026, 4, 1), tom = LocalDate.of(2026, 4, 10))),
        innsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
    )

private fun validSoknadVedtakPostDTO() =
    SoknadVedtakPostDTO(
        utfall = "INNVILGET",
        innvilgetePerioder = emptyList(),
        document =
            listOf(
                DocumentComponent(
                    type = DocumentComponentType.HEADER_H1,
                    title = "Vedtak",
                    texts = listOf("Søknaden din er innvilget"),
                ),
            ),
    )

private fun soknadServiceCreatingVedtak(
    mottattSoknad: Soknad,
    lagreVedtak: (Soknad) -> Unit = { _ -> },
): SoknadService =
    SoknadService(
        soknadRepository =
            object : ISoknadRepository {
                override fun hentSoknad(soknadId: UUID): Soknad? = mottattSoknad

                override fun hentSoknadForUpdate(
                    transaction: Transaction,
                    soknadId: UUID,
                ): Soknad? = mottattSoknad

                override fun hentSoknader(personident: Personident): List<Soknad> = throw NotImplementedError("Ikke i bruk i denne testen")

                override fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat =
                    throw NotImplementedError("Ikke i bruk i denne testen")

                override fun lagreVedtak(
                    transaction: Transaction,
                    soknadMedVedtak: Soknad,
                ): Soknad {
                    lagreVedtak.invoke(soknadMedVedtak)
                    return soknadMedVedtak
                }

                override fun getIkkeJournalforteSoknader(): List<Soknad> = emptyList()

                override fun getSoknaderMedIkkeDistribuerteVedtak(): List<Soknad> = emptyList()

                override fun setVedtakDistribuert(
                    vedtakId: UUID,
                    distribuertTidspunkt: Instant,
                ) = Unit

                override fun setVedtakJournalfort(
                    vedtakId: UUID,
                    journalpostId: JournalpostId,
                    journalfortTidspunkt: Instant,
                ) = Unit
            },
        transactionManager = TestTransactionManager,
    )

private object NoopDatabase : DatabaseInterface {
    override val connection get() = throw NotImplementedError("Ikke i bruk i denne testen")
}

private object TestTransaction : Transaction

private object TestTransactionManager : TransactionManager {
    override fun <T> inTransaction(
        isolation: TransactionIsolation?,
        block: (Transaction) -> T,
    ): T = block(TestTransaction)
}
