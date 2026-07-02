package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.SoknadService

fun Route.registerSoknadApi(soknadService: SoknadService) {
    route("/api/v1/soknader") {
        post("/query") {
            val request = call.receive<SoknaderQueryDTO>()
            val personident = Personident(request.personident)

            val soknader = soknadService.hentSoknader(personident)

            call.respond(soknader.toResponseDTO())
        }
    }
}
