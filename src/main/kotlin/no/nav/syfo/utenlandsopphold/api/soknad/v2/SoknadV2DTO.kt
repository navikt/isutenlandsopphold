package no.nav.syfo.utenlandsopphold.api.soknad.v2

import no.nav.syfo.utenlandsopphold.domain.Behandling
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellGrunn
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.util.toLocalDateTimeOslo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * v2-kontrakten mot frontend. Typene er bevisst duplisert fra v1 framfor gjenbrukt
 * slik at de er uavhengige og v1 kan slettes i sin helhet.
 */

data class SoknaderQueryV2DTO(
    val personident: String,
)

data class SoknaderResponseV2DTO(
    val soknader: List<SoknadV2DTO>,
)

data class SoknadResponseV2DTO(
    val soknad: SoknadV2DTO,
)

data class VedtakPostV2DTO(
    val utfall: String,
    val innvilgedePerioder: List<PeriodeV2DTO>,
    val document: List<DocumentComponent>,
    val begrunnelse: String? = null,
)

data class HenleggelsePostV2DTO(
    val document: List<DocumentComponent>,
    val begrunnelse: String,
)

data class IkkeAktuellPostV2DTO(
    val grunn: IkkeAktuellGrunnV2DTO,
)

data class SoknadV2DTO(
    val soknadId: String,
    val eksternId: UUID,
    val status: SoknadStatusV2DTO,
    val innsendtTidspunkt: LocalDateTime,
    val soktePerioder: List<PeriodeV2DTO>,
    val behandling: BehandlingV2DTO?,
)

data class PeriodeV2DTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

data class BehandlingV2DTO(
    val utfall: UtfallV2DTO,
    val innvilgedePerioder: List<PeriodeV2DTO>,
    val behandletAv: String,
    val behandletTidspunkt: LocalDateTime,
    val begrunnelse: String?,
    val ikkeAktuellGrunn: IkkeAktuellGrunnV2DTO?,
)

enum class SoknadStatusV2DTO { MOTTATT, INNVILGET, DELVIS_INNVILGET, AVSLAG, HENLAGT, IKKE_AKTUELL }

enum class UtfallV2DTO { INNVILGET, DELVIS_INNVILGET, AVSLAG, HENLAGT, IKKE_AKTUELL }

enum class IkkeAktuellGrunnV2DTO { BEHANDLET_I_INFOTRYGD, DUPLIKAT, ANNET }

fun IkkeAktuellGrunnV2DTO.toDomain(): IkkeAktuellGrunn =
    when (this) {
        IkkeAktuellGrunnV2DTO.BEHANDLET_I_INFOTRYGD -> IkkeAktuellGrunn.BEHANDLET_I_INFOTRYGD
        IkkeAktuellGrunnV2DTO.DUPLIKAT -> IkkeAktuellGrunn.DUPLIKAT
        IkkeAktuellGrunnV2DTO.ANNET -> IkkeAktuellGrunn.ANNET
    }

private fun IkkeAktuellGrunn.toV2DTO(): IkkeAktuellGrunnV2DTO =
    when (this) {
        IkkeAktuellGrunn.BEHANDLET_I_INFOTRYGD -> IkkeAktuellGrunnV2DTO.BEHANDLET_I_INFOTRYGD
        IkkeAktuellGrunn.DUPLIKAT -> IkkeAktuellGrunnV2DTO.DUPLIKAT
        IkkeAktuellGrunn.ANNET -> IkkeAktuellGrunnV2DTO.ANNET
    }

private fun SoknadStatus.toV2DTO(): SoknadStatusV2DTO =
    when (this) {
        SoknadStatus.MOTTATT -> SoknadStatusV2DTO.MOTTATT
        SoknadStatus.INNVILGET -> SoknadStatusV2DTO.INNVILGET
        SoknadStatus.DELVIS_INNVILGET -> SoknadStatusV2DTO.DELVIS_INNVILGET
        SoknadStatus.AVSLAG -> SoknadStatusV2DTO.AVSLAG
        SoknadStatus.HENLAGT -> SoknadStatusV2DTO.HENLAGT
        SoknadStatus.IKKE_AKTUELL -> SoknadStatusV2DTO.IKKE_AKTUELL
    }

private fun Utfall.toV2DTO(): UtfallV2DTO =
    when (this) {
        Utfall.Innvilget -> UtfallV2DTO.INNVILGET
        is Utfall.DelvisInnvilget -> UtfallV2DTO.DELVIS_INNVILGET
        Utfall.Avslag -> UtfallV2DTO.AVSLAG
        Utfall.Henlagt -> UtfallV2DTO.HENLAGT
        is Utfall.IkkeAktuell -> UtfallV2DTO.IKKE_AKTUELL
    }

fun List<Soknad>.toResponseV2DTO(): SoknaderResponseV2DTO = SoknaderResponseV2DTO(soknader = map { it.toV2DTO() })

fun Soknad.toResponseV2DTO(): SoknadResponseV2DTO = SoknadResponseV2DTO(soknad = toV2DTO())

fun Soknad.toV2DTO(): SoknadV2DTO =
    SoknadV2DTO(
        soknadId = id.toString(),
        eksternId = eksternId,
        status = status.toV2DTO(),
        innsendtTidspunkt = innsendtTidspunkt.toLocalDateTimeOslo(),
        soktePerioder = soktePerioder.map { it.toV2DTO() },
        behandling = behandling?.toV2DTO(),
    )

private fun Periode.toV2DTO(): PeriodeV2DTO = PeriodeV2DTO(fom = fom, tom = tom)

private fun Behandling.toV2DTO(): BehandlingV2DTO =
    BehandlingV2DTO(
        utfall = utfall.toV2DTO(),
        innvilgedePerioder = innvilgedePerioder.map { it.toV2DTO() },
        behandletAv = behandletAv.value,
        behandletTidspunkt = behandletTidspunkt.toLocalDateTimeOslo(),
        begrunnelse = begrunnelse,
        ikkeAktuellGrunn = (utfall as? Utfall.IkkeAktuell)?.grunn?.toV2DTO(),
    )

fun PeriodeV2DTO.toDomain(): Periode = Periode(fom = fom, tom = tom)
