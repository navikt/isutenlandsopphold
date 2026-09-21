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
                        is Utfall.Innvilget -> SoknadStatus.INNVILGET
                        is Utfall.DelvisInnvilget -> SoknadStatus.DELVIS_INNVILGET
                        is Utfall.Avslag -> SoknadStatus.AVSLAG
                        is Utfall.Henlagt -> SoknadStatus.HENLAGT
                        is Utfall.IkkeAktuell -> SoknadStatus.IKKE_AKTUELL
                    }
            }

    init {
        if (soktePerioder.isEmpty()) {
            throw ManglerSoktePerioderException()
        }
    }

    fun fattVedtak(
        vedtakOppretting: VedtakOppretting,
        behandletAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
    ): Soknad {
        val vedtak = vedtakFor(vedtakOppretting)
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

    /**
     * Oversetter vedtakOppretting til vedtak. Ved full innvilgelse settes innvilgede perioder
     * til søknadens søkte perioder.
     */
    private fun vedtakFor(vedtak: VedtakOppretting): Utfall.Vedtak =
        when (vedtak) {
            VedtakOppretting.Innvilgelse -> Utfall.Innvilget(innvilgedePerioder = soktePerioder)
            is VedtakOppretting.DelvisInnvilgelse -> {
                require(!vedtak.innvilgedePerioder.harOverlapp()) {
                    "Innvilgede perioder ved delvis innvilgelse kan ikke overlappe"
                }
                require(vedtak.innvilgedePerioder.alleDagerErInnenfor(soktePerioder)) {
                    "Innvilgede perioder ved delvis innvilgelse må være innenfor søkte perioder"
                }
                Utfall.DelvisInnvilget(
                    innvilgedePerioder = vedtak.innvilgedePerioder,
                    begrunnelse = vedtak.begrunnelse,
                )
            }
            is VedtakOppretting.Avslag -> Utfall.Avslag(begrunnelse = vedtak.begrunnelse)
        }

    fun henlegg(
        behandletAv: Navident,
        now: OffsetDateTime,
        document: List<DocumentComponent>,
        begrunnelse: String,
    ): Soknad =
        registrerBehandling(
            utfall = Utfall.Henlagt(begrunnelse = begrunnelse),
            behandletAv = behandletAv,
            now = now,
            brev = Brev(brevtype = Brevtype.HENLEGGELSE, document = document),
        )

    /**
     * Markerer at søknaden ikke skal realitetsbehandles her. Det sendes ikke brev til
     * bruker, så det finnes heller ingenting å journalføre eller distribuere.
     */
    fun merkIkkeAktuell(
        grunn: IkkeAktuellGrunn,
        behandletAv: Navident,
        now: OffsetDateTime,
    ): Soknad =
        registrerBehandling(
            utfall = Utfall.IkkeAktuell(grunn),
            behandletAv = behandletAv,
            now = now,
            brev = null,
        )

    private fun registrerBehandling(
        utfall: Utfall,
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
