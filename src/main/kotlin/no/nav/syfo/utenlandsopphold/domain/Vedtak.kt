package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.Instant
import java.util.UUID

sealed interface Utfall {
    data object Innvilget : Utfall

    data class DelvisInnvilget(
        val innvilgetePerioder: List<Periode>,
    ) : Utfall

    data object Avslag : Utfall
}

data class Vedtak(
    val utfall: Utfall,
    val fattetAv: Navident,
    val fattetTidspunkt: Instant,
    val innvilgetePerioder: List<Periode>,
    val vedtakId: UUID = UUID.randomUUID(),
    val document: List<DocumentComponent>,
    val journalpostId: JournalpostId? = null,
    val journalfortTidspunkt: Instant? = null,
    val distribuertTidspunkt: Instant? = null,
) {
    init {
        when (utfall) {
            Utfall.Innvilget -> require(innvilgetePerioder.isNotEmpty()) { "Innvilget vedtak må ha innvilgede perioder" }
            is Utfall.DelvisInnvilget -> {
                require(utfall.innvilgetePerioder.isNotEmpty()) { "Delvis innvilget vedtak må ha innvilgede perioder" }
                require(utfall.innvilgetePerioder == innvilgetePerioder) {
                    "Innvilgede perioder på utfall og vedtak må være like"
                }
            }
            Utfall.Avslag -> require(innvilgetePerioder.isEmpty()) { "Avslått vedtak skal ikke ha innvilgede perioder" }
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
        tidspunkt: Instant,
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
    fun distribuer(tidspunkt: Instant): Vedtak {
        check(erJournalfort) {
            "Vedtak $vedtakId må være journalført før det kan distribueres"
        }
        check(!erDistribuert) {
            "Vedtak $vedtakId er allerede distribuert (distribuertTidspunkt=$distribuertTidspunkt)"
        }

        return copy(distribuertTidspunkt = tidspunkt)
    }
}
