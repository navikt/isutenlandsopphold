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
    val behandling: BehandlingRecord? = null,
) {
    companion object {
        fun fromSoknad(soknad: Soknad) =
            SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = Soknadstatus.MOTTATT,
            )

        fun fromBehandletSoknad(soknad: Soknad): SoknadstatusRecord {
            val behandling =
                checkNotNull(soknad.behandling) {
                    "Soknad må være behandlet for å lage SoknadstatusRecord med behandling"
                }
            return SoknadstatusRecord(
                uuid = soknad.eksternId,
                createdAt = soknad.innsendtTidspunkt,
                personident = soknad.personident.value,
                status = Soknadstatus.BEHANDLET,
                behandling =
                    BehandlingRecord(
                        uuid = behandling.behandlingId,
                        createdAt = behandling.behandletTidspunkt,
                        veilederident = behandling.behandletAv.value,
                        utfall = behandling.utfall.toBehandlingRecordUtfall(),
                        innvilgedePerioder = behandling.innvilgedePerioder.map { BehandlingRecordPeriode(it.fom, it.tom) },
                    ),
            )
        }
    }
}

enum class Soknadstatus {
    MOTTATT,
    BEHANDLET,
}

data class BehandlingRecord(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val utfall: BehandlingRecordUtfall,
    val innvilgedePerioder: List<BehandlingRecordPeriode>,
)

enum class BehandlingRecordUtfall {
    AVSLAG,
    DELVIS_INNVILGET,
    INNVILGET,
    HENLAGT,
}

data class BehandlingRecordPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

private fun Utfall.toBehandlingRecordUtfall(): BehandlingRecordUtfall =
    when (this) {
        is Utfall.Avslag -> BehandlingRecordUtfall.AVSLAG
        is Utfall.DelvisInnvilget -> BehandlingRecordUtfall.DELVIS_INNVILGET
        is Utfall.Innvilget -> BehandlingRecordUtfall.INNVILGET
        is Utfall.Henlagt -> BehandlingRecordUtfall.HENLAGT
    }
