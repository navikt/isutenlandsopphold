package no.nav.syfo.utenlandsopphold.api.endpoints

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.utenlandsopphold.application.ApplicationState

const val POD_LIVENESS_PATH = "/internal/is_alive"
const val POD_READINESS_PATH = "/internal/is_ready"

fun Routing.registerPodApi(applicationState: ApplicationState) {
    get(POD_LIVENESS_PATH) {
        if (applicationState.alive) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get(POD_READINESS_PATH) {
        if (isReady(applicationState)) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun isReady(applicationState: ApplicationState): Boolean = applicationState.ready
