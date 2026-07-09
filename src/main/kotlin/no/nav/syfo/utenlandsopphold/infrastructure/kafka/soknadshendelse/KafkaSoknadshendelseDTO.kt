package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadshendelse

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.domain.ManglerSendtNavException
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private const val TAG_PERIODEUTLAND = "PERIODEUTLAND"

private val objectMapper = configuredJacksonMapper()

data class KafkaSykepengesoknadDTO(
    val id: String,
    val fnr: String,
    val status: KafkaSoknadstatusDTO,
    val type: KafkaSoknadstypeDTO,
    val sendtNav: LocalDateTime?,
    val sporsmal: List<KafkaSporsmalDTO> = emptyList(),
)

enum class KafkaSoknadstypeDTO {
    SELVSTENDIGE_OG_FRILANSERE,
    OPPHOLD_UTLAND,
    ARBEIDSTAKERE,
    ANNET_ARBEIDSFORHOLD,
    ARBEIDSLEDIG,
    BEHANDLINGSDAGER,
    REISETILSKUDD,
    GRADERT_REISETILSKUDD,
    FRISKMELDT_TIL_ARBEIDSFORMIDLING,
}

enum class KafkaSoknadstatusDTO {
    NY,
    SENDT,
    FREMTIDIG,
    UTKAST_TIL_KORRIGERING,
    KORRIGERT,
    AVBRUTT,
    UTGAATT,
    SLETTET,
}

data class KafkaSporsmalDTO(
    val tag: String,
    val svar: List<KafkaSvarDTO> = emptyList(),
)

data class KafkaSvarDTO(
    val verdi: String,
)

// Svaret for PERIODEUTLAND er en JSON-serialisert periode, f.eks. {"fom":"2024-01-01","tom":"2024-01-10"}.
private data class KafkaPeriodeSvarDTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

fun KafkaSykepengesoknadDTO.toSoknad(): Soknad {
    val soktePerioder =
        sporsmal
            .firstOrNull { it.tag == TAG_PERIODEUTLAND }
            ?.svar
            ?.map { it.verdi.tilPeriode() }
            .orEmpty()

    return Soknad(
        eksternId = UUID.fromString(id),
        personident = Personident(fnr),
        soktePerioder = soktePerioder,
        innsendtTidspunkt =
            (sendtNav ?: throw ManglerSendtNavException(id)).atZone(osloZone).toOffsetDateTime(),
    )
}

val osloZone: ZoneId = ZoneId.of("Europe/Oslo")

private fun String.tilPeriode(): Periode {
    val periodeSvar = objectMapper.readValue(this, KafkaPeriodeSvarDTO::class.java)
    return Periode(fom = periodeSvar.fom, tom = periodeSvar.tom)
}
