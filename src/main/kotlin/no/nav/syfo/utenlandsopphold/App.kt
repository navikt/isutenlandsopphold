package no.nav.syfo.utenlandsopphold

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.JournalforVedtakService
import no.nav.syfo.utenlandsopphold.application.JournalforingCronjobConfig
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.application.launchJournalforVedtakCronjob
import no.nav.syfo.utenlandsopphold.infrastructure.clients.ClientsModule
import no.nav.syfo.utenlandsopphold.infrastructure.database.Database
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.database.VedtakRepository
import no.nav.syfo.utenlandsopphold.infrastructure.database.databaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.launchKafkaModule
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("ktor.application")
    val applicationState = ApplicationState()

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
                    databaseConfig(Environment().database)
                },
        )

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
                )
                monitor.subscribe(ApplicationStarted) {
                    applicationState.ready = true
                    logger.info("Application is ready, running Java VM ${Runtime.version()}")

                    launchKafkaModule(
                        applicationState = applicationState,
                        environment = Environment(),
                        soknadService = SoknadService(),
                    )

                    // Klientene (Azure AD, dokarkiv, PDL, ispdfgen) og leder-valg krever
                    // NAIS-injiserte miljøvariabler som ikke finnes lokalt.
                    if (!isLocal()) {
                        val clientsModule = ClientsModule()
                        val journalforVedtakService =
                            JournalforVedtakService(
                                vedtakRepository = VedtakRepository(database = database),
                                personInfoClient = clientsModule.personInfoClient,
                                pdfClient = clientsModule.pdfClient,
                                journalforingService = clientsModule.journalforingService,
                            )

                        launchJournalforVedtakCronjob(
                            applicationState = applicationState,
                            leaderElection = clientsModule.leaderElection,
                            journalforVedtakService = journalforVedtakService,
                            interval = JournalforingCronjobConfig.fromEnv().interval,
                        )
                    }
                }
                monitor.subscribe(ApplicationStopping) {
                    applicationState.ready = false
                    logger.info("Application is stopping")
                }
            },
        )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            applicationState.ready = false
        },
    )

    server.start(wait = true)
}
