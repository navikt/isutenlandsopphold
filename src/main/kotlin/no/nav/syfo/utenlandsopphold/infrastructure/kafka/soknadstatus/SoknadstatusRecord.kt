package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus

import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Kafka-kontrakt for søknadsstatus. Feltnavnene her (inkludert det nestede `vedtak`-objektet)
 * er en ekstern kontrakt mot konsumenter, og beholdes uendret selv om domenet internt er
 * generalisert fra `Vedtak` til `Behandlingsutfall`. Mappingen under fungerer derfor som en
 * bevisst adapter fra domenet til den eksisterende kontrakten.
 */
data class SoknadstatusRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: String,
    val status: Soknadstatus,
    val vedtak: VedtakRecord? = null,
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
                    "Soknad må ha behandlingsutfall for å lage SoknadstatusRecord med vedtak"
                }
            return SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = Soknadstatus.BEHANDLET,
                vedtak =
                    VedtakRecord(
                        uuid = behandlingsutfall.behandlingsutfallId,
                        createdAt = behandlingsutfall.fattetTidspunkt,
                        veilederident = behandlingsutfall.fattetAv.value,
                        utfall = behandlingsutfall.utfall.toVedtakRecordUtfall(),
                        innvilgedePerioder = behandlingsutfall.innvilgedePerioder.map { VedtakRecordPeriode(it.fom, it.tom) },
                    ),
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
    HENLAGT,
}

data class VedtakRecordPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

private fun Utfall.toVedtakRecordUtfall(): VedtakRecordUtfall =
    when (this) {
        is Utfall.Avslag -> VedtakRecordUtfall.AVSLAG
        is Utfall.DelvisInnvilget -> VedtakRecordUtfall.DELVIS_INNVILGET
        is Utfall.Innvilget -> VedtakRecordUtfall.INNVILGET
        is Utfall.Henlagt -> VedtakRecordUtfall.HENLAGT
    }
