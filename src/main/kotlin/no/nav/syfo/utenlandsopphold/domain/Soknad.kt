package no.nav.syfo.utenlandsopphold.domain

import java.time.Instant
import java.util.UUID

enum class SoknadStatus {
    MOTTATT,
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLAATT,
    HENLAGT,
}

data class Henleggelse(
    val av: NavIdent,
    val aarsak: String,
    val tidspunkt: Instant,
)

data class Soknad(
    val id: String, // type?
    val eksternId: UUID, // type?
    val personident: Personident,
    val soktePerioder: List<Periode>,
    val mottattTidspunkt: Instant,
    val status: SoknadStatus = SoknadStatus.MOTTATT,
    val vedtak: Vedtak? = null,
    val henleggelse: Henleggelse? = null,
) {
    init {
        require(soktePerioder.isNotEmpty()) { "Soknad maa ha minst en omsoekt periode" }
    }

    fun fattVedtak(
        utfall: Utfall,
        av: NavIdent,
        begrunnelse: String,
        naa: Instant,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Vedtak kan kun fattes paa en MOTTATT soknad, men status er $status"
        }
        val nyStatus =
            when (utfall) {
                is Utfall.Avslaatt -> SoknadStatus.AVSLAATT
                is Utfall.Innvilget -> {
                    require(utfall.innvilgetePerioder.all { innvilget -> soktePerioder.any { it.dekker(innvilget) } }) {
                        "Innvilget periode maa ligge innenfor en omsoekt periode"
                    }
                    if (utfall.innvilgetePerioder == soktePerioder) {
                        SoknadStatus.INNVILGET
                    } else {
                        SoknadStatus.DELVIS_INNVILGET
                    }
                }
            }
        return copy(
            status = nyStatus,
            vedtak = Vedtak(utfall, begrunnelse, av, naa),
        )
    }

    fun henlegg(
        av: NavIdent,
        aarsak: String,
        naa: Instant,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Kun en MOTTATT soknad kan henlegges, men status er $status"
        }
        return copy(
            status = SoknadStatus.HENLAGT,
            henleggelse = Henleggelse(av, aarsak, naa),
        )
    }
}
