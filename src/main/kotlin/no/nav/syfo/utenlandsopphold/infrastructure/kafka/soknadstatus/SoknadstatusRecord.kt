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
    val status: String,
    val vedtak: VedtakRecord? = null,
) {
    companion object {
        fun fromSoknad(
            soknad: Soknad,
        ) = {
            require(soknad.vedtak == null) {
                "Soknad må ikke ha vedtak for å lage SoknadstatusRecord uten vedtak"
            }

            SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = "MOTTATT",
            )
        }

        fun fromSoknadMedVedtak(
            soknad: Soknad,
        ) = {
            require(soknad.vedtak != null) {
                "Soknad må ha vedtak for å lage SoknadstatusRecord med vedtak"
            }
            SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = "BEHANDLET",
                vedtak = VedtakRecord(
                    uuid = soknad.vedtak.vedtakId,
                    createdAt = soknad.vedtak.fattetTidspunkt,
                    veilederident = soknad.vedtak.fattetAv.value,
                    utfall =
                        when (soknad.vedtak.utfall) {
                            is Utfall.Avslag -> "Avslag"
                            is Utfall.DelvisInnvilget -> "DelvisInnvilget"
                            is Utfall.Innvilget -> "Innvilget"
                        },
                    innvilgedePerioder = soknad.vedtak.innvilgedePerioder.map { VedtakRecordPeriode(it.fom, it.tom) },
                ),
            )
        }
    }
}

data class VedtakRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val utfall: String,
    val innvilgedePerioder: List<VedtakRecordPeriode>,
)

data class VedtakRecordPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)
