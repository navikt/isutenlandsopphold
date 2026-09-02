package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus

import no.nav.syfo.utenlandsopphold.domain.Henleggelse
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Både vedtak og henleggelse publiseres med status BEHANDLET — en henleggelse er teknisk
 * sett ikke et vedtak, men søknaden er ferdigbehandlet. Konsumenter skiller mellom de to
 * ved å se på hvilket av feltene [vedtak]/[henleggelse] som er satt.
 */
data class SoknadstatusRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: String,
    val status: Soknadstatus,
    val vedtak: VedtakRecord? = null,
    val henleggelse: HenleggelseRecord? = null,
) {
    companion object {
        fun fromSoknad(soknad: Soknad) =
            SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = Soknadstatus.MOTTATT,
            )

        fun fromSoknadMedBehandlingsutfall(soknad: Soknad): SoknadstatusRecord {
            val behandlingsutfall =
                requireNotNull(soknad.behandlingsutfall) {
                    "Soknad må ha behandlingsutfall for å lage SoknadstatusRecord med vedtak/henleggelse"
                }

            return SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = Soknadstatus.BEHANDLET,
                vedtak =
                    (behandlingsutfall as? Vedtak)?.let { vedtak ->
                        VedtakRecord(
                            uuid = vedtak.behandlingsutfallId,
                            createdAt = vedtak.fattetTidspunkt,
                            veilederident = vedtak.fattetAv.value,
                            utfall = vedtak.utfall.toVedtakRecordUtfall(),
                            innvilgedePerioder = vedtak.innvilgedePerioder.map { VedtakRecordPeriode(it.fom, it.tom) },
                        )
                    },
                henleggelse =
                    (behandlingsutfall as? Henleggelse)?.let { henleggelse ->
                        HenleggelseRecord(
                            uuid = henleggelse.behandlingsutfallId,
                            createdAt = henleggelse.fattetTidspunkt,
                            veilederident = henleggelse.fattetAv.value,
                            begrunnelse = henleggelse.begrunnelse,
                        )
                    },
            )
        }
    }
}

enum class Soknadstatus {
    MOTTATT,
    BEHANDLET,
}

data class VedtakRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val utfall: VedtakRecordUtfall,
    val innvilgedePerioder: List<VedtakRecordPeriode>,
)

enum class VedtakRecordUtfall {
    AVSLAG,
    DELVIS_INNVILGET,
    INNVILGET,
}

data class VedtakRecordPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

data class HenleggelseRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val begrunnelse: String,
)

private fun Utfall.toVedtakRecordUtfall(): VedtakRecordUtfall =
    when (this) {
        is Utfall.Avslag -> VedtakRecordUtfall.AVSLAG
        is Utfall.DelvisInnvilget -> VedtakRecordUtfall.DELVIS_INNVILGET
        is Utfall.Innvilget -> VedtakRecordUtfall.INNVILGET
    }
