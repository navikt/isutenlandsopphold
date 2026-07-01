package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.types.ident.Navident
import java.time.Instant

sealed interface Utfall {
    data object FullInnvilgelse : Utfall
}

data class Vedtak(
    val utfall: Utfall,
    val fattetAv: Navident,
    val fattetTidspunkt: Instant,
)
