package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.OffsetDateTime
import java.util.UUID

sealed interface BehandlingsUtfall {
    sealed interface Vedtak : BehandlingsUtfall

    data class Innvilget(
        val innvilgedePerioder: List<Periode>,
    ) : Vedtak {
        init {
            require(innvilgedePerioder.isNotEmpty()) { "Innvilgelse må ha innvilgede perioder" }
        }
    }

    data class DelvisInnvilget(
        val innvilgedePerioder: List<Periode>,
        val begrunnelse: String,
    ) : Vedtak {
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
    ) : BehandlingsUtfall {
        init {
            require(begrunnelse.isNotBlank()) { "Henleggelse må ha begrunnelse" }
        }
    }

    /**
     * Søknaden skal ikke realitetsbehandles her, og gir derfor ikke brev til bruker.
     */
    data class IkkeAktuell(
        val grunn: IkkeAktuellGrunn,
    ) : BehandlingsUtfall
}

fun BehandlingsUtfall.innvilgedePerioder(): List<Periode> =
    when (this) {
        is BehandlingsUtfall.Innvilget -> innvilgedePerioder
        is BehandlingsUtfall.DelvisInnvilget -> innvilgedePerioder
        is BehandlingsUtfall.Avslag, is BehandlingsUtfall.Henlagt, is BehandlingsUtfall.IkkeAktuell -> emptyList()
    }

internal fun paakrevdBegrunnelse(
    begrunnelse: String?,
    utfall: String,
): String = requireNotNull(begrunnelse?.takeIf { it.isNotBlank() }) { "Begrunnelse er påkrevd ved $utfall" }

enum class IkkeAktuellGrunn {
    BEHANDLET_I_INFOTRYGD,
    DUPLIKAT,
    ANNET,
}

/**
 * Brevet et gitt utfall skal gi, eller null for utfall som ikke sender brev.
 */
fun BehandlingsUtfall.brevtype(): Brevtype? =
    when (this) {
        is BehandlingsUtfall.Innvilget -> Brevtype.VEDTAK_INNVILGET
        is BehandlingsUtfall.DelvisInnvilget -> Brevtype.VEDTAK_DELVIS_INNVILGET
        is BehandlingsUtfall.Avslag -> Brevtype.VEDTAK_AVSLAG
        is BehandlingsUtfall.Henlagt -> Brevtype.HENLEGGELSE
        is BehandlingsUtfall.IkkeAktuell -> null
    }

/**
 * Et ferdig registrert resultat av å behandle en søknad.
 *
 * En behandling er ikke det samme som et vedtak: en henleggelse er også en behandling.
 */
data class Behandling(
    val utfall: BehandlingsUtfall,
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
