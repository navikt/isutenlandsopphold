package no.nav.syfo.utenlandsopphold.api.soknad.v2

import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.VedtakOppretting
import java.util.UUID

/**
 * v2 av søknads-APIet. Hovedforskjellen fra v1 er at hver måte å behandle en søknad
 * på har sitt eget endepunkt, i stedet for én `utfall`-diskriminator på `/vedtak`.
 */
fun Route.registerSoknadApiV2(
    soknadService: SoknadService,
    tilgangskontrollClient: TilgangskontrollClient,
) {
    route("/api/v2/soknader") {
        post("/query") {
            val request = call.receive<SoknaderQueryV2DTO>()
            val personident = Personident(request.personident)

            checkPersonAndSyfoTilgang(
                action = "hent søknader om utenlandsopphold for person",
                personident = personident,
                tilgangskontrollClient = tilgangskontrollClient,
            ) { _, personident, _ ->
                val soknader = soknadService.hentSoknader(personident)

                call.respond(soknader.toResponseV2DTO())
            }
        }

        post("/{soknadId}/vedtak") {
            val request = call.receive<VedtakPostV2DTO>()
            val soknad = hentSoknad(soknadService)

            checkPersonAndSyfoTilgang(
                action = "fatt vedtak om utenlandsopphold for person",
                personident = soknad.personident,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                val behandletSoknad =
                    soknadService.fattVedtak(
                        soknadId = soknad.id,
                        vedtakOppretting =
                            VedtakOppretting.from(
                                utfall = request.utfall,
                                innvilgedePerioder = request.innvilgedePerioder.map { it.toDomain() },
                                begrunnelse = request.begrunnelse,
                            ),
                        behandletAv = authorizedUser.navident,
                        document = request.document,
                    )

                call.respond(behandletSoknad.toResponseV2DTO())
            }
        }

        post("/{soknadId}/henleggelse") {
            val request = call.receive<HenleggelsePostV2DTO>()
            val soknad = hentSoknad(soknadService)

            checkPersonAndSyfoTilgang(
                action = "henlegg søknad om utenlandsopphold for person",
                personident = soknad.personident,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                val behandletSoknad =
                    soknadService.henlegg(
                        soknadId = soknad.id,
                        behandletAv = authorizedUser.navident,
                        document = request.document,
                        begrunnelse = request.begrunnelse,
                    )

                call.respond(behandletSoknad.toResponseV2DTO())
            }
        }

        post("/{soknadId}/ikke-aktuell") {
            val request = call.receive<IkkeAktuellPostV2DTO>()
            val soknad = hentSoknad(soknadService)

            checkPersonAndSyfoTilgang(
                action = "merk søknad om utenlandsopphold som ikke aktuell for person",
                personident = soknad.personident,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                val behandletSoknad =
                    soknadService.merkIkkeAktuell(
                        soknadId = soknad.id,
                        grunn = request.grunn.toDomain(),
                        behandletAv = authorizedUser.navident,
                    )

                call.respond(behandletSoknad.toResponseV2DTO())
            }
        }
    }
}

/**
 * Slår opp søknaden fra `soknadId` i stien. Oppslaget må skje før tilgangssjekken,
 * siden personidenten kommer fra søknaden og ikke fra forespørselen.
 */
private fun RoutingContext.hentSoknad(soknadService: SoknadService): Soknad {
    val soknadId = UUID.fromString(requireNotNull(call.parameters["soknadId"]) { "Missing soknadId" })

    return soknadService.hentSoknad(soknadId = soknadId)
        ?: throw NotFoundException("Søknad med id $soknadId finnes ikke")
}
