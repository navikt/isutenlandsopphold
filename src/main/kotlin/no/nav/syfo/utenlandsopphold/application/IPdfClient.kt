package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Brevtype
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import java.time.LocalDate

/**
 * Genererer PDF-en for et brev om utenlandsopphold via ispdfgen.
 */
interface IPdfClient {
    suspend fun createBrevPdf(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        brevtype: Brevtype,
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate = LocalDate.now(),
    ): ByteArray
}
