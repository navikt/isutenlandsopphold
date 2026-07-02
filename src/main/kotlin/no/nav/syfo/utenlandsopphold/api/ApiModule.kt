package no.nav.syfo.utenlandsopphold.api

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.tilgangskontroll.TilgangDeniedException
import no.nav.syfo.common.util.applyCommonJacksonConfig
import no.nav.syfo.common.util.consumerClientId
import no.nav.syfo.utenlandsopphold.api.endpoints.registerPodApi
import no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    soknadService: SoknadService,
) {
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database,
        )
        registerSoknadApi(soknadService = soknadService)
    }
}

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson {
            applyCommonJacksonConfig()
        }
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val log = call.application.log
            log
                .atError()
                .setCause(cause)
                .setMessage("Caught exception: ${cause.message}")
                .addKeyValue("consumerClientId", call.consumerClientId)
                .log()

            var isUnexpectedException = false
            val responseStatus =
                when (cause) {
                    is BadRequestException -> HttpStatusCode.BadRequest
                    is IllegalArgumentException -> HttpStatusCode.BadRequest
                    is TilgangDeniedException -> HttpStatusCode.Forbidden
                    else -> {
                        isUnexpectedException = true
                        HttpStatusCode.InternalServerError
                    }
                }

            val message =
                if (isUnexpectedException) {
                    "The server reported an unexpected error and cannot complete the request."
                } else {
                    cause.message ?: "Unknown error"
                }
            call.respond(responseStatus, message)
        }
    }
}
