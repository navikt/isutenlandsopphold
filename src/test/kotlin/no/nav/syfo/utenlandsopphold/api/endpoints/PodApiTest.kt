package no.nav.syfo.utenlandsopphold.api.endpoints

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.infrastructure.database.Database
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockTilgangskontrollClient
import no.nav.syfo.utenlandsopphold.testutil.TEST_AZURE_APP_CLIENT_ID
import no.nav.syfo.utenlandsopphold.testutil.wellKnownInternalAzureAD
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class PodApiTest {
    private val soknadServiceMock = mockk<SoknadService>()

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
                    soknadService = soknadServiceMock,
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
                    soknadService = soknadServiceMock,
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
                    soknadService = soknadServiceMock,
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
                    soknadService = soknadServiceMock,
                    tilgangskontrollClient = mockTilgangskontrollClient(),
                    azureAppClientId = TEST_AZURE_APP_CLIENT_ID,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD(),
                )
            }
            val response = client.get(POD_READINESS_PATH)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}
