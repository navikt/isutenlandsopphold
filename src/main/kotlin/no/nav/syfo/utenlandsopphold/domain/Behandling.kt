package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.OffsetDateTime
import java.util.UUID

sealed interface Utfall {
    /**
     * Utfall som avgjør retten til sykepenger under utenlandsopphold. En henleggelse
     * avslutter saken uten å ta stilling til retten, og er derfor ikke et vedtak.
     */
    sealed interface Vedtak : Utfall {
        companion object {
            fun from(
                utfall: String,
                innvilgedePerioder: List<Periode>,
            ): Vedtak =
                when (utfall) {
                    "INNVILGET" -> Innvilget
                    "DELVIS_INNVILGET" -> DelvisInnvilget(innvilgedePerioder)
                    "AVSLAG" -> {
                        require(innvilgedePerioder.isEmpty()) { "innvilgedePerioder skal være tom ved avslag" }
                        Avslag
                    }
                    else -> throw IllegalArgumentException("Invalid utfall: $utfall")
                }
        }
    }

    data object Innvilget : Vedtak

    data class DelvisInnvilget(
        val innvilgedePerioder: List<Periode>,
    ) : Vedtak

    data object Avslag : Vedtak

    data object Henlagt : Utfall

    /**
     * Søknaden skal ikke realitetsbehandles her, og gir derfor ikke brev til bruker.
     */
    data class IkkeAktuell(
        val grunn: IkkeAktuellGrunn,
    ) : Utfall

    companion object {
        /**
         * Brukes av v1, der henleggelse er en utfallsverdi på vedtaksendepunktet. I v2
         * er henleggelse et eget endepunkt, og `/vedtak` bruker [Vedtak.from].
         *
         * IKKE_AKTUELL mangler bevisst: v1 kan vise utfallet, men ikke opprette det.
         */
        fun from(
            utfall: String,
            innvilgedePerioder: List<Periode>,
        ): Utfall =
            when (utfall) {
                "HENLAGT" -> {
                    require(innvilgedePerioder.isEmpty()) { "innvilgedePerioder skal være tom ved henleggelse" }
                    Henlagt
                }
                else -> Vedtak.from(utfall = utfall, innvilgedePerioder = innvilgedePerioder)
            }
    }
}

enum class IkkeAktuellGrunn {
    BEHANDLET_I_INFOTRYGD,
    DUPLIKAT,
    ANNET,
}

/**
 * Brevet et gitt utfall skal gi, eller null for utfall som ikke sender brev.
 */
fun Utfall.brevtype(): Brevtype? =
    when (this) {
        Utfall.Innvilget -> Brevtype.VEDTAK_INNVILGET
        is Utfall.DelvisInnvilget -> Brevtype.VEDTAK_DELVIS_INNVILGET
        Utfall.Avslag -> Brevtype.VEDTAK_AVSLAG
        Utfall.Henlagt -> Brevtype.HENLEGGELSE
        is Utfall.IkkeAktuell -> null
    }

/**
 * Et ferdig registrert resultat av å behandle en søknad — ikke en kladd eller en
 * arbeidsflyt med mellomtilstander.
 *
 * En behandling er ikke det samme som et vedtak: en henleggelse er også en behandling,
 * men ikke et vedtak om retten til sykepenger under utenlandsopphold.
 */
data class Behandling(
    val utfall: Utfall,
    val behandletAv: Navident,
    val behandletTidspunkt: OffsetDateTime,
    val innvilgedePerioder: List<Periode>,
    val brev: Brev?,
    val behandlingId: UUID = UUID.randomUUID(),
    val begrunnelse: String? = null,
) {
    init {
        when (utfall) {
            Utfall.Innvilget -> {
                require(innvilgedePerioder.isNotEmpty()) { "Innvilget behandling må ha innvilgede perioder" }
                require(begrunnelse == null) { "Innvilget behandling skal ikke ha begrunnelse" }
            }
            is Utfall.DelvisInnvilget -> {
                require(utfall.innvilgedePerioder.isNotEmpty()) {
                    "Delvis innvilget behandling må ha innvilgede perioder"
                }
                require(utfall.innvilgedePerioder == innvilgedePerioder) {
                    "Innvilgede perioder på utfall og behandling må være like"
                }
                require(!begrunnelse.isNullOrBlank()) { "Delvis innvilget behandling må ha begrunnelse" }
            }
            Utfall.Avslag -> {
                require(innvilgedePerioder.isEmpty()) { "Avslått behandling skal ikke ha innvilgede perioder" }
                require(!begrunnelse.isNullOrBlank()) { "Avslått behandling må ha begrunnelse" }
            }
            Utfall.Henlagt -> {
                require(innvilgedePerioder.isEmpty()) { "Henlagt behandling skal ikke ha innvilgede perioder" }
                require(!begrunnelse.isNullOrBlank()) { "Henlagt behandling må ha begrunnelse" }
            }
            is Utfall.IkkeAktuell -> {
                require(innvilgedePerioder.isEmpty()) {
                    "Behandling merket ikke aktuell skal ikke ha innvilgede perioder"
                }
                require(begrunnelse == null) { "Behandling merket ikke aktuell skal ikke ha begrunnelse" }
            }
        }

        // Dekker fire tilfeller på én gang: riktig brevtype, feil brevtype, utfall som
        // skal ha brev uten å ha det, og utfall uten brev som likevel har et.
        require(brev?.brevtype == utfall.brevtype()) {
            "Behandling $behandlingId med utfall $utfall må ha brev av type ${utfall.brevtype()}, men har ${brev?.brevtype}"
        }
    }

    fun journalforBrev(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Behandling {
        val gjeldendeBrev =
            checkNotNull(brev) { "Behandling $behandlingId har ikke brev og kan ikke journalføres" }

        return copy(brev = gjeldendeBrev.journalfor(journalpostId, tidspunkt))
    }

    fun distribuerBrev(tidspunkt: OffsetDateTime): Behandling {
        val gjeldendeBrev =
            checkNotNull(brev) { "Behandling $behandlingId har ikke brev og kan ikke distribueres" }

        return copy(brev = gjeldendeBrev.distribuer(tidspunkt))
    }
}
