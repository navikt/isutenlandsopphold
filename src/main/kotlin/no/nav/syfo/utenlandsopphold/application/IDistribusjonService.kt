package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId

/**
 * Bestiller distribusjon (utsending) av en allerede journalført journalpost til mottaker,
 * via dokdistfordeling.
 */
interface IDistribusjonService {
    suspend fun distribuer(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
    ): Result<String>
}
