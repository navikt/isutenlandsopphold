package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.Instant
import java.util.UUID

sealed interface Utfall {
    data object Innvilget : Utfall
}

data class Vedtak(
    val vedtakId: UUID = UUID.randomUUID(),
    val utfall: Utfall,
    val fattetAv: Navident,
    val fattetTidspunkt: Instant,
    val innvilgetePerioder: List<Periode>,
    val document: List<DocumentComponent> = emptyList(),
    val journalpostId: JournalpostId? = null,
    val journalfortTidspunkt: Instant? = null,
) {
    val erJournalfort: Boolean
        get() = journalpostId != null

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
            "Vedtak $vedtakId er allerede journalført med journalpostId $journalpostId"
        }

        return copy(journalpostId = journalpostId, journalfortTidspunkt = tidspunkt)
    }
}
