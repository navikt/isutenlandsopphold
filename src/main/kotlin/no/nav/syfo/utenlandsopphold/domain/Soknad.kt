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
    val id: UUID = UUID.randomUUID(),
    val eksternId: UUID,
    val personident: Personident,
    val soktePerioder: List<Periode>,
    val innsendtTidspunkt: Instant,
    val vedtak: Vedtak? = null,
) {
    val status: SoknadStatus
        get() =
            when (vedtak) {
                null -> SoknadStatus.MOTTATT
                else -> SoknadStatus.INNVILGET
            }

    init {
        if (soktePerioder.isEmpty()) {
            throw ManglerSoktePerioderException()
        }
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
