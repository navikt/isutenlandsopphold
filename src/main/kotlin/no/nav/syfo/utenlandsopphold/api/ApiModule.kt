package no.nav.syfo.utenlandsopphold.api

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.syfo.utenlandsopphold.api.endpoints.registerPodApi
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
) {
    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database,
        )
    }
}
