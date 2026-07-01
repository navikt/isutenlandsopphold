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
