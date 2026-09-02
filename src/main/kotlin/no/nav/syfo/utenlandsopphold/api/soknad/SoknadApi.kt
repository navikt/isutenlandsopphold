package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.Utfall
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
            require(request.document.isNotEmpty()) { "document kan ikke være tomt" }
            when (request.utfall) {
                "AVSLAG", "DELVIS_INNVILGET" ->
                    require(!request.begrunnelse.isNullOrBlank()) {
                        "begrunnelse er påkrevd og kan ikke være blank for utfall ${request.utfall}"
                    }
                "INNVILGET" ->
                    require(request.begrunnelse == null) {
                        "begrunnelse skal ikke settes for utfall INNVILGET"
                    }
            }

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
                val utfall =
                    Utfall.from(
                        utfall = request.utfall,
                        innvilgedePerioder = request.innvilgedePerioder.map { it.toDomain() },
                    )
                val soknadMedVedtak =
                    soknadService.fattVedtak(
                        soknadId = soknadId,
                        utfall = utfall,
                        fattetAv = authorizedUser.navident,
                        document = request.document,
                        begrunnelse = request.begrunnelse,
                    )

                call.respond(soknadMedVedtak.toResponseDTO())
            }
        }

        post("/{soknadId}/henleggelse") {
            val request = call.receive<SoknadHenleggelsePostDTO>()
            require(request.document.isNotEmpty()) { "document kan ikke være tomt" }
            require(request.begrunnelse.isNotBlank()) { "begrunnelse er påkrevd og kan ikke være blank for henleggelse" }

            val soknadId = UUID.fromString(requireNotNull(call.parameters["soknadId"]) { "Missing soknadId" })
            val soknad =
                soknadService.hentSoknad(soknadId = soknadId)
                    ?: throw NotFoundException("Søknad med id $soknadId finnes ikke")

            checkPersonAndSyfoTilgang(
                action = "henlegg søknad om utenlandsopphold for person",
                personident = soknad.personident,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                val soknadMedHenleggelse =
                    soknadService.henlegg(
                        soknadId = soknadId,
                        fattetAv = authorizedUser.navident,
                        document = request.document,
                        begrunnelse = request.begrunnelse,
                    )

                call.respond(soknadMedHenleggelse.toResponseDTO())
            }
        }
    }
}
