package no.nav.syfo.utenlandsopphold.domain.kvote

import java.time.LocalDate

const val KVOTE_VIRKEDAGER = 28

data class Restkvote(
    val kvote: Int,
    val brukt: Int,
    val periodeFra: LocalDate,
    val periodeTil: LocalDate,
) {
    val rest: Int = (kvote - brukt).coerceAtLeast(0)
}
