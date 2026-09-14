package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Hvilket brev en behandling gir opphav til. Én verdi per mal i ispdfgen.
 */
enum class Brevtype {
    VEDTAK_INNVILGET,
    VEDTAK_DELVIS_INNVILGET,
    VEDTAK_AVSLAG,
    HENLEGGELSE,
}

/**
 * Brevet som sendes til bruker som følge av en [Behandling]. Eier dokumentinnholdet
 * og tilstanden for journalføring og distribusjon.
 *
 * [brevId] brukes som `eksternReferanseId` mot dokarkiv, som dedupliserer på den.
 */
data class Brev(
    val brevtype: Brevtype,
    val document: List<DocumentComponent>,
    val brevId: UUID = UUID.randomUUID(),
    val journalpostId: JournalpostId? = null,
    val journalfortTidspunkt: OffsetDateTime? = null,
    val distribuertTidspunkt: OffsetDateTime? = null,
) {
    init {
        require(document.isNotEmpty()) { "Brev $brevId må ha dokumentinnhold" }
        require((journalpostId == null) == (journalfortTidspunkt == null)) {
            "Brev $brevId må ha både journalpostId og journalfortTidspunkt eller ingen av dem"
        }
    }

    val erJournalfort: Boolean
        get() = journalpostId != null

    val erDistribuert: Boolean
        get() = distribuertTidspunkt != null

    /**
     * Rød sone: dette er en kjerne-invariant for journalføring. Et brev skal aldri
     * journalføres mer enn én gang (idempotens) — kall denne kun etter en vellykket
     * arkivering i dokarkiv, aldri på forhånd.
     */
    fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Brev {
        check(!erJournalfort) {
            "Brev $brevId er allerede journalført med journalpostId ${this.journalpostId}"
        }

        return copy(journalpostId = journalpostId, journalfortTidspunkt = tidspunkt)
    }

    /**
     * Rød sone: kjerne-invariant for distribusjon. Et brev kan kun distribueres etter at
     * det er journalført, og skal aldri distribueres mer enn én gang (idempotens) — kall
     * denne kun etter en vellykket bestilling i dokdistfordeling, aldri på forhånd.
     */
    fun distribuer(tidspunkt: OffsetDateTime): Brev {
        check(erJournalfort) {
            "Brev $brevId må være journalført før det kan distribueres"
        }
        check(!erDistribuert) {
            "Brev $brevId er allerede distribuert (distribuertTidspunkt=$distribuertTidspunkt)"
        }

        return copy(distribuertTidspunkt = tidspunkt)
    }
}
