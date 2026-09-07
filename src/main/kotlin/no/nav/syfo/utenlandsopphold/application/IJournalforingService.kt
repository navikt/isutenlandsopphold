package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.Brevkode
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident

/**
 * Journalfører (archives) a document to dokarkiv (Joark) for a citizen.
 *
 * @param brevkode Joark-brevkode for dokumentet. Sendes inn av kalleren (og ikke hardkodet i
 * implementasjonen) slik at samme tjeneste kan gjenbrukes for ulike dokumenttyper, f.eks.
 * vedtak og henleggelse, uten at disse blandes sammen.
 * @param tittel Tittel på journalposten. Skal ikke inneholde ordet "vedtak" for
 * henleggelser, siden en henleggelse ikke er et vedtak.
 */
interface IJournalforingService {
    suspend fun journalfor(
        personident: Personident,
        pdf: ByteArray,
        eksternReferanseId: String,
        brevkode: Brevkode,
        tittel: String,
    ): Result<JournalpostId>
}
