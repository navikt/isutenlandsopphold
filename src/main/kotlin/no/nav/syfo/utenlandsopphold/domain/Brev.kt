package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Hvilket dokument en behandling gir opphav til. Én verdi per mal i ispdfgen.
 */
enum class Dokumenttype {
    VEDTAK_INNVILGET,
    VEDTAK_DELVIS_INNVILGET,
    VEDTAK_AVSLAG,
    HENLEGGELSE,
}

/**
 * Dokumentet som sendes til bruker som følge av en [Behandling]. Eier innholdet
 * og tilstanden for journalføring og distribusjon.
 *
 * [dokumentId] brukes som `eksternReferanseId` mot dokarkiv, som dedupliserer på den.
 */
data class Dokument(
    val brevtype: Dokumenttype,
    val document: List<DocumentComponent>,
    val brevId: UUID = UUID.randomUUID(),
    val journalpostId: JournalpostId? = null,
    val journalfortTidspunkt: OffsetDateTime? = null,
    val distribuertTidspunkt: OffsetDateTime? = null,
) {
    init {
        require(document.isNotEmpty()) { "Dokument $brevId må ha innhold" }
        require((journalpostId == null) == (journalfortTidspunkt == null)) {
            "Dokument $brevId må ha både journalpostId og journalfortTidspunkt eller ingen av dem"
        }

    }

    val erJournalfort: Boolean
        get() = journalpostId != null

    val erDistribuert: Boolean
        get() = distribuertTidspunkt != null

    /**
     * Et dokument skal aldri journalføres mer enn én gang (idempotens) — kall denne kun etter
     * en vellykket arkivering i dokarkiv.
     */
    fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Dokument {
        check(!erJournalfort) {
            "Dokument $brevId er allerede journalført med journalpostId ${this.journalpostId}"
        }

        return copy(journalpostId = journalpostId, journalfortTidspunkt = tidspunkt)
    }

    /**
     * Et dokument kan kun distribueres etter at det er journalført, og skal aldri distribueres
     * mer enn én gang (idempotens) — kall denne kun etter en vellykket bestilling i
     * dokdistfordeling.
     */
    fun distribuer(tidspunkt: OffsetDateTime): Dokument {
        check(erJournalfort) {
            "Dokument $brevId må være journalført før det kan distribueres"
        }
        check(!erDistribuert) {
            "Dokument $brevId er allerede distribuert (distribuertTidspunkt=$distribuertTidspunkt)"
        }

        return copy(distribuertTidspunkt = tidspunkt)
    }
}

typealias Brev = Dokument
typealias Brevtype = Dokumenttype

val Dokument.dokumenttype: Dokumenttype
    get() = brevtype

val Dokument.innhold: List<DocumentComponent>
    get() = document

val Dokument.dokumentId: UUID
    get() = brevId
