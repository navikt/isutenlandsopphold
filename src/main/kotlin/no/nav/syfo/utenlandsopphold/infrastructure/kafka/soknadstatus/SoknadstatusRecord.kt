package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus

import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

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

        fun fromSoknadMedVedtak(soknad: Soknad): SoknadstatusRecord {
            require(soknad.vedtak != null) {
                "Soknad må ha vedtak for å lage SoknadstatusRecord med vedtak"
            }
            return SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = Soknadstatus.BEHANDLET,
                vedtak =
                    VedtakRecord(
                        uuid = soknad.vedtak.vedtakId,
                        createdAt = soknad.vedtak.fattetTidspunkt,
                        veilederident = soknad.vedtak.fattetAv.value,
                        utfall = soknad.vedtak.utfall.toVedtakRecordUtfall(),
                        innvilgedePerioder = soknad.vedtak.innvilgedePerioder.map { VedtakRecordPeriode(it.fom, it.tom) },
                    ),
            )
        }

        fun fromSoknadHenlagt(soknad: Soknad): SoknadstatusRecord {
            val henleggelse =
                requireNotNull(soknad.henleggelse) {
                    "Soknad må ha henleggelse for å lage SoknadstatusRecord med henleggelse"
                }
            return SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = Soknadstatus.HENLAGT,
                henleggelse =
                    HenleggelseRecord(
                        uuid = henleggelse.henleggelseId,
                        createdAt = henleggelse.henlagtTidspunkt,
                        veilederident = henleggelse.henlagtAv.value,
                    ),
            )
        }
    }
}

enum class Soknadstatus {
    MOTTATT,
    BEHANDLET,
    HENLAGT,
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
)

private fun Utfall.toVedtakRecordUtfall(): VedtakRecordUtfall =
    when (this) {
        is Utfall.Avslag -> VedtakRecordUtfall.AVSLAG
        is Utfall.DelvisInnvilget -> VedtakRecordUtfall.DELVIS_INNVILGET
        is Utfall.Innvilget -> VedtakRecordUtfall.INNVILGET
    }
