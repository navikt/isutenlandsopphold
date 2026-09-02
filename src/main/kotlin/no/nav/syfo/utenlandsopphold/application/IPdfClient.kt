package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Behandlingsutfall
import java.time.LocalDate

/**
 * Genererer en PDF for et behandlingsutfall (vedtak eller henleggelse) om utenlandsopphold
 * via ispdfgen.
 */
interface IPdfClient {
    suspend fun createPdf(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        behandlingsutfall: Behandlingsutfall,
        datoSendt: LocalDate = LocalDate.now(),
    ): ByteArray
}
