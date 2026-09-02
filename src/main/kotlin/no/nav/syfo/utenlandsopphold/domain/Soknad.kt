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
    val behandlingsutfall: Behandlingsutfall? = null,
) {
    val status: SoknadStatus
        get() =
            when (val gjeldendeBehandlingsutfall = behandlingsutfall) {
                null -> SoknadStatus.MOTTATT
                is Henleggelse -> SoknadStatus.HENLAGT
                is Vedtak ->
                    when (gjeldendeBehandlingsutfall.utfall) {
                        Utfall.Innvilget -> SoknadStatus.INNVILGET
                        is Utfall.DelvisInnvilget -> SoknadStatus.DELVIS_INNVILGET
                        Utfall.Avslag -> SoknadStatus.AVSLAG
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
            behandlingsutfall =
                Vedtak(
                    utfall = utfall,
                    fattetAv = fattetAv,
                    fattetTidspunkt = now,
                    innvilgedePerioder = innvilgedePerioder,
                    begrunnelse = begrunnelse,
                    utsending = Utsending(document = document),
                ),
        )
    }

    /**
     * Henlegger søknaden. En henleggelse er teknisk sett ikke et vedtak (søknaden avgjøres
     * ikke med et utfall), men krever på samme måte som et vedtak en begrunnelse og et
     * dokument som journalføres og distribueres, se [Henleggelse] og [Utsending].
     */
    fun henlegg(
        fattetAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
        begrunnelse: String,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Søknad kan kun henlegges når den er MOTTATT, men status er $status"
        }

        return copy(
            behandlingsutfall =
                Henleggelse(
                    fattetAv = fattetAv,
                    fattetTidspunkt = now,
                    begrunnelse = begrunnelse,
                    utsending = Utsending(document = document),
                ),
        )
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at journalføring kun kan skje
     * på en søknad som faktisk har et behandlingsutfall (vedtak eller henleggelse). Selve
     * idempotens-sjekken (kan ikke journalføres to ganger) håndheves av
     * Behandlingsutfall.journalfor().
     */
    fun journalforBehandlingsutfall(
        journalpostId: JournalpostId,
        now: OffsetDateTime,
    ): Soknad {
        val gjeldendeBehandlingsutfall =
            checkNotNull(behandlingsutfall) {
                "Kan ikke journalføre en søknad som ikke har fått behandlingsutfall"
            }

        return copy(behandlingsutfall = gjeldendeBehandlingsutfall.journalfor(journalpostId, now))
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at distribusjon kun kan skje
     * på en søknad som faktisk har et behandlingsutfall. Selve idempotens- og
     * rekkefølge-sjekken (må være journalført, kan ikke distribueres to ganger) håndheves av
     * Behandlingsutfall.distribuer().
     */
    fun distribuerBehandlingsutfall(now: OffsetDateTime): Soknad {
        val gjeldendeBehandlingsutfall =
            checkNotNull(behandlingsutfall) {
                "Kan ikke distribuere en søknad som ikke har fått behandlingsutfall"
            }

        return copy(behandlingsutfall = gjeldendeBehandlingsutfall.distribuer(now))
    }
}
