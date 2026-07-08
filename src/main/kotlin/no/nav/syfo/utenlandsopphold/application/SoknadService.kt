package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import java.time.Instant
import java.util.UUID

class SoknadService(
    private val soknadRepository: ISoknadRepository,
) {
    fun hentSoknad(soknadId: UUID): Soknad? = soknadRepository.hentSoknad(soknadId)

    fun hentSoknader(personident: Personident): List<Soknad> = soknadRepository.hentSoknader(personident)

    fun mottaSoknad(soknad: Soknad): LagreMottattSoknadResultat = soknadRepository.lagreMottattSoknad(soknad)

    fun fattVedtak(
        soknad: Soknad,
        fattetAv: Navident,
        utfall: Utfall.Innvilget,
        document: List<DocumentComponent>,
    ): Soknad {
        val soknadMedVedtak =
            soknad.fattVedtak(
                utfall = utfall,
                fattetAv = fattetAv,
                now = Instant.now(),
                document = document,
            )

        return soknadRepository.lagreVedtak(soknadMedVedtak = soknadMedVedtak)
    }
}
