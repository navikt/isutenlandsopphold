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
 * [antallDagerBruktHvisInnvilget] og [antallDagerPotensieltBruktHvisInnvilget] svarer på "hva blir
 * resultatet hvis periodene i denne søknaden innvilges", og inkluderer derfor søknadens egne søkte
 * dager i vinduet:
 *
 * - [antallDagerBruktHvisInnvilget] tar med tidligere innvilgede dager.
 * - [antallDagerPotensieltBruktHvisInnvilget] tar i tillegg med søkte dager fra andre ubehandlede
 *   søknader, altså worst case dersom også disse er innvilget eller innvilges. Dette tallet er relevant fordi søknader
 *   behandlet i Infotrygd ikke har vedtak i vår database.
 *
 * [soktePerioderIVinduet], [tidligereInnvilgedePerioder] og [tidligereUbehandledePerioder] fordeler dagene i vinduet
 * på tre innbyrdes disjunkte lister, alle klippet til vinduet. Ingen dag telles to ganger:
 *
 * - En dag som er søkt om i denne søknaden hører til [soktePerioderIVinduet].
 * - Ellers, en dag som er innvilget i en annen søknad hører til [tidligereInnvilgedePerioder].
 * - Ellers, en dag som er søkt om i en annen ubehandlet søknad hører til [tidligereUbehandledePerioder].
 *
 * Summen av dagene i de tre listene er derfor lik [antallDagerPotensieltBruktHvisInnvilget], mens
 * dagene i [tidligereInnvilgedePerioder] og [soktePerioderIVinduet] til sammen utgjør
 * [antallDagerBruktHvisInnvilget].
 *
 * Dagene telles som distinkte kalenderdager, slik at overlappende perioder kun teller én gang.
 * "Dager igjen" kan bli negativt, som betyr at grensen overskrides med tilsvarende antall dager.
 */
data class Opptelling(
    val fom: LocalDate,
    val tom: LocalDate,
    val antallDagerBruktHvisInnvilget: Int,
    val antallDagerPotensieltBruktHvisInnvilget: Int,
    val soktePerioderIVinduet: List<Periode>,
    val tidligereInnvilgedePerioder: List<Periode>,
    val tidligereUbehandledePerioder: List<Periode>,
) {
    val antallDagerIgjenHvisInnvilget: Int
        get() = MAKS_ANTALL_DAGER_UTENFOR_EOS - antallDagerBruktHvisInnvilget

    val antallDagerPotensieltIgjenHvisInnvilget: Int
        get() = MAKS_ANTALL_DAGER_UTENFOR_EOS - antallDagerPotensieltBruktHvisInnvilget
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
    val alleInnvilgedeDager =
        filter { it.status == SoknadStatus.INNVILGET || it.status == SoknadStatus.DELVIS_INNVILGET }
            .flatMap { it.vedtak?.innvilgedePerioder.orEmpty() }
            .dager()

    val alleSokteDagerIAndreUbehandlede =
        filter { it.status == SoknadStatus.MOTTATT && it.id != soknad.id }
            .flatMap { it.soktePerioder }
            .dager()

    val soktedagerDenneSoknaden = soknad.soktePerioder.dager()

    // Operator + mellom mengder (Set) gir unionen av mengdene. Altså kommer ikke samme dag med flere ganger.
    val alleInnvilgedeDagerHvisInnvilget = alleInnvilgedeDager + soktedagerDenneSoknaden
    val allePotensieltInnvilgedeDagerHvisInnvilget = alleInnvilgedeDagerHvisInnvilget + alleSokteDagerIAndreUbehandlede

    // En opptelling for hver søkte periode, der opptellingen dekker et vindu på ett år som slutter på siste dag i perioden.
    val opptellinger =
        soknad.soktePerioder
            .map { it.tom }
            .sorted()
            .map { tom ->
                val vindu = tom.minusYears(1).plusDays(1)..tom

                Opptelling(
                    fom = vindu.start,
                    tom = vindu.endInclusive,
                    antallDagerBruktHvisInnvilget = alleInnvilgedeDagerHvisInnvilget.count { it in vindu },
                    antallDagerPotensieltBruktHvisInnvilget =
                        allePotensieltInnvilgedeDagerHvisInnvilget.count { it in vindu },
                    soktePerioderIVinduet =
                        soktedagerDenneSoknaden.filterTo(mutableSetOf()) { it in vindu }.tilPerioder(),
                    tidligereInnvilgedePerioder =
                        alleInnvilgedeDager
                            .filterTo(mutableSetOf()) { it in vindu && it !in soktedagerDenneSoknaden }
                            .tilPerioder(),
                    tidligereUbehandledePerioder =
                        alleSokteDagerIAndreUbehandlede
                            .filterTo(mutableSetOf()) {
                                it in vindu && it !in alleInnvilgedeDager && it !in soktedagerDenneSoknaden
                            }.tilPerioder(),
                )
            }

    return InfoTilBehandlingAvSoknad(opptellinger = opptellinger)
}
