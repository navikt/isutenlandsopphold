package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident

/**
 * Henter personinformasjon (navn) fra PDL for bruk i journalførte dokumenter.
 */
interface IPersonInfoClient {
    suspend fun getNavn(personident: Personident): String
}
