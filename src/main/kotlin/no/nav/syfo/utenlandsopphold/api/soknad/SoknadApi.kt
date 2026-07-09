package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.SoknadService

fun Route.registerSoknadApi(
    soknadService: SoknadService,
    tilgangskontrollClient: TilgangskontrollClient,
) {
    route("/api/v1/soknader") {
        post("/query") {
            val request = call.receive<SoknaderQueryDTO>()
            val personident = Personident(request.personident)

            checkPersonAndSyfoTilgang(
                action = "hent søknader om utenlandsopphold for person",
                personident = personident,
                tilgangskontrollClient = tilgangskontrollClient,
            ) { _, personident, _ ->
                val soknader = soknadService.hentSoknader(personident)

                call.respond(soknader.toResponseDTO())
            }
        }
    }
}
