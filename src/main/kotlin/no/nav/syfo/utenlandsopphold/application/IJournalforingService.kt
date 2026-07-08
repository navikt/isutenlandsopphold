package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident

/**
 * Journalfører (archives) a document to dokarkiv (Joark) for a citizen.
 */
interface IJournalforingService {
    suspend fun journalfor(
        personident: Personident,
        pdf: ByteArray,
        eksternReferanseId: String,
    ): Result<JournalpostId>
}
