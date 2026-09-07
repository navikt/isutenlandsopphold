package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import java.time.OffsetDateTime
import java.util.UUID

enum class SoknadStatus {
    MOTTATT,
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLAG,
    HENLAGT,
}

data class Soknad(
    val id: UUID = UUID.randomUUID(),
    val eksternId: UUID,
    val personident: Personident,
    val soktePerioder: List<Periode>,
    val innsendtTidspunkt: OffsetDateTime,
    val vedtak: Vedtak? = null,
    val henleggelse: Henleggelse? = null,
) {
    val status: SoknadStatus
        get() =
            when {
                henleggelse != null -> SoknadStatus.HENLAGT
                vedtak == null -> SoknadStatus.MOTTATT
                else ->
                    when (vedtak.utfall) {
                        Utfall.Innvilget -> SoknadStatus.INNVILGET
                        is Utfall.DelvisInnvilget -> SoknadStatus.DELVIS_INNVILGET
                        Utfall.Avslag -> SoknadStatus.AVSLAG
                    }
            }

    init {
        if (soktePerioder.isEmpty()) {
            throw ManglerSoktePerioderException()
        }
        check(vedtak == null || henleggelse == null) {
            "Soknad kan ikke ha både vedtak og henleggelse"
        }
    }

    fun fattVedtak(
        utfall: Utfall,
        fattetAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
        begrunnelse: String?,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Vedtak kan kun fattes på en MOTTATT soknad, men status er $status"
        }

        val innvilgedePerioder =
            when (utfall) {
                Utfall.Innvilget -> soktePerioder
                is Utfall.DelvisInnvilget -> {
                    require(utfall.innvilgedePerioder.isNotEmpty()) {
                        "Delvis innvilgelse må ha minst én innvilget periode"
                    }
                    require(!utfall.innvilgedePerioder.harOverlapp()) {
                        "Innvilgede perioder ved delvis innvilgelse kan ikke overlappe"
                    }
                    require(
                        utfall.innvilgedePerioder.alleDagerErInnenfor(soktePerioder),
                    ) {
                        "Innvilgede perioder ved delvis innvilgelse må være innenfor søkte perioder"
                    }
                    utfall.innvilgedePerioder
                }
                Utfall.Avslag -> emptyList()
            }

        return copy(
            vedtak =
                Vedtak(
                    utfall = utfall,
                    fattetAv = fattetAv,
                    fattetTidspunkt = now,
                    innvilgedePerioder = innvilgedePerioder,
                    document = document,
                    begrunnelse = begrunnelse,
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
        now: OffsetDateTime,
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
    fun distribuerVedtak(now: OffsetDateTime): Soknad {
        val gjeldendeVedtak =
            checkNotNull(vedtak) {
                "Kan ikke distribuere en søknad som ikke har fått vedtak"
            }

        return copy(vedtak = gjeldendeVedtak.distribuer(now))
    }

    /**
     * Henlegger søknaden. En henleggelse er ikke et vedtak — søknaden avsluttes uten at det
     * er tatt stilling til utfallet. Kan kun gjøres på en MOTTATT søknad, og er gjensidig
     * utelukkende med [fattVedtak] (håndhevet av init-blokken over).
     */
    fun henlegg(
        begrunnelse: String,
        henlagtAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Henleggelse kan kun gjøres på en MOTTATT soknad, men status er $status"
        }

        return copy(
            henleggelse =
                Henleggelse(
                    begrunnelse = begrunnelse,
                    henlagtAv = henlagtAv,
                    henlagtTidspunkt = now,
                    document = document,
                ),
        )
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at journalføring kun kan skje
     * på en søknad som faktisk er henlagt. Selve idempotens-sjekken (kan ikke
     * journalføres to ganger) håndheves av Henleggelse.journalfor().
     */
    fun journalforHenleggelse(
        journalpostId: JournalpostId,
        now: OffsetDateTime,
    ): Soknad {
        val gjeldendeHenleggelse =
            checkNotNull(henleggelse) {
                "Kan ikke journalføre en søknad som ikke er henlagt"
            }

        return copy(henleggelse = gjeldendeHenleggelse.journalfor(journalpostId, now))
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at distribusjon kun kan skje
     * på en søknad som faktisk er henlagt. Selve idempotens- og rekkefølge-sjekken
     * (må være journalført, kan ikke distribueres to ganger) håndheves av Henleggelse.distribuer().
     */
    fun distribuerHenleggelse(now: OffsetDateTime): Soknad {
        val gjeldendeHenleggelse =
            checkNotNull(henleggelse) {
                "Kan ikke distribuere en søknad som ikke er henlagt"
            }

        return copy(henleggelse = gjeldendeHenleggelse.distribuer(now))
    }
}
