package no.nav.syfo.utenlandsopphold

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.utenlandsopphold.api.apiModule
import no.nav.syfo.utenlandsopphold.application.ApplicationState

fun main(args: Array<String>) {
    val applicationState =
        ApplicationState(
            alive = true,
            ready = true,
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
                )
            },
        )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            applicationState.ready = false
        },
    )

    server.start(wait = true)
}
