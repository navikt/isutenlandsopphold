package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.Soknad
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

        post("/{soknadId}/behandlingsutfall") {
            val request = call.receive<SoknadBehandlingsutfallPostDTO>()

            registrerBehandlingsutfall(
                request = request,
                soknadService = soknadService,
                tilgangskontrollClient = tilgangskontrollClient,
            ) { soknad -> call.respond(soknad.toResponseDTO()) }
        }

        /**
         * Utfaset til fordel for `POST /{soknadId}/behandlingsutfall`. Beholdes midlertidig for
         * bakoverkompatibilitet til frontend har gått over, og delegerer til nøyaktig samme flyt.
         */
        @Suppress("DEPRECATION")
        post("/{soknadId}/vedtak") {
            val legacyRequest = call.receive<SoknadVedtakPostDTO>()
            val request =
                SoknadBehandlingsutfallPostDTO(
                    utfall = legacyRequest.utfall,
                    innvilgedePerioder = legacyRequest.innvilgedePerioder,
                    document = legacyRequest.document,
                    begrunnelse = legacyRequest.begrunnelse,
                )

            registrerBehandlingsutfall(
                request = request,
                soknadService = soknadService,
                tilgangskontrollClient = tilgangskontrollClient,
            ) { soknad -> call.respond(soknad.toLegacyVedtakResponseDTO()) }
        }
    }
}

/**
 * Felles flyt for registrering av behandlingsutfall, delt av det gjeldende
 * `/{soknadId}/behandlingsutfall`-endepunktet og det utfasede `/{soknadId}/vedtak`-endepunktet,
 * slik at validering, tilgangskontroll og domenekall ikke kan komme i utakt mellom de to.
 */
private suspend fun RoutingContext.registrerBehandlingsutfall(
    request: SoknadBehandlingsutfallPostDTO,
    soknadService: SoknadService,
    tilgangskontrollClient: TilgangskontrollClient,
    respond: suspend (Soknad) -> Unit,
) {
    require(request.document.isNotEmpty()) { "document kan ikke være tomt" }
    when (request.utfall) {
        "AVSLAG", "DELVIS_INNVILGET", "HENLAGT" ->
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
        action = "registrer behandlingsutfall for søknad om utenlandsopphold for person",
        personident = soknad.personident,
        tilgangskontrollClient = tilgangskontrollClient,
        requiresWriteAccess = true,
    ) { authorizedUser, _, _ ->
        val utfall =
            Utfall.from(
                utfall = request.utfall,
                innvilgedePerioder = request.innvilgedePerioder.map { it.toDomain() },
            )
        val soknadMedBehandlingsutfall =
            soknadService.registrerBehandlingsutfall(
                soknadId = soknadId,
                utfall = utfall,
                fattetAv = authorizedUser.navident,
                document = request.document,
                begrunnelse = request.begrunnelse,
            )

        respond(soknadMedBehandlingsutfall)
    }
}
