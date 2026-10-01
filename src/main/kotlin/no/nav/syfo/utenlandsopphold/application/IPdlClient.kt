package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident

/**
 * Henter personinformasjon fra PDL: navn for bruk i journalførte dokumenter,
 * og folkeregisteridenter for å verifisere identhendelser.
 */
interface IPdlClient {
    suspend fun getNavn(personident: Personident): String

    /** Henter folkeregisteridenter (inkludert historiske) for personen, eller null hvis personen ikke finnes. */
    suspend fun hentIdenter(personident: Personident): List<PdlIdent>?
}

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
)
