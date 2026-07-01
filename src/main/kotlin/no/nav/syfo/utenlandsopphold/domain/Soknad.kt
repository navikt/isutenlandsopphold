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
    val id: UUID,
    val personident: Personident,
    val soktePerioder: List<Periode>,
    val mottattTidspunkt: Instant,
    val vedtak: Vedtak? = null,
) {
    val status: SoknadStatus
        get() =
            when (vedtak) {
                null -> SoknadStatus.MOTTATT
                else -> SoknadStatus.INNVILGET
            }

    init {
        require(soktePerioder.isNotEmpty()) { "Søknad må ha minst en søkt periode" }
    }

    fun fattVedtak(
        utfall: Utfall,
        fattetAv: Navident,
        now: Instant,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Vedtak kan kun fattes på en MOTTATT soknad, men status er $status"
        }

        return copy(vedtak = Vedtak(utfall, fattetAv, now))
    }
}
