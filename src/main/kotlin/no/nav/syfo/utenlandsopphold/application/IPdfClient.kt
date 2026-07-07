package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import java.time.LocalDate

/**
 * Genererer en PDF for et vedtak om utenlandsopphold via ispdfgen.
 */
interface IPdfClient {
    suspend fun createVedtakPdf(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate = LocalDate.now(),
    ): ByteArray
}
