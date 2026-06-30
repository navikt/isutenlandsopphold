package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import java.time.Instant
import java.util.UUID

enum class SoknadStatus {
    MOTTATT,
    INNVILGET,
}

data class Soknad(
    val id: String,
    val eksternId: UUID, // sjekke type
    val personident: Personident,
    val soktePerioder: List<Periode>,
    val mottattTidspunkt: Instant,
    val status: SoknadStatus = SoknadStatus.MOTTATT,
    val vedtak: Vedtak? = null,
) {
    init {
        require(soktePerioder.isNotEmpty()) { "Søknad må ha minst en søkt periode" }
    }

    fun fattVedtak(
        utfall: Utfall,
        av: Navident,
        naa: Instant,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Vedtak kan kun fattes paa en MOTTATT soknad, men status er $status"
        }

        val nyStatus =
            when (utfall) {
                is Utfall.FullInnvilgelse -> {
                    SoknadStatus.INNVILGET
                }
            }

        return copy(
            status = nyStatus,
            vedtak = Vedtak(utfall, av, naa),
        )
    }
}
