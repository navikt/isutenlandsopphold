package no.nav.syfo.utenlandsopphold.api.soknad

import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.InfoTilBehandlingAvSoknad
import no.nav.syfo.utenlandsopphold.domain.Opptelling
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import no.nav.syfo.utenlandsopphold.domain.infoTilBehandlingAv
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

data class SoknadVedtakPostDTO(
    val utfall: String,
    val innvilgedePerioder: List<PeriodeDTO>,
    val document: List<DocumentComponent>,
    val begrunnelse: String? = null,
)

data class SoknadVedtakResponseDTO(
    val soknad: SoknadDTO,
)

data class SoknadDTO(
    val soknadId: String,
    val eksternId: UUID,
    val status: SoknadStatusDTO,
    val innsendtTidspunkt: LocalDateTime,
    val soktePerioder: List<PeriodeDTO>,
    val vedtak: VedtakDTO?,
    val infoTilBehandlingAvSoknad: InfoTilBehandlingAvSoknadDTO?,
)

data class InfoTilBehandlingAvSoknadDTO(
    val opptellinger: List<OpptellingDTO>,
)

data class OpptellingDTO(
    val fom: LocalDate,
    val tom: LocalDate,
    val antallDagerBruktHvisInnvilget: Int,
    val antallDagerIgjenHvisInnvilget: Int,
    val antallDagerPotensieltBruktHvisInnvilget: Int,
    val antallDagerPotensieltIgjenHvisInnvilget: Int,
    val tidligereInnvilgedePerioder: List<PeriodeDTO>,
    val tidligereUbehandledePerioder: List<PeriodeDTO>,
    val soktePerioderIVinduet: List<PeriodeDTO>,
)

data class PeriodeDTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

data class VedtakDTO(
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

fun List<Soknad>.toResponseDTO(): SoknaderResponseDTO =
    SoknaderResponseDTO(
        soknader =
            map { soknad ->
                soknad.toDTO(
                    infoTilBehandlingAvSoknad =
                        if (soknad.status == SoknadStatus.MOTTATT) {
                            infoTilBehandlingAv(soknad).toDTO()
                        } else {
                            null
                        },
                )
            },
    )

fun Soknad.toResponseDTO(): SoknadVedtakResponseDTO = SoknadVedtakResponseDTO(soknad = toDTO())

fun Soknad.toDTO(infoTilBehandlingAvSoknad: InfoTilBehandlingAvSoknadDTO? = null): SoknadDTO =
    SoknadDTO(
        soknadId = id.toString(),
        eksternId = eksternId,
        status = status.toDTO(),
        innsendtTidspunkt = innsendtTidspunkt.toLocalDateTimeOslo(),
        soktePerioder = soktePerioder.map { it.toDTO() },
        vedtak = vedtak?.toDTO(),
        infoTilBehandlingAvSoknad = infoTilBehandlingAvSoknad,
    )

private fun InfoTilBehandlingAvSoknad.toDTO(): InfoTilBehandlingAvSoknadDTO =
    InfoTilBehandlingAvSoknadDTO(opptellinger = opptellinger.map { it.toDTO() })

private fun Opptelling.toDTO(): OpptellingDTO =
    OpptellingDTO(
        fom = fom,
        tom = tom,
        antallDagerBruktHvisInnvilget = antallDagerBruktHvisInnvilget,
        antallDagerIgjenHvisInnvilget = antallDagerIgjenHvisInnvilget,
        antallDagerPotensieltBruktHvisInnvilget = antallDagerPotensieltBruktHvisInnvilget,
        antallDagerPotensieltIgjenHvisInnvilget = antallDagerPotensieltIgjenHvisInnvilget,
        soktePerioderIVinduet = soktePerioderIVinduet.map { it.toDTO() },
        tidligereInnvilgedePerioder = tidligereInnvilgedePerioder.map { it.toDTO() },
        tidligereUbehandledePerioder = tidligereUbehandledePerioder.map { it.toDTO() },
    )

private fun Periode.toDTO(): PeriodeDTO = PeriodeDTO(fom = fom, tom = tom)

private fun Vedtak.toDTO(): VedtakDTO =
    VedtakDTO(
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
