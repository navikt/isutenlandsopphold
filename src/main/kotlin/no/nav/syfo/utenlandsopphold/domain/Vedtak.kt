package no.nav.syfo.utenlandsopphold.domain

import java.time.Instant

// Veileders valg ved vedtak. Full vs. delvis vs. avslag er et eksplisitt valg,
// ikke noe som utledes ved sammenligning av perioder.
sealed interface Utfall {
    data object FullInnvilgelse : Utfall

//    // under arbeid
//    data class DelvisInnvilgelse(
//        val innvilgetePerioder: List<Periode>,
//    ) : Utfall {
//        init {
//            require(innvilgetePerioder.isNotEmpty()) {
//                "Delvis innvilgelse maa ha minst en periode"
//            }
//        }
//    }

//    data object Avslag : Utfall
}

data class Vedtak(
    val utfall: Utfall,
//    val begrunnelse: String?,
    val fattetAv: NavIdent,
    val fattetTidspunkt: Instant,
)
