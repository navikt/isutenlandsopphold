package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.JournalforVedtakService
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.application.launchAsyncTask
import no.nav.syfo.utenlandsopphold.domain.Utfall
import org.slf4j.LoggerFactory
import java.util.UUID

fun Route.registerSoknadApi(
    soknadService: SoknadService,
    tilgangskontrollClient: TilgangskontrollClient,
    journalforVedtakService: JournalforVedtakService?,
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
                val utfall =
                    when (request.utfall) {
                        "INNVILGET" -> Utfall.Innvilget
                        else -> throw IllegalArgumentException("Invalid utfall: ${request.utfall}")
                    }
                require(request.document.isNotEmpty()) { "document kan ikke være tom" }

                val soknadMedVedtak =
                    soknadService.fattVedtak(
                        soknadId = soknadId,
                        utfall = utfall,
                        fattetAv = authorizedUser.navident,
                        document = request.document,
                    )

                if (journalforVedtakService != null) {
                    // Forsøker journalføring umiddelbart som en fire-and-forget bakgrunnsoppgave.
                    // Feiler dette, plukkes vedtaket likevel opp av den periodiske cronjobben
                    // (launchJournalforVedtakCronjob) etter dens gradeperiode for ferske vedtak.
                    launchAsyncTask {
                        try {
                            journalforVedtakService.journalforVedtak(soknadMedVedtak)
                        } catch (exception: Exception) {
                            log.error("Feil ved umiddelbar journalføring av vedtak for søknad $soknadId", exception)
                        }
                    }
                }

                call.respond(soknadMedVedtak.toResponseDTO())
            }
        }
    }
}

private val log = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.api.soknad.SoknadApi")
