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
    IKKE_AKTUELL,
}

enum class VedtaksUtfall {
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLAG,
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
                        is BehandlingsUtfall.Innvilget -> SoknadStatus.INNVILGET
                        is BehandlingsUtfall.DelvisInnvilget -> SoknadStatus.DELVIS_INNVILGET
                        is BehandlingsUtfall.Avslag -> SoknadStatus.AVSLAG
                        is BehandlingsUtfall.Henlagt -> SoknadStatus.HENLAGT
                        is BehandlingsUtfall.IkkeAktuell -> SoknadStatus.IKKE_AKTUELL
                    }
            }

    init {
        if (soktePerioder.isEmpty()) {
            throw ManglerSoktePerioderException()
        }
    }

    /**
     * Fatter vedtak. [innvilgedePerioder] og [begrunnelse] brukes bare for bestemte
     * vedtaksutfall. Innvilgede perioder ignoreres for [VedtaksUtfall.INNVILGET].
     */
    fun fattVedtak(
        utfall: VedtaksUtfall,
        innvilgedePerioder: List<Periode>,
        begrunnelse: String?,
        behandletAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
    ): Soknad {
        val vedtak =
            when (utfall) {
                VedtaksUtfall.INNVILGET -> BehandlingsUtfall.Innvilget(innvilgedePerioder = soktePerioder)
                VedtaksUtfall.DELVIS_INNVILGET -> {
                    require(!innvilgedePerioder.harOverlapp()) {
                        "Innvilgede perioder ved delvis innvilgelse kan ikke overlappe"
                    }
                    require(innvilgedePerioder.alleDagerErInnenfor(soktePerioder)) {
                        "Innvilgede perioder ved delvis innvilgelse må være innenfor søkte perioder"
                    }
                    BehandlingsUtfall.DelvisInnvilget(
                        innvilgedePerioder = innvilgedePerioder,
                        begrunnelse = pakrevdBegrunnelse(begrunnelse, "delvis innvilgelse"),
                    )
                }
                VedtaksUtfall.AVSLAG -> BehandlingsUtfall.Avslag(begrunnelse = pakrevdBegrunnelse(begrunnelse, "avslag"))
            }
        val brevtype =
            requireNotNull(vedtak.brevtype()) {
                "Finner ikke brevtype for $vedtak"
            }

        return registrerBehandling(
            utfall = vedtak,
            behandletAv = behandletAv,
            now = now,
            brev = Brev(brevtype = brevtype, document = document),
        )
    }

    fun henlegg(
        behandletAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
        begrunnelse: String,
    ): Soknad =
        registrerBehandling(
            utfall = BehandlingsUtfall.Henlagt(begrunnelse = begrunnelse),
            behandletAv = behandletAv,
            now = now,
            brev = Brev(brevtype = Brevtype.HENLEGGELSE, document = document),
        )

    /**
     * Markerer at søknaden ikke skal realitetsbehandles her. Det sendes ikke brev til
     * bruker, så det finnes heller ingenting å journalføre eller distribuere.
     */
    fun merkIkkeAktuell(
        arsak: IkkeAktuellArsak,
        behandletAv: Navident,
        now: OffsetDateTime,
    ): Soknad =
        registrerBehandling(
            utfall = BehandlingsUtfall.IkkeAktuell(arsak),
            behandletAv = behandletAv,
            now = now,
            brev = null,
        )

    private fun registrerBehandling(
        utfall: BehandlingsUtfall,
        behandletAv: Navident,
        now: OffsetDateTime,
        brev: Brev?,
    ): Soknad {
        check(status == SoknadStatus.MOTTATT) {
            "En søknad kan kun behandles når den er MOTTATT, men status er $status"
        }

        return copy(
            behandling =
                Behandling(
                    utfall = utfall,
                    behandletAv = behandletAv,
                    behandletTidspunkt = now,
                    brev = brev,
                ),
        )
    }

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

    fun distribuerBrev(now: OffsetDateTime): Soknad {
        val gjeldendeBehandling =
            checkNotNull(behandling) {
                "Kan ikke distribuere brev for en søknad som ikke er behandlet"
            }

        return copy(behandling = gjeldendeBehandling.distribuerBrev(now))
    }
}
