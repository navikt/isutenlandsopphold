package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.VedtakOppretting
import no.nav.syfo.utenlandsopphold.domain.paakrevdBegrunnelse
import java.util.UUID

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

        post("/{soknadId}/vedtak") {
            val request = call.receive<SoknadVedtakPostDTO>()

            val soknadId = UUID.fromString(requireNotNull(call.parameters["soknadId"]) { "Missing soknadId" })
            val soknad =
                soknadService.hentSoknad(soknadId = soknadId)
                    ?: throw NotFoundException("Søknad med id $soknadId finnes ikke")

            checkPersonAndSyfoTilgang(
                action = "fatt vedtak om utenlandsopphold for person",
                personident = soknad.personident,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                val behandletSoknad =
                    when (request.utfall) {
                        "HENLAGT" -> {
                            require(request.innvilgedePerioder.isEmpty()) {
                                "innvilgedePerioder skal være tom ved henleggelse"
                            }
                            soknadService.henlegg(
                                soknadId = soknadId,
                                behandletAv = authorizedUser.navident,
                                document = request.document,
                                begrunnelse = paakrevdBegrunnelse(request.begrunnelse, "henleggelse"),
                            )
                        }
                        else ->
                            soknadService.fattVedtak(
                                soknadId = soknadId,
                                vedtakOppretting =
                                    VedtakOppretting.from(
                                        utfall = request.utfall,
                                        innvilgedePerioder = request.innvilgedePerioder.map { it.toDomain() },
                                        begrunnelse = request.begrunnelse,
                                    ),
                                behandletAv = authorizedUser.navident,
                                document = request.document,
                            )
                    }

                call.respond(behandletSoknad.toResponseDTO())
            }
        }
    }
}
