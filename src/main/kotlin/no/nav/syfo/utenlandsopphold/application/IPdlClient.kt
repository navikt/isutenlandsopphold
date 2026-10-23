package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident

/**
 * Henter personinformasjon fra PDL: navn for bruk i journalførte dokumenter, og gjeldende
 * folkeregisterident for opprydding av søknader lagret på utdatert ident.
 */
interface IPdlClient {
    suspend fun getNavn(personident: Personident): String

    /**
     * Returnerer gjeldende (ikke-historisk) folkeregisterident for [personident], eller null hvis
     * PDL ikke returnerer noen.
     */
    suspend fun getAktivIdent(personident: Personident): Personident?
}
