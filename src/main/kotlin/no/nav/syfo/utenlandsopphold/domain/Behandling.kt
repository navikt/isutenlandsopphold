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
    sealed interface Vedtak : Utfall

    /**
     * Utfall som innvilger perioder. De øvrige utfallene innvilger ingenting, og har
     * derfor ingen perioder å bære.
     */
    sealed interface Innvilgelse : Vedtak {
        val innvilgedePerioder: List<Periode>
    }

    data class Innvilget(
        override val innvilgedePerioder: List<Periode>,
    ) : Innvilgelse {
        init {
            require(innvilgedePerioder.isNotEmpty()) { "Innvilgelse må ha innvilgede perioder" }
        }
    }

    data class DelvisInnvilget(
        override val innvilgedePerioder: List<Periode>,
        val begrunnelse: String,
    ) : Innvilgelse {
        init {
            require(innvilgedePerioder.isNotEmpty()) { "Delvis innvilgelse må ha innvilgede perioder" }
            require(begrunnelse.isNotBlank()) { "Delvis innvilgelse må ha begrunnelse" }
        }
    }

    data class Avslag(
        val begrunnelse: String,
    ) : Vedtak {
        init {
            require(begrunnelse.isNotBlank()) { "Avslag må ha begrunnelse" }
        }
    }

    data class Henlagt(
        val begrunnelse: String,
    ) : Utfall {
        init {
            require(begrunnelse.isNotBlank()) { "Henleggelse må ha begrunnelse" }
        }
    }

    /**
     * Søknaden skal ikke realitetsbehandles her, og gir derfor ikke brev til bruker.
     */
    data class IkkeAktuell(
        val grunn: IkkeAktuellGrunn,
    ) : Utfall
}

/**
 * Perioder saksbehandleren har tatt stilling til. Ligger utenfor [Utfall] fordi bare
 * [Utfall.Innvilgelse] har dem, og fordi de øvrige utfallene ikke skal fristes til å
 * svare på spørsmålet.
 */
fun Utfall.innvilgedePerioder(): List<Periode> =
    when (this) {
        is Utfall.Innvilgelse -> innvilgedePerioder
        is Utfall.Avslag, is Utfall.Henlagt, is Utfall.IkkeAktuell -> emptyList()
    }

/**
 * Begrunnelsen kommer inn som nullbar fra API-et, så påkrevdheten må sjekkes der
 * inputen treffer domenet.
 */
internal fun paakrevdBegrunnelse(
    begrunnelse: String?,
    utfall: String,
): String =
    requireNotNull(begrunnelse?.takeIf { it.isNotBlank() }) { "Begrunnelse er påkrevd ved $utfall" }

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
        is Utfall.Innvilget -> Brevtype.VEDTAK_INNVILGET
        is Utfall.DelvisInnvilget -> Brevtype.VEDTAK_DELVIS_INNVILGET
        is Utfall.Avslag -> Brevtype.VEDTAK_AVSLAG
        is Utfall.Henlagt -> Brevtype.HENLEGGELSE
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
    val brev: Brev?,
    val behandlingId: UUID = UUID.randomUUID(),
) {
    init {
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
