package no.nav.syfo.utenlandsopphold.domain

/**
 * Informasjon som veileder trenger for å behandle en søknad, utover selve søknaden.
 *
 * Foreløpig består dette kun av [opptellinger] av dager utenfor EØS, én per søkt periode i
 * søknaden. Se [Opptelling] for hva hver opptelling inneholder.
 *
 * Kan utvides med mer informasjon som kan være relevant for behandlingen, for eksempel om de søkte periodene er
 * innenfor et oppfølgingstilfelle eller lignende.
 */
data class InfoTilBehandlingAvSoknad(
    val opptellinger: List<Opptelling>,
)

fun List<Soknad>.infoTilBehandlingAv(soknad: Soknad): InfoTilBehandlingAvSoknad =
    InfoTilBehandlingAvSoknad(opptellinger = opptellingerFor(soknad))
