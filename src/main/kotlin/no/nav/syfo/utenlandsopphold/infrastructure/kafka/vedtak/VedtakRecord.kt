package no.nav.syfo.utenlandsopphold.infrastructure.kafka.vedtak

import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class VedtakRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: String,
    val veilederident: String,
    val soknadId: UUID,
    val utfall: String,
    val innvilgedePerioder: List<VedtakPeriode>,
) {
    companion object {
        fun fromVedtak(
            vedtak: Vedtak,
            soknad: Soknad,
        ) = VedtakRecord(
            uuid = vedtak.vedtakId,
            createdAt = vedtak.fattetTidspunkt,
            personident = soknad.personident.value,
            veilederident = vedtak.fattetAv.value,
            soknadId = soknad.eksternId,
            utfall =
                when (vedtak.utfall) {
                    is Utfall.Avslag -> "Avslag"
                    is Utfall.DelvisInnvilget -> "DelvisInnvilget"
                    is Utfall.Innvilget -> "Innvilget"
                },
            innvilgedePerioder = vedtak.innvilgedePerioder.map { VedtakPeriode(it.fom, it.tom) },
        )
    }
}

data class VedtakPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)
