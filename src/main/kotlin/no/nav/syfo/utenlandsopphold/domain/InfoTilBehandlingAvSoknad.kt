package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate

/**
 * En innbygger kan maksimalt få innvilget 28 dager med sykepenger under utenlandsopphold
 * utenfor EØS i løpet av et vindu på ett år.
 */
const val MAKS_ANTALL_DAGER_UTENFOR_EOS = 28

/**
 * Opptelling av dager utenfor EØS i et vindu på ett år som slutter på [tom].
 *
 * Begge tallene svarer på "hva blir resultatet hvis periodene i denne søknaden innvilges", og
 * inkluderer derfor søknadens egne søkte dager i vinduet:
 *
 * - [antallDagerHvisInnvilget] tar med tidligere innvilgede dager.
 * - [antallDagerHvisInnvilgetInklUbehandlede] tar i tillegg med søkte dager fra andre ubehandlede
 *   søknader, altså worst case dersom også disse innvilges. Dette tallet er relevant fordi
 *   søknader behandlet i tidligere saksbehandlingsløsninger ikke har vedtak i vår database.
 *
 * Dagene telles som distinkte kalenderdager, slik at overlappende perioder kun teller én gang.
 * "Dager igjen" kan bli negativt, som betyr at grensen overskrides med tilsvarende antall dager.
 */
data class Opptelling(
    val fom: LocalDate,
    val tom: LocalDate,
    val antallDagerHvisInnvilget: Int,
    val antallDagerHvisInnvilgetInklUbehandlede: Int,
) {
    val antallDagerIgjenHvisInnvilget: Int
        get() = MAKS_ANTALL_DAGER_UTENFOR_EOS - antallDagerHvisInnvilget

    val antallDagerIgjenHvisInnvilgetInklUbehandlede: Int
        get() = MAKS_ANTALL_DAGER_UTENFOR_EOS - antallDagerHvisInnvilgetInklUbehandlede
}

data class InfoTilBehandlingAvSoknad(
    val opptellinger: List<Opptelling>,
)

/**
 * Lager én opptelling per søkt periode i [soknad], der hver opptelling dekker et vindu på nøyaktig
 * ett år som slutter på siste dag i den søkte perioden.
 *
 * Mottakerlisten er alle søknadene til innbyggeren, og brukes som datagrunnlag for opptellingene.
 * Perioder som strekker seg forbi vinduet, samt senere perioder i samme søknad, faller naturlig
 * utenfor fordi vinduet slutter på periodens siste dag.
 */
fun List<Soknad>.infoTilBehandlingAv(soknad: Soknad): InfoTilBehandlingAvSoknad {
    val innvilgedeDager =
        filter { it.status == SoknadStatus.INNVILGET || it.status == SoknadStatus.DELVIS_INNVILGET }
            .flatMap { it.vedtak?.innvilgedePerioder.orEmpty() }
            .dager()

    val soktedagerAndreUbehandlede =
        filter { it.status == SoknadStatus.MOTTATT && it.id != soknad.id }
            .flatMap { it.soktePerioder }
            .dager()

    val soktedagerDenneSoknaden = soknad.soktePerioder.dager()

    val dagerHvisInnvilget = innvilgedeDager + soktedagerDenneSoknaden
    val dagerHvisInnvilgetInklUbehandlede = dagerHvisInnvilget + soktedagerAndreUbehandlede

    val opptellinger =
        soknad.soktePerioder
            .map { it.tom }
            .distinct()
            .sorted()
            .map { tom ->
                val vindu = tom.minusYears(1).plusDays(1)..tom

                Opptelling(
                    fom = vindu.start,
                    tom = vindu.endInclusive,
                    antallDagerHvisInnvilget = dagerHvisInnvilget.count { it in vindu },
                    antallDagerHvisInnvilgetInklUbehandlede =
                        dagerHvisInnvilgetInklUbehandlede.count { it in vindu },
                )
            }

    return InfoTilBehandlingAvSoknad(opptellinger = opptellinger)
}
