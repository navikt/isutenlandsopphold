package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
) {
    init {
        require(!tom.isBefore(fom)) { "tom kan ikke være før fom" }
    }
}

internal fun List<Periode>.harOverlapp(): Boolean =
    sortedBy { it.fom }
        .zipWithNext()
        .any { (forrige, neste) -> !neste.fom.isAfter(forrige.tom) }

internal fun List<Periode>.alleDagerErInnenfor(perioder: List<Periode>): Boolean = dager().all { it in perioder.dager() }

internal fun List<Periode>.dager(): Set<LocalDate> =
    flatMap { periode ->
        generateSequence(periode.fom) { dato ->
            dato.plusDays(1).takeIf { !it.isAfter(periode.tom) }
        }.toList()
    }.toSet()

/**
 * Slår sammen datoene til sammenhengende perioder, sortert stigende. Datoer som følger rett etter
 * hverandre havner i samme periode, mens et hull på én eller flere dager gir en ny periode.
 */
internal fun Set<LocalDate>.tilPerioder(): List<Periode> =
    sorted().fold(mutableListOf()) { perioder: MutableList<Periode>, dato ->
        val forrige = perioder.lastOrNull()
        if (forrige != null && forrige.tom.plusDays(1) == dato) {
            perioder[perioder.lastIndex] = forrige.copy(tom = dato)
        } else {
            perioder.add(Periode(fom = dato, tom = dato))
        }
        perioder
    }
