package no.nav.syfo.utenlandsopphold.domain.kvote

import no.nav.syfo.utenlandsopphold.domain.Periode
import java.time.DayOfWeek

class Virkedager(
    private val helligdager: HelligdagKalender,
) {
    // Teller virkedager (mandag-fredag, eksklusiv helligdager) i en periode, fom og tom inklusiv.
    fun tell(periode: Periode): Int =
        generateSequence(periode.fom) { dato ->
            dato.plusDays(1).takeIf { !it.isAfter(periode.tom) }
        }.count { dato ->
            dato.dayOfWeek != DayOfWeek.SATURDAY &&
                dato.dayOfWeek != DayOfWeek.SUNDAY &&
                !helligdager.erHelligdag(dato)
        }
}
