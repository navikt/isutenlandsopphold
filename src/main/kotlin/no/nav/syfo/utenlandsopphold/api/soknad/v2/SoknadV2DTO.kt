package no.nav.syfo.utenlandsopphold.api.soknad.v2

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.syfo.utenlandsopphold.domain.Behandling
import no.nav.syfo.utenlandsopphold.domain.BehandlingsUtfall
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellArsak
import no.nav.syfo.utenlandsopphold.domain.InfoTilBehandlingAvSoknad
import no.nav.syfo.utenlandsopphold.domain.MAKS_ANTALL_DAGER_UTENFOR_EOS
import no.nav.syfo.utenlandsopphold.domain.Opptelling
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.VedtaksUtfall
import no.nav.syfo.utenlandsopphold.domain.infoTilBehandlingAv
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
    val utfall: VedtaksUtfall,
    val document: List<DocumentComponent>,
    val innvilgedePerioder: List<PeriodeV2DTO>? = null,
    val begrunnelse: String? = null,
)

data class HenleggelsePostV2DTO(
    val document: List<DocumentComponent>,
    val begrunnelse: String,
)

data class IkkeAktuellPostV2DTO(
    val arsak: IkkeAktuellArsakV2DTO,
)

data class SoknadV2DTO(
    val soknadId: String,
    val eksternId: UUID,
    val status: SoknadStatusV2DTO,
    val innsendtTidspunkt: LocalDateTime,
    val soktePerioder: List<PeriodeV2DTO>,
    val behandling: BehandlingV2DTO?,
    val infoTilBehandling: InfoTilBehandlingV2DTO?,
)

data class PeriodeV2DTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

/**
 * Informasjon veileder trenger for å behandle en søknad. Settes kun for søknader som ikke er behandlet.
 */
data class InfoTilBehandlingV2DTO(
    val opptellinger: List<OpptellingV2DTO>,
)

/**
 * Opptelling av dager utenfor EØS i et vindu på ett år som slutter på [tom], én per søkte periode.
 * Tallene svarer på hva resultatet blir dersom søknaden innvilges. "Potensielt" tar i tillegg med dager fra
 * andre ubehandlede søknader og søknader behandlet i Infotrygd. Dager igjen er [maksAntallDager] minus brukt, og kan
 * være negativt hvis grensen overskrides.
 */
data class OpptellingV2DTO(
    val fom: LocalDate,
    val tom: LocalDate,
    val maksAntallDager: Int,
    val antallDagerBruktHvisInnvilget: Int,
    val antallDagerPotensieltBruktHvisInnvilget: Int,
    val soktePerioderIVinduet: List<PeriodeV2DTO>,
    val tidligereInnvilgedePerioder: List<PeriodeV2DTO>,
    val tidligereUbehandledePerioder: List<PeriodeV2DTO>,
)

