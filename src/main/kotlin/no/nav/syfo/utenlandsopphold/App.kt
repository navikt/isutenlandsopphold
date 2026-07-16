package no.nav.syfo.utenlandsopphold

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.common.auth.getWellKnown
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.token.azuread.AzureAdClient
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.JournalforVedtakService
import no.nav.syfo.utenlandsopphold.application.JournalforingCronjobConfig
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.application.launchJournalforVedtakCronjob
import no.nav.syfo.utenlandsopphold.infrastructure.clients.ClientsModule
import no.nav.syfo.utenlandsopphold.infrastructure.database.Database
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.database.JdbcTransactionManager
import no.nav.syfo.utenlandsopphold.infrastructure.database.databaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.database.repository.SoknadRepository
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.launchKafkaModule
import no.nav.syfo.utenlandsopphold.infrastructure.tilgangskontroll.TilgangskontrollClientConfig
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("ktor.application")
    val applicationState = ApplicationState()
    val environment = Environment()

    val database =
        Database(
            config =
                if (isLocal()) {
                    DatabaseConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/isutenlandsopphold_dev",
                        username = "username",
                        password = "password",
                    )
                } else {
                    databaseConfig(environment.database)
                },
        )

    val transactionManager = JdbcTransactionManager(database = database)
    val soknadRepository = SoknadRepository(database = database)
    val soknadService =
        SoknadService(
            transactionManager = transactionManager,
            soknadRepository = soknadRepository,
        )

    val wellKnownInternalAzureAD = getWellKnown(environment.azure.appWellKnownUrl)

    val tilgangskontrollClient =
        TilgangskontrollClient(
            oboTokenProvider = AzureAdClient(),
            clientConfig = TilgangskontrollClientConfig.fromEnv(),
        )

    // Klientene (Azure AD, dokarkiv, PDL, ispdfgen) og leder-valg krever
    // miljøvariabler som ikke finnes lokalt.
    val clientsModule = if (isLocal()) null else ClientsModule()
    val journalforingCronjobConfig = if (isLocal()) null else JournalforingCronjobConfig.fromEnv()
    val journalforVedtakService =
        clientsModule?.let {
            JournalforVedtakService(
                soknadRepository = soknadRepository,
                personInfoClient = it.personInfoClient,
                pdfClient = it.pdfClient,
                journalforingService = it.journalforingService,
                distribusjonService = it.distribusjonService,
                freshVedtakGracePeriod = checkNotNull(journalforingCronjobConfig).freshVedtakGracePeriod,
            )
        }

    val server =
        embeddedServer(
            Netty,
            configure = {
                connector {
                    port = 8080
                }
                connectionGroupSize = 8
                workerGroupSize = 8
                callGroupSize = 16
            },
            module = {
                apiModule(
                    applicationState = applicationState,
                    database = database,
                    soknadService = soknadService,
                    tilgangskontrollClient = tilgangskontrollClient,
                    azureAppClientId = environment.azure.appClientId,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                    journalforVedtakService = journalforVedtakService,
                )
                monitor.subscribe(ApplicationStarted) {
                    applicationState.ready = true
                    logger.info("Application is ready, running Java VM ${Runtime.version()}")

                    launchKafkaModule(
                        applicationState = applicationState,
                        environment = environment,
                        soknadService = soknadService,
                    )

                    if (clientsModule != null && journalforVedtakService != null && journalforingCronjobConfig != null) {
                        launchJournalforVedtakCronjob(
                            applicationState = applicationState,
                            leaderElection = clientsModule.leaderElection,
                            journalforVedtakService = journalforVedtakService,
                            interval = journalforingCronjobConfig.interval,
                        )
                    }
                }
                monitor.subscribe(ApplicationStopping) {
                    applicationState.ready = false
                    logger.info("Application is stopping")
                }
            },
        )

    server.start(wait = true)
}
