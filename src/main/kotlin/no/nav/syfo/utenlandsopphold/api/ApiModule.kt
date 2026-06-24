package no.nav.syfo.utenlandsopphold.api

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.syfo.utenlandsopphold.api.endpoints.registerPodApi
import no.nav.syfo.utenlandsopphold.application.ApplicationState

fun Application.apiModule(
    applicationState: ApplicationState,
) {
    routing {
        registerPodApi(
            applicationState = applicationState,
        )
    }
}
