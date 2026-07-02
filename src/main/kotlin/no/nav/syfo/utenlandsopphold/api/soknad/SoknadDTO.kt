package no.nav.syfo.utenlandsopphold.api.soknad

import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SoknaderQueryDTO(
    val personident: String,
)

data class SoknaderResponseDTO(
    val soknader: List<SoknadDTO>,
)

data class SoknadDTO(
    val soknadId: String,
    val eksternId: UUID,
    val status: SoknadStatusDTO,
    val innsendtTidspunkt: Instant,
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

fun List<Soknad>.toResponseDTO(): SoknaderResponseDTO = SoknaderResponseDTO(soknader = map { it.toDTO() })

fun Soknad.toDTO(): SoknadDTO =
    SoknadDTO(
        soknadId = id.toString(),
        eksternId = eksternId,
        status = status.toDTO(),
        innsendtTidspunkt = innsendtTidspunkt,
        soktePerioder = soktePerioder.map { it.toDTO() },
        vedtak = vedtak?.toDTO(),
    )

private fun Periode.toDTO(): PeriodeDTO = PeriodeDTO(fom = fom, tom = tom)

private fun Vedtak.toDTO(): VedtakDTO =
    VedtakDTO(
        utfall =
            when (utfall) {
                Utfall.Innvilget -> "INNVILGET"
            },
        innvilgetePerioder = innvilgetePerioder.map { it.toDTO() },
        fattetAv = fattetAv.value,
        fattetTidspunkt = fattetTidspunkt,
    )
