package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
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

        return copy(vedtak = Vedtak(utfall, fattetAv, now, innvilgetePerioder = soktePerioder))
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at journalføring kun kan skje
     * på en søknad som faktisk har et vedtak. Selve idempotens-sjekken (kan ikke
     * journalføres to ganger) håndheves av Vedtak.journalfor().
     */
    fun journalforVedtak(
        journalpostId: JournalpostId,
        now: Instant,
    ): Soknad {
        val gjeldendeVedtak =
            checkNotNull(vedtak) {
                "Kan ikke journalføre en søknad som ikke har fått vedtak"
            }

        return copy(vedtak = gjeldendeVedtak.journalfor(journalpostId, now))
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at distribusjon kun kan skje
     * på en søknad som faktisk har et vedtak. Selve idempotens- og rekkefølge-sjekken
     * (må være journalført, kan ikke distribueres to ganger) håndheves av Vedtak.distribuer().
     */
    fun distribuerVedtak(now: Instant): Soknad {
        val gjeldendeVedtak =
            checkNotNull(vedtak) {
                "Kan ikke distribuere en søknad som ikke har fått vedtak"
            }

        return copy(vedtak = gjeldendeVedtak.distribuer(now))
    }
}
