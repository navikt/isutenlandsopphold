package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

enum class SoknadStatus {
    MOTTATT,
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLATT,
}

data class Soknad(
    val id: UUID = UUID.randomUUID(),
    val eksternId: UUID,
    val personident: Personident,
    val soktePerioder: List<Periode>,
    val innsendtTidspunkt: OffsetDateTime,
    val vedtak: Vedtak? = null,
) {
    val status: SoknadStatus
        get() =
            when (vedtak) {
                null -> SoknadStatus.MOTTATT
                else ->
                    when (vedtak.utfall) {
                        Utfall.Innvilget -> SoknadStatus.INNVILGET
                        is Utfall.DelvisInnvilget -> SoknadStatus.DELVIS_INNVILGET
                        Utfall.Avslag -> SoknadStatus.AVSLATT
                    }
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
        document: List<DocumentComponent>,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Vedtak kan kun fattes på en MOTTATT soknad, men status er $status"
        }

        val innvilgetePerioder =
            when (utfall) {
                Utfall.Innvilget -> soktePerioder
                is Utfall.DelvisInnvilget -> {
                    require(utfall.innvilgetePerioder.isNotEmpty()) {
                        "Delvis innvilgelse må ha minst én innvilget periode"
                    }
                    require(!utfall.innvilgetePerioder.harOverlapp()) {
                        "Innvilgede perioder ved delvis innvilgelse kan ikke overlappe"
                    }
                    require(
                        utfall.innvilgetePerioder.alleDagerErInnenfor(soktePerioder),
                    ) {
                        "Innvilgede perioder ved delvis innvilgelse må være innenfor søkte perioder"
                    }
                    utfall.innvilgetePerioder
                }
                Utfall.Avslag -> emptyList()
            }

        return copy(
            vedtak =
                Vedtak(
                    utfall = utfall,
                    fattetAv = fattetAv,
                    fattetTidspunkt = now,
                    innvilgetePerioder = innvilgetePerioder,
                    document = document,
                ),
        )
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

private fun List<Periode>.harOverlapp(): Boolean =
    sortedBy { it.fom }
        .zipWithNext()
        .any { (forrige, neste) -> !neste.fom.isAfter(forrige.tom) }

private fun List<Periode>.alleDagerErInnenfor(perioder: List<Periode>): Boolean = dager().all { it in perioder.dager() }

private fun List<Periode>.dager(): Set<LocalDate> =
    flatMap { periode ->
        generateSequence(periode.fom) { dato ->
            dato.plusDays(1).takeIf { !it.isAfter(periode.tom) }
        }.toList()
    }.toSet()
