package no.nav.syfo.utenlandsopphold.api.soknad

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.common.types.ident.Personident
import java.time.Instant
import java.time.LocalDate

fun Route.registerSoknadApi() {
    route("/api/v1/soknader") {
        post("/query") {
            val request = call.receive<SoknaderQueryDTO>()

            // Personident hentes ut av requesten, men brukes ikke ennaa - responsen er mocket.
            @Suppress("UNUSED_VARIABLE")
            val personident = Personident(request.personident)

            call.respond(mockSoknaderResponse())
        }
    }
}

private fun mockSoknaderResponse(): SoknaderResponseDTO =
    SoknaderResponseDTO(
        soknader =
            listOf(
                SoknadDTO(
                    soknadId = "1a2b3c4d-5e6f-7890-abcd-ef0987654321",
                    status = SoknadStatusDTO.MOTTATT,
                    innsendtTidspunkt = Instant.parse("2026-05-15T08:30:00Z"),
                    soktePerioder =
                        listOf(
                            PeriodeDTO(
                                fom = LocalDate.of(2026, 6, 1),
                                tom = LocalDate.of(2026, 6, 7),
                            ),
                        ),
                    vedtak = null,
                ),
                SoknadDTO(
                    soknadId = "9b1c2d3e-4f56-7890-abcd-ef1234567890",
                    status = SoknadStatusDTO.INNVILGET,
                    innsendtTidspunkt = Instant.parse("2026-03-01T09:00:00Z"),
                    soktePerioder =
                        listOf(
                            PeriodeDTO(
                                fom = LocalDate.of(2026, 4, 1),
                                tom = LocalDate.of(2026, 4, 10),
                            ),
                        ),
                    vedtak =
                        VedtakDTO(
                            utfall = "INNVILGET",
                            innvilgetePerioder =
                                listOf(
                                    PeriodeDTO(
                                        fom = LocalDate.of(2026, 4, 1),
                                        tom = LocalDate.of(2026, 4, 5),
                                    ),
                                ),
                            fattetAv = "Z990000",
                            fattetTidspunkt = Instant.parse("2026-03-02T11:00:00Z"),
                        ),
                ),
            ),
    )
