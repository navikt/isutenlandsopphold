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
                        Utfall.Innvilget -> SoknadStatus.INNVILGET
                        is Utfall.DelvisInnvilget -> SoknadStatus.DELVIS_INNVILGET
                        Utfall.Avslag -> SoknadStatus.AVSLAG
                        Utfall.Henlagt -> SoknadStatus.HENLAGT
                        is Utfall.IkkeAktuell -> SoknadStatus.IKKE_AKTUELL
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
        val brevtype =
            requireNotNull(utfall.brevtype()) {
                "Utfall $utfall gir ikke brev og kan ikke behandles med dokumentinnhold"
            }

        return registrerBehandling(
            utfall = utfall,
            behandletAv = behandletAv,
            now = now,
            begrunnelse = begrunnelse,
            brev = Brev(brevtype = brevtype, document = document),
        )
    }

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
            begrunnelse = null,
            brev = null,
        )

    private fun registrerBehandling(
        utfall: Utfall,
        behandletAv: Navident,
        now: OffsetDateTime,
        begrunnelse: String?,
        brev: Brev?,
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
                is Utfall.IkkeAktuell -> emptyList()
            }

        return copy(
            behandling =
                Behandling(
                    utfall = utfall,
                    behandletAv = behandletAv,
                    behandletTidspunkt = now,
                    innvilgedePerioder = innvilgedePerioder,
                    begrunnelse = begrunnelse,
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
