package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.OffsetDateTime

/**
 * Utsending er livssyklusen for journalføring og distribusjon av dokumentet til et
 * [Behandlingsutfall] (enten [Vedtak] eller [Henleggelse]). Begge undertyper genererer et
 * dokument som skal journalføres og distribueres på samme måte, og deler derfor denne
 * value object-en og dens invarianter.
 */
data class Utsending(
    val document: List<DocumentComponent>,
    val journalpostId: JournalpostId? = null,
    val journalfortTidspunkt: OffsetDateTime? = null,
    val distribuertTidspunkt: OffsetDateTime? = null,
) {
    val erJournalfort: Boolean
        get() = journalpostId != null

    val erDistribuert: Boolean
        get() = distribuertTidspunkt != null

    /**
     * Rød sone: dette er en kjerne-invariant for journalføring. Et behandlingsutfall skal
     * aldri journalføres mer enn én gang (idempotens) — kall denne kun etter en vellykket
     * arkivering i dokarkiv, aldri på forhånd.
     */
    fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Utsending {
        check(!erJournalfort) {
            "Kan ikke journalføre: allerede journalført med journalpostId ${this.journalpostId}"
        }

        return copy(journalpostId = journalpostId, journalfortTidspunkt = tidspunkt)
    }

    /**
     * Rød sone: kjerne-invariant for distribusjon. Et behandlingsutfall kan kun distribueres
     * etter at det er journalført, og skal aldri distribueres mer enn én gang (idempotens) —
     * kall denne kun etter en vellykket bestilling i dokdistfordeling, aldri på forhånd.
     */
    fun distribuer(tidspunkt: OffsetDateTime): Utsending {
        check(erJournalfort) {
            "Må være journalført før det kan distribueres"
        }
        check(!erDistribuert) {
            "Allerede distribuert (distribuertTidspunkt=$distribuertTidspunkt)"
        }

        return copy(distribuertTidspunkt = tidspunkt)
    }
}
