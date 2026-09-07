package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.OffsetDateTime
import java.util.UUID

/**
 * En henleggelse av en søknad om utenlandsopphold. En henleggelse er IKKE et vedtak —
 * søknaden avsluttes uten at det er tatt stilling til om søknaden er innvilget/avslått.
 * (se [no.nav.syfo.utenlandsopphold.infrastructure.journalforing.UtenlandsoppholdBrevkode.HENLEGGELSE]).
 */
data class Henleggelse(
    val begrunnelse: String,
    val henlagtAv: Navident,
    val henlagtTidspunkt: OffsetDateTime,
    val document: List<DocumentComponent>,
    val henleggelseId: UUID = UUID.randomUUID(),
    val journalpostId: JournalpostId? = null,
    val journalfortTidspunkt: OffsetDateTime? = null,
    val distribuertTidspunkt: OffsetDateTime? = null,
) {
    init {
        require(begrunnelse.isNotBlank()) { "Henleggelse må ha begrunnelse" }
    }

    val erJournalfort: Boolean
        get() = journalpostId != null

    val erDistribuert: Boolean
        get() = distribuertTidspunkt != null

    /**
     * Rød sone: dette er en kjerne-invariant for journalføring. En henleggelse skal aldri
     * journalføres mer enn én gang (idempotens)
     */
    fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Henleggelse {
        check(!erJournalfort) {
            "Henleggelse $henleggelseId er allerede journalført med journalpostId ${this.journalpostId}"
        }

        return copy(journalpostId = journalpostId, journalfortTidspunkt = tidspunkt)
    }

    /**
     * Rød sone: kjerne-invariant for distribusjon. En henleggelse kan kun distribueres etter
     * at den er journalført, og skal aldri distribueres mer enn én gang (idempotens).
     */
    fun distribuer(tidspunkt: OffsetDateTime): Henleggelse {
        check(erJournalfort) {
            "Henleggelse $henleggelseId må være journalført før den kan distribueres"
        }
        check(!erDistribuert) {
            "Henleggelse $henleggelseId er allerede distribuert (distribuertTidspunkt=$distribuertTidspunkt)"
        }

        return copy(distribuertTidspunkt = tidspunkt)
    }
}
