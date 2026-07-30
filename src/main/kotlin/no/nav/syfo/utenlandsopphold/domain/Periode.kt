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

private fun List<Periode>.dager(): Set<LocalDate> =
    flatMap { periode ->
        generateSequence(periode.fom) { dato ->
            dato.plusDays(1).takeIf { !it.isAfter(periode.tom) }
        }.toList()
    }.toSet()
