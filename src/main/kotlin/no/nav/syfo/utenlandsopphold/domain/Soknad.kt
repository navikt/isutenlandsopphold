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
            when (behandlingsutfall) {
                null -> SoknadStatus.MOTTATT
                else ->
                    when (behandlingsutfall.utfall) {
                        Utfall.Innvilget -> SoknadStatus.INNVILGET
                        is Utfall.DelvisInnvilget -> SoknadStatus.DELVIS_INNVILGET
                        Utfall.Avslag -> SoknadStatus.AVSLAG
                        Utfall.Henlagt -> SoknadStatus.HENLAGT
                    }
            }

    init {
        if (soktePerioder.isEmpty()) {
            throw ManglerSoktePerioderException()
        }
    }

    /**
     * Registrerer utfallet av behandlingen av søknaden. Utfallet er ikke nødvendigvis et vedtak —
     * en henleggelse ([Utfall.Henlagt]) avslutter behandlingen uten at det fattes vedtak.
     */
    fun registrerBehandlingsutfall(
        utfall: Utfall,
        fattetAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
        begrunnelse: String?,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "Behandlingsutfall kan kun registreres på en MOTTATT soknad, men status er $status"
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
                Utfall.Henlagt -> emptyList()
            }

        return copy(
            behandlingsutfall =
                Behandlingsutfall(
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
     * på en søknad som faktisk er ferdigbehandlet. Selve idempotens-sjekken (kan ikke
     * journalføres to ganger) håndheves av Behandlingsutfall.journalfor().
     */
    fun journalforBehandlingsutfall(
        journalpostId: JournalpostId,
        now: OffsetDateTime,
    ): Soknad {
        val gjeldendeBehandlingsutfall =
            checkNotNull(behandlingsutfall) {
                "Kan ikke journalføre en søknad som ikke er ferdigbehandlet"
            }

        return copy(behandlingsutfall = gjeldendeBehandlingsutfall.journalfor(journalpostId, now))
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at distribusjon kun kan skje
     * på en søknad som faktisk er ferdigbehandlet. Selve idempotens- og rekkefølge-sjekken
     * (må være journalført, kan ikke distribueres to ganger) håndheves av
     * Behandlingsutfall.distribuer().
     */
    fun distribuerBehandlingsutfall(now: OffsetDateTime): Soknad {
        val gjeldendeBehandlingsutfall =
            checkNotNull(behandlingsutfall) {
                "Kan ikke distribuere en søknad som ikke er ferdigbehandlet"
            }

        return copy(behandlingsutfall = gjeldendeBehandlingsutfall.distribuer(now))
    }
}
