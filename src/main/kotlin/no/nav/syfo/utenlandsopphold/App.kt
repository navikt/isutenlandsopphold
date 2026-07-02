package no.nav.syfo.utenlandsopphold

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.infrastructure.database.Database
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.database.databaseConfig
import no.nav.syfo.utenlandsopphold.infrastructure.database.repository.SoknadRepository
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

    val soknadRepository = SoknadRepository(database = database)
    val soknadService = SoknadService(soknadRepository = soknadRepository)

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
                )
                monitor.subscribe(ApplicationStarted) {
                    applicationState.ready = true
                    logger.info("Application is ready, running Java VM ${Runtime.version()}")

                    launchKafkaModule(
                        applicationState = applicationState,
                        environment = Environment(),
                        soknadService = soknadService,
                    )
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
