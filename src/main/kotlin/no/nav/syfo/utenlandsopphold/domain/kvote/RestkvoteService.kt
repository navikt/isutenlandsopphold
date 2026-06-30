package no.nav.syfo.utenlandsopphold.domain.kvote

import no.nav.syfo.utenlandsopphold.domain.Periode
import java.time.LocalDate

class RestkvoteService(
    private val virkedager: Virkedager,
) {
    // Beregner restkvote for et rullerende 365-dagers vindu som ender paa paaDato.
    // Kun innvilgede dager innenfor vinduet teller mot kvoten.
    fun beregn(
        paaDato: LocalDate,
        innvilgedePerioder: List<Periode>,
    ): Restkvote {
        val vindu = Periode(fom = paaDato.minusDays(VINDU_DAGER - 1), tom = paaDato)
        val brukt =
            innvilgedePerioder
                .mapNotNull { it.snitt(vindu) }
                .sumOf { virkedager.tell(it) }
        return Restkvote(
            kvote = KVOTE_VIRKEDAGER,
            brukt = brukt,
            periodeFra = vindu.fom,
            periodeTil = vindu.tom,
        )
    }

    companion object {
        private const val VINDU_DAGER = 365L
    }
}
