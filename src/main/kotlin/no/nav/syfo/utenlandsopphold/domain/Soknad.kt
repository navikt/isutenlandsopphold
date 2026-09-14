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
    val behandling: Behandling? = null,
) {
    val status: SoknadStatus
        get() =
            when (behandling) {
                null -> SoknadStatus.MOTTATT
                else ->
                    when (behandling.utfall) {
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
     * Registrerer resultatet av å behandle søknaden, sammen med brevet som skal sendes.
     * En søknad kan i dag kun behandles én gang.
     */
    fun behandle(
        utfall: Utfall,
        behandletAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
        begrunnelse: String?,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "En søknad kan kun behandles når den er MOTTATT, men status er $status"
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
            behandling =
                Behandling(
                    utfall = utfall,
                    behandletAv = behandletAv,
                    behandletTidspunkt = now,
                    innvilgedePerioder = innvilgedePerioder,
                    begrunnelse = begrunnelse,
                    brev =
                        Brev(
                            brevtype = utfall.brevtype(),
                            document = document,
                        ),
                ),
        )
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at journalføring kun kan skje
     * på en søknad som faktisk er behandlet. Selve idempotens-sjekken (kan ikke
     * journalføres to ganger) håndheves av [Brev.journalfor].
     */
    fun journalforBrev(
        journalpostId: JournalpostId,
        now: OffsetDateTime,
    ): Soknad {
        val gjeldendeBehandling =
            checkNotNull(behandling) {
                "Kan ikke journalføre brev for en søknad som ikke er behandlet"
            }

        return copy(behandling = gjeldendeBehandling.journalforBrev(journalpostId, now))
    }

    /**
     * Aggregatroten (Soknad) styrer invarianten om at distribusjon kun kan skje
     * på en søknad som faktisk er behandlet. Selve idempotens- og rekkefølge-sjekken
     * (må være journalført, kan ikke distribueres to ganger) håndheves av [Brev.distribuer].
     */
    fun distribuerBrev(now: OffsetDateTime): Soknad {
        val gjeldendeBehandling =
            checkNotNull(behandling) {
                "Kan ikke distribuere brev for en søknad som ikke er behandlet"
            }

        return copy(behandling = gjeldendeBehandling.distribuerBrev(now))
    }
}
