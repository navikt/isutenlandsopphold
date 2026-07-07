package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad

class SoknadService(
    private val soknadRepository: ISoknadRepository,
) {
    fun hentSoknader(personident: Personident): List<Soknad> = soknadRepository.hentSoknader(personident)

    fun mottaSoknad(soknad: Soknad) {
        // Process soknader:
        // Check if soknad is already processed, if not, process it and store it in the database

        soknadRepository.lagreMottattSoknad(soknad)
    }
}
