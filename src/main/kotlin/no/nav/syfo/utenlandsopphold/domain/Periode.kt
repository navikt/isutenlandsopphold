package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
) {
    init {
        require(!tom.isBefore(fom)) { "tom kan ikke vaere foer fom" }
    }

    fun overlapper(annen: Periode): Boolean = !fom.isAfter(annen.tom) && !annen.fom.isAfter(tom)

    // Sann hvis denne perioden dekker hele den andre (brukes til delvis innvilgelse).
    fun dekker(annen: Periode): Boolean = !fom.isAfter(annen.fom) && !tom.isBefore(annen.tom)

    // Snittet mellom to perioder, eller null hvis de ikke overlapper.
    fun snitt(annen: Periode): Periode? {
        val nyFom = maxOf(fom, annen.fom)
        val nyTom = minOf(tom, annen.tom)
        return if (nyFom.isAfter(nyTom)) null else Periode(nyFom, nyTom)
    }

    // Alle datoer i perioden, fom og tom inklusiv.
    fun dager(): List<LocalDate> =
        generateSequence(fom) { dato ->
            dato.plusDays(1).takeIf { !it.isAfter(tom) }
        }.toList()
}
