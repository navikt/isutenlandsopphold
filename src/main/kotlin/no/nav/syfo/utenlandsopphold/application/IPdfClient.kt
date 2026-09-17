package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Dokumenttype
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import java.time.LocalDate

/**
 * Genererer PDF-en for et dokument om utenlandsopphold via ispdfgen.
 */
interface IPdfClient {
    suspend fun createDokumentPdf(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        dokumenttype: Dokumenttype,
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate = LocalDate.now(),
    ): ByteArray
}