/**
 * Behandlingen slik den ser ut for frontend. Hver variant har bare feltene som gjelder
 * for sitt utfall, slik at kontrakten slipper å love felt som nesten alltid er null.
 *
 * `utfall` i JSON skiller variantene. Avslag og henleggelse har ellers helt lik form, så
 * uten det feltet ville de vært umulige å skille fra hverandre. Verdiene er skrevet ut i
 * [JsonSubTypes] slik at de står uavhengig av hva klassene heter i Kotlin.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "utfall",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = InnvilgetBehandlingV2DTO::class, name = "INNVILGET"),
    JsonSubTypes.Type(value = DelvisInnvilgetBehandlingV2DTO::class, name = "DELVIS_INNVILGET"),
    JsonSubTypes.Type(value = AvslagBehandlingV2DTO::class, name = "AVSLAG"),
    JsonSubTypes.Type(value = HenlagtBehandlingV2DTO::class, name = "HENLAGT"),
    JsonSubTypes.Type(value = IkkeAktuellBehandlingV2DTO::class, name = "IKKE_AKTUELL"),
)
sealed interface BehandlingV2DTO {
    val behandletAv: String
    val behandletTidspunkt: LocalDateTime
}

data class InnvilgetBehandlingV2DTO(
    override val behandletAv: String,
    override val behandletTidspunkt: LocalDateTime,
    val innvilgedePerioder: List<PeriodeV2DTO>,
    val begrunnelse: String? = null,
) : BehandlingV2DTO

data class DelvisInnvilgetBehandlingV2DTO(
    override val behandletAv: String,
    override val behandletTidspunkt: LocalDateTime,
    val innvilgedePerioder: List<PeriodeV2DTO>,
    val begrunnelse: String,
) : BehandlingV2DTO

data class AvslagBehandlingV2DTO(
    override val behandletAv: String,
    override val behandletTidspunkt: LocalDateTime,
    val begrunnelse: String,
) : BehandlingV2DTO

data class HenlagtBehandlingV2DTO(
    override val behandletAv: String,
    override val behandletTidspunkt: LocalDateTime,
    val begrunnelse: String,
) : BehandlingV2DTO

data class IkkeAktuellBehandlingV2DTO(
    override val behandletAv: String,
    override val behandletTidspunkt: LocalDateTime,
    val ikkeAktuellArsak: IkkeAktuellArsakV2DTO,
) : BehandlingV2DTO

enum class SoknadStatusV2DTO { MOTTATT, INNVILGET, DELVIS_INNVILGET, AVSLAG, HENLAGT, IKKE_AKTUELL }

enum class IkkeAktuellArsakV2DTO { BEHANDLET_I_INFOTRYGD, DUPLIKAT, ANNET }

fun IkkeAktuellArsakV2DTO.toDomain(): IkkeAktuellArsak =
    when (this) {
        IkkeAktuellArsakV2DTO.BEHANDLET_I_INFOTRYGD -> IkkeAktuellArsak.BEHANDLET_I_INFOTRYGD
        IkkeAktuellArsakV2DTO.DUPLIKAT -> IkkeAktuellArsak.DUPLIKAT
        IkkeAktuellArsakV2DTO.ANNET -> IkkeAktuellArsak.ANNET
    }

private fun IkkeAktuellArsak.toV2DTO(): IkkeAktuellArsakV2DTO =
    when (this) {
        IkkeAktuellArsak.BEHANDLET_I_INFOTRYGD -> IkkeAktuellArsakV2DTO.BEHANDLET_I_INFOTRYGD
        IkkeAktuellArsak.DUPLIKAT -> IkkeAktuellArsakV2DTO.DUPLIKAT
        IkkeAktuellArsak.ANNET -> IkkeAktuellArsakV2DTO.ANNET
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

fun List<Soknad>.toResponseV2DTO(): SoknaderResponseV2DTO =
    SoknaderResponseV2DTO(
        soknader =
            map { soknad ->
                val infoTilBehandling =
                    if (soknad.status == SoknadStatus.MOTTATT) infoTilBehandlingAv(soknad) else null
                soknad.toV2DTO(infoTilBehandling)
            },
    )

fun Soknad.toResponseV2DTO(): SoknadResponseV2DTO = SoknadResponseV2DTO(soknad = toV2DTO())

private fun InfoTilBehandlingAvSoknad.toV2DTO(): InfoTilBehandlingV2DTO =
    InfoTilBehandlingV2DTO(opptellinger = opptellinger.map { it.toV2DTO() })

private fun Opptelling.toV2DTO(): OpptellingV2DTO =
    OpptellingV2DTO(
        fom = fom,
        tom = tom,
        maksAntallDager = MAKS_ANTALL_DAGER_UTENFOR_EOS,
        antallDagerBruktHvisInnvilget = antallDagerBruktHvisInnvilget,
        antallDagerPotensieltBruktHvisInnvilget = antallDagerPotensieltBruktHvisInnvilget,
        soktePerioderIVinduet = soktePerioderIVinduet.map { it.toV2DTO() },
        tidligereInnvilgedePerioder = tidligereInnvilgedePerioder.map { it.toV2DTO() },
        tidligereUbehandledePerioder = tidligereUbehandledePerioder.map { it.toV2DTO() },
    )

fun Soknad.toV2DTO(infoTilBehandling: InfoTilBehandlingAvSoknad? = null): SoknadV2DTO =
    SoknadV2DTO(
        soknadId = id.toString(),
        eksternId = eksternId,
        status = status.toV2DTO(),
        innsendtTidspunkt = innsendtTidspunkt.toLocalDateTimeOslo(),
        soktePerioder = soktePerioder.map { it.toV2DTO() },
        behandling = behandling?.toV2DTO(),
        infoTilBehandling = infoTilBehandling?.toV2DTO(),
    )

private fun Periode.toV2DTO(): PeriodeV2DTO = PeriodeV2DTO(fom = fom, tom = tom)

private fun Behandling.toV2DTO(): BehandlingV2DTO =
    when (val utfall = utfall) {
        is BehandlingsUtfall.Innvilget ->
            InnvilgetBehandlingV2DTO(
                behandletAv = behandletAv.value,
                behandletTidspunkt = behandletTidspunkt.toLocalDateTimeOslo(),
                innvilgedePerioder = utfall.innvilgedePerioder.map { it.toV2DTO() },
                begrunnelse = utfall.begrunnelse,
            )
        is BehandlingsUtfall.DelvisInnvilget ->
            DelvisInnvilgetBehandlingV2DTO(
                behandletAv = behandletAv.value,
                behandletTidspunkt = behandletTidspunkt.toLocalDateTimeOslo(),
                innvilgedePerioder = utfall.innvilgedePerioder.map { it.toV2DTO() },
                begrunnelse = utfall.begrunnelse,
            )
        is BehandlingsUtfall.Avslag ->
            AvslagBehandlingV2DTO(
                behandletAv = behandletAv.value,
                behandletTidspunkt = behandletTidspunkt.toLocalDateTimeOslo(),
                begrunnelse = utfall.begrunnelse,
            )
        is BehandlingsUtfall.Henlagt ->
            HenlagtBehandlingV2DTO(
                behandletAv = behandletAv.value,
                behandletTidspunkt = behandletTidspunkt.toLocalDateTimeOslo(),
                begrunnelse = utfall.begrunnelse,
            )
        is BehandlingsUtfall.IkkeAktuell ->
            IkkeAktuellBehandlingV2DTO(
                behandletAv = behandletAv.value,
                behandletTidspunkt = behandletTidspunkt.toLocalDateTimeOslo(),
                ikkeAktuellArsak = utfall.arsak.toV2DTO(),
            )
    }

fun PeriodeV2DTO.toDomain(): Periode = Periode(fom = fom, tom = tom)
