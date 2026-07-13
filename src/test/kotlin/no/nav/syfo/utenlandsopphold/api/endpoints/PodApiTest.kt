package no.nav.syfo.utenlandsopphold.api.endpoints

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.application.TransactionContext
import no.nav.syfo.utenlandsopphold.application.TransactionManager
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.database.Database
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockTilgangskontrollClient
import no.nav.syfo.utenlandsopphold.testutil.TEST_AZURE_APP_CLIENT_ID
import no.nav.syfo.utenlandsopphold.testutil.wellKnownInternalAzureAD
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PodApiTest {
    companion object {
        private lateinit var embeddedPostgres: EmbeddedPostgres
        lateinit var database: Database

        @BeforeAll
        @JvmStatic
        fun setup() {
            embeddedPostgres = EmbeddedPostgres.start()
            database =
                Database(
                    config =
                        DatabaseConfig(
                            jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres"),
                            username = "postgres",
                            password = "",
                        ),
                )
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            embeddedPostgres.close()
        }
    }

    @Test
    fun `is_alive returns 200`() =
        testApplication {
            application {
                apiModule(
                    applicationState = ApplicationState(alive = true, ready = true),
                    database = database,
                    soknadService = emptySoknadService(),
                    tilgangskontrollClient = mockTilgangskontrollClient(),
                    azureAppClientId = TEST_AZURE_APP_CLIENT_ID,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD(),
                )
            }
            val response = client.get(POD_LIVENESS_PATH)
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `is_ready returns 200 when alive and database is ok`() =
        testApplication {
            application {
                apiModule(
                    applicationState = ApplicationState(alive = true, ready = true),
                    database = database,
                    soknadService = emptySoknadService(),
                    tilgangskontrollClient = mockTilgangskontrollClient(),
                    azureAppClientId = TEST_AZURE_APP_CLIENT_ID,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD(),
                )
            }
            val response = client.get(POD_READINESS_PATH)
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `is_ready returns 500 when not ready`() =
        testApplication {
            application {
                apiModule(
                    applicationState = ApplicationState(alive = true, ready = false),
                    database = database,
                    soknadService = emptySoknadService(),
                    tilgangskontrollClient = mockTilgangskontrollClient(),
                    azureAppClientId = TEST_AZURE_APP_CLIENT_ID,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD(),
                )
            }
            val response = client.get(POD_READINESS_PATH)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `is_ready returns 500 when database is not ok`() =
        testApplication {
            val brokenDb =
                object : DatabaseInterface {
                    override val connection: Connection
                        get() = throw RuntimeException("DB is down")
                }
            application {
                apiModule(
                    applicationState = ApplicationState(alive = true, ready = true),
                    database = brokenDb,
                    soknadService = emptySoknadService(),
                    tilgangskontrollClient = mockTilgangskontrollClient(),
                    azureAppClientId = TEST_AZURE_APP_CLIENT_ID,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD(),
                )
            }
            val response = client.get(POD_READINESS_PATH)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}

private fun emptySoknadService(): SoknadService =
    SoknadService(
        soknadRepository =
            object : ISoknadRepository {
                override fun hentSoknad(soknadId: UUID): Soknad? = null

                override fun hentSoknadForUpdate(
                    tx: TransactionContext,
                    soknadId: UUID,
                ): Soknad? = null

                override fun hentSoknader(personident: Personident): List<Soknad> = emptyList()

                override fun lagreVedtak(
                    tx: TransactionContext,
                    soknadMedVedtak: Soknad,
                ): Soknad = throw NotImplementedError("Ikke i bruk i denne testen")

                override fun getIkkeJournalforteSoknader(): List<Soknad> = emptyList()

                override fun setVedtakJournalfort(
                    vedtakId: UUID,
                    journalpostId: JournalpostId,
                    journalfortTidspunkt: Instant,
                ) = Unit

                override fun getSoknaderMedIkkeDistribuerteVedtak(): List<Soknad> = emptyList()

                override fun setVedtakDistribuert(
                    vedtakId: UUID,
                    distribuertTidspunkt: Instant,
                ) = Unit

                override fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat = LagreMottattSoknadResultat.LAGRET
            },
        transactionManager = TestTransactionManager,
    )

private object TestTransactionContext : TransactionContext

private object TestTransactionManager : TransactionManager {
    override fun <T> inTransaction(block: (TransactionContext) -> T): T = block(TestTransactionContext)
}
