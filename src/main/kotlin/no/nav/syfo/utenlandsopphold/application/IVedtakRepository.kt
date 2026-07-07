package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Soknad
import java.time.Instant
import java.util.UUID

/**
 * Persistens-port for vedtak. Skriving/opprettelse av vedtak er utenfor scope her —
 * denne porten dekker kun det som trengs for journalføringsjobben: å finne
 * søknader med u-journalførte vedtak, og å markere et vedtak som journalført.
 */
interface IVedtakRepository {
    fun getUjournalforteSoknader(): List<Soknad>

    fun setVedtakJournalfort(
        vedtakId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: Instant,
    )
}
