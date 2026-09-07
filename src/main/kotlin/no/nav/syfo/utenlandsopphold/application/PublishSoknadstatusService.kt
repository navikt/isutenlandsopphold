package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.SoknadstatusRecord
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

class PublishSoknadstatusService(
    private val soknadRepository: ISoknadRepository,
    private val soknadstatusProducer: ISoknadstatusProducer,
) {
    fun publishMottatteSoknader(): List<Result<Unit>> {
        val unpublishedSoknader = soknadRepository.getUnpublishedSoknader()

        return unpublishedSoknader.map { soknad ->
            runCatching { publishMottattSoknad(soknad) }
                .onFailure { log.error("Feil ved publisering av MOTTATT-status for søknad ${soknad.id}", it) }
        }
    }

    private fun publishMottattSoknad(soknad: Soknad) {
        soknadstatusProducer.publish(SoknadstatusRecord.fromSoknad(soknad)).getOrThrow()
        soknadRepository.setSoknadPublished(soknadId = soknad.id, publishedAt = OffsetDateTime.now())
        log.info("Søknad ${soknad.id} publisert som MOTTATT")
    }

    fun publishBehandledeSoknader(): List<Result<Unit>> {
        val soknaderMedUnpublishedBehandlingsutfall = soknadRepository.getSoknaderMedUnpublishedBehandlingsutfall()

        return soknaderMedUnpublishedBehandlingsutfall.map { soknad ->
            runCatching { publishBehandletSoknad(soknad) }
                .onFailure { log.error("Feil ved publisering av BEHANDLET-status for søknad ${soknad.id}", it) }
        }
    }

    private fun publishBehandletSoknad(soknad: Soknad) {
        val behandlingsutfall =
            checkNotNull(soknad.behandlingsutfall) {
                "Søknad ${soknad.id} er ikke ferdigbehandlet, kan ikke publisere BEHANDLET-status"
            }

        soknadstatusProducer.publish(SoknadstatusRecord.fromSoknadMedBehandlingsutfall(soknad)).getOrThrow()

        soknadRepository.setBehandlingsutfallPublished(
            behandlingsutfallId = behandlingsutfall.behandlingsutfallId,
            publishedAt = OffsetDateTime.now(),
        )
        log.info("Behandlingsutfall ${behandlingsutfall.behandlingsutfallId} for søknad ${soknad.id} publisert som BEHANDLET")
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishSoknadstatusService::class.java)
    }
}
