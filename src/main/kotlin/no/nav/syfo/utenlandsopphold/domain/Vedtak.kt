package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.OffsetDateTime
import java.util.UUID

sealed interface Utfall {
    data object Innvilget : Utfall

    data class DelvisInnvilget(
        val innvilgedePerioder: List<Periode>,
    ) : Utfall

    data object Avslag : Utfall

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

data class Vedtak(
    val utfall: Utfall,
    val fattetAv: Navident,
    val fattetTidspunkt: OffsetDateTime,
    val innvilgedePerioder: List<Periode>,
    val vedtakId: UUID = UUID.randomUUID(),
    val document: List<DocumentComponent>,
    val begrunnelse: String? = null,
    val journalpostId: JournalpostId? = null,
    val journalfortTidspunkt: OffsetDateTime? = null,
    val distribuertTidspunkt: OffsetDateTime? = null,
) {
    init {
        when (utfall) {
            Utfall.Innvilget -> {
                require(innvilgedePerioder.isNotEmpty()) { "Innvilget vedtak må ha innvilgede perioder" }
                require(begrunnelse == null) { "Innvilget vedtak skal ikke ha begrunnelse" }
            }
            is Utfall.DelvisInnvilget -> {
                require(utfall.innvilgedePerioder.isNotEmpty()) { "Delvis innvilget vedtak må ha innvilgede perioder" }
                require(utfall.innvilgedePerioder == innvilgedePerioder) {
                    "Innvilgede perioder på utfall og vedtak må være like"
                }
                require(!begrunnelse.isNullOrBlank()) { "Delvis innvilget vedtak må ha begrunnelse" }
            }
            Utfall.Avslag -> {
                require(innvilgedePerioder.isEmpty()) { "Avslått vedtak skal ikke ha innvilgede perioder" }
                require(!begrunnelse.isNullOrBlank()) { "Avslått vedtak må ha begrunnelse" }
            }
            Utfall.Henlagt -> {
                require(innvilgedePerioder.isEmpty()) { "Henlagt vedtak skal ikke ha innvilgede perioder" }
                require(!begrunnelse.isNullOrBlank()) { "Henlagt vedtak må ha begrunnelse" }
            }
        }
    }

    val erJournalfort: Boolean
        get() = journalpostId != null

    val erDistribuert: Boolean
        get() = distribuertTidspunkt != null

    /**
     * Rød sone: dette er en kjerne-invariant for journalføring. Et vedtak skal aldri
     * journalføres mer enn én gang (idempotens) — kall denne kun etter en vellykket
     * arkivering i dokarkiv, aldri på forhånd.
     */
    fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Vedtak {
        check(!erJournalfort) {
            "Vedtak $vedtakId er allerede journalført med journalpostId ${this.journalpostId}"
        }

        return copy(journalpostId = journalpostId, journalfortTidspunkt = tidspunkt)
    }

    /**
     * Rød sone: kjerne-invariant for distribusjon. Et vedtak kan kun distribueres etter at
     * det er journalført, og skal aldri distribueres mer enn én gang (idempotens) — kall
     * denne kun etter en vellykket bestilling i dokdistfordeling, aldri på forhånd.
     */
    fun distribuer(tidspunkt: OffsetDateTime): Vedtak {
        check(erJournalfort) {
            "Vedtak $vedtakId må være journalført før det kan distribueres"
        }
        check(!erDistribuert) {
            "Vedtak $vedtakId er allerede distribuert (distribuertTidspunkt=$distribuertTidspunkt)"
        }

        return copy(distribuertTidspunkt = tidspunkt)
    }
}
