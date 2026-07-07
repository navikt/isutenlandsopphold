package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad

interface ISoknadRepository {
    fun hentSoknader(personident: Personident): List<Soknad>

    fun upsertSoknad(soknad: Soknad): Soknad
}
