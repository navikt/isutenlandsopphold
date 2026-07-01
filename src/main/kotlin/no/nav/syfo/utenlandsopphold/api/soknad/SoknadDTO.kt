package no.nav.syfo.utenlandsopphold.api.soknad

import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import java.time.Instant
import java.time.LocalDate

data class SoknaderRequestDTO(
    val personident: String,
)

data class SoknaderResponseDTO(
    val soknader: List<SoknadDTO>,
)

data class SoknadDTO(
    val soknadId: String,
    val status: SoknadStatusDTO,
    val mottattTidspunkt: Instant,
    val soktePerioder: List<PeriodeDTO>,
    val vedtak: VedtakDTO?,
)

data class PeriodeDTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

data class VedtakDTO(
    val utfall: String,
    val innvilgetePerioder: List<PeriodeDTO>,
    val fattetAv: String,
    val fattetTidspunkt: Instant,
)

enum class SoknadStatusDTO { MOTTATT, INNVILGET }

fun SoknadStatus.toDTO(): SoknadStatusDTO =
    when (this) {
        SoknadStatus.MOTTATT -> SoknadStatusDTO.MOTTATT
        SoknadStatus.INNVILGET -> SoknadStatusDTO.INNVILGET
    }
