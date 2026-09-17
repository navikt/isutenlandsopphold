package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.OffsetDateTime
import java.util.UUID

sealed interface Utfall {
    sealed interface Vedtak : Utfall

    data object Innvilget : Vedtak

    data class DelvisInnvilget(
        val innvilgedePerioder: List<Periode>,
    ) : Vedtak

    data object Avslag : Vedtak

    data object Henlagt : Utfall

    companion object {
        fun from(
            utfall: String,
            innvilgedePerioder: List<Periode>,
        ): Utfall =
            when (utfall) {
                "INNVILGET" -> Innvilget
                "DELVIS_INNVILGET" -> DelvisInnvilget(innvilgedePerioder)
                "AVSLAG" -> {
                    require(innvilgedePerioder.isEmpty()) { "innvilgedePerioder skal være tom ved avslag" }
                    Avslag
                }
                "HENLAGT" -> {
                    require(innvilgedePerioder.isEmpty()) { "innvilgedePerioder skal være tom ved henleggelse" }
                    Henlagt
                }
                else -> throw IllegalArgumentException("Invalid utfall: $utfall")
            }
    }
}

/**
 * Dokumentet et gitt utfall skal gi. Alle dagens utfall sender dokument; når dokumentløse utfall
 * (som «ikke aktuell») innføres, blir returtypen nullable.
 */
fun Utfall.dokumenttype(): Dokumenttype =
    when (this) {
        Utfall.Innvilget -> Dokumenttype.VEDTAK_INNVILGET
        is Utfall.DelvisInnvilget -> Dokumenttype.VEDTAK_DELVIS_INNVILGET
        Utfall.Avslag -> Dokumenttype.VEDTAK_AVSLAG
        Utfall.Henlagt -> Dokumenttype.HENLEGGELSE
    }

fun Utfall.brevtype(): Dokumenttype = dokumenttype()

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
    val dokument: Dokument,
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
        }

        require(dokument.dokumenttype == utfall.dokumenttype()) {
            "Behandling $behandlingId med utfall $utfall må ha dokument av type ${utfall.dokumenttype()}, men har ${dokument.dokumenttype}"
        }
    }

    fun journalforDokument(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Behandling = copy(dokument = dokument.journalfor(journalpostId, tidspunkt))

    fun distribuerDokument(tidspunkt: OffsetDateTime): Behandling = copy(dokument = dokument.distribuer(tidspunkt))

    fun journalforBrev(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Behandling = journalforDokument(journalpostId, tidspunkt)

    fun distribuerBrev(tidspunkt: OffsetDateTime): Behandling = distribuerDokument(tidspunkt)
}
