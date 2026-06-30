package no.nav.syfo.utenlandsopphold.domain.kvote

import java.time.LocalDate

// Abstraksjon over helligdager. Konkret implementasjon (bevegelige helligdager,
// jul/paaske osv.) hoerer hjemme i infrastrukturlaget, ikke i domenet.
fun interface HelligdagKalender {
    fun erHelligdag(dato: LocalDate): Boolean
}
