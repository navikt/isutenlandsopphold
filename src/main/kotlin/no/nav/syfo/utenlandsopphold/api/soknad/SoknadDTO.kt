package no.nav.syfo.utenlandsopphold.api.soknad

import no.nav.syfo.utenlandsopphold.domain.Behandlingsutfall
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.util.toLocalDateTimeOslo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SoknaderQueryDTO(
    val personident: String,
)

data class SoknaderResponseDTO(
    val soknader: List<SoknadDTO>,
)

/**
 * Request for å registrere utfallet av behandlingen av en søknad.
 */
data class SoknadBehandlingsutfallPostDTO(
    val utfall: String,
    val innvilgedePerioder: List<PeriodeDTO>,
    val document: List<DocumentComponent>,
    val begrunnelse: String? = null,
)

data class SoknadBehandlingsutfallResponseDTO(
    val soknad: SoknadDTO,
)

/**
 * Legacy request-format for `POST /{soknadId}/vedtak`. Identisk med
 * [SoknadBehandlingsutfallPostDTO], og beholdes kun til frontend har gått over til
 * `/{soknadId}/behandlingsutfall`.
 */
@Deprecated("Bruk SoknadBehandlingsutfallPostDTO", ReplaceWith("SoknadBehandlingsutfallPostDTO"))
data class SoknadVedtakPostDTO(
    val utfall: String,
    val innvilgedePerioder: List<PeriodeDTO>,
    val document: List<DocumentComponent>,
    val begrunnelse: String? = null,
)

@Deprecated("Bruk SoknadBehandlingsutfallResponseDTO", ReplaceWith("SoknadBehandlingsutfallResponseDTO"))
data class SoknadVedtakResponseDTO(
    val soknad: SoknadDTO,
)

/**
 * `vedtak` er utfaset til fordel for `behandlingsutfall`, men beholdes midlertidig med identisk
 * innhold slik at eksisterende frontend-versjoner fortsatt fungerer.
 */
data class SoknadDTO(
    val soknadId: String,
    val eksternId: UUID,
    val status: SoknadStatusDTO,
    val innsendtTidspunkt: LocalDateTime,
    val soktePerioder: List<PeriodeDTO>,
    val behandlingsutfall: BehandlingsutfallDTO?,
    val vedtak: BehandlingsutfallDTO?,
)

data class PeriodeDTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

data class BehandlingsutfallDTO(
    val utfall: String,
    val innvilgedePerioder: List<PeriodeDTO>,
    val fattetAv: String,
    val fattetTidspunkt: LocalDateTime,
    val begrunnelse: String?,
)

enum class SoknadStatusDTO { MOTTATT, INNVILGET, DELVIS_INNVILGET, AVSLAG, HENLAGT }

fun SoknadStatus.toDTO(): SoknadStatusDTO =
    when (this) {
        SoknadStatus.MOTTATT -> SoknadStatusDTO.MOTTATT
        SoknadStatus.INNVILGET -> SoknadStatusDTO.INNVILGET
        SoknadStatus.DELVIS_INNVILGET -> SoknadStatusDTO.DELVIS_INNVILGET
        SoknadStatus.AVSLAG -> SoknadStatusDTO.AVSLAG
        SoknadStatus.HENLAGT -> SoknadStatusDTO.HENLAGT
    }

fun List<Soknad>.toResponseDTO(): SoknaderResponseDTO = SoknaderResponseDTO(soknader = map { it.toDTO() })

fun Soknad.toResponseDTO(): SoknadBehandlingsutfallResponseDTO = SoknadBehandlingsutfallResponseDTO(soknad = toDTO())

@Suppress("DEPRECATION")
fun Soknad.toLegacyVedtakResponseDTO(): SoknadVedtakResponseDTO = SoknadVedtakResponseDTO(soknad = toDTO())

fun Soknad.toDTO(): SoknadDTO {
    val behandlingsutfallDTO = behandlingsutfall?.toDTO()
    return SoknadDTO(
        soknadId = id.toString(),
        eksternId = eksternId,
        status = status.toDTO(),
        innsendtTidspunkt = innsendtTidspunkt.toLocalDateTimeOslo(),
        soktePerioder = soktePerioder.map { it.toDTO() },
        behandlingsutfall = behandlingsutfallDTO,
        vedtak = behandlingsutfallDTO,
    )
}

private fun Periode.toDTO(): PeriodeDTO = PeriodeDTO(fom = fom, tom = tom)

private fun Behandlingsutfall.toDTO(): BehandlingsutfallDTO =
    BehandlingsutfallDTO(
        utfall =
            when (utfall) {
                Utfall.Innvilget -> "INNVILGET"
                is Utfall.DelvisInnvilget -> "DELVIS_INNVILGET"
                Utfall.Avslag -> "AVSLAG"
                Utfall.Henlagt -> "HENLAGT"
            },
        innvilgedePerioder = innvilgedePerioder.map { it.toDTO() },
        fattetAv = fattetAv.value,
        fattetTidspunkt = fattetTidspunkt.toLocalDateTimeOslo(),
        begrunnelse = begrunnelse,
    )

fun PeriodeDTO.toDomain(): Periode = Periode(fom = fom, tom = tom)
