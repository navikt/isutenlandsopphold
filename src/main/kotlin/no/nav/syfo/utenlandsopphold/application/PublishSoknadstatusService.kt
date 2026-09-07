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
        val soknaderMedUnpublishedVedtak = soknadRepository.getSoknaderMedUnpublishedVedtak()

        return soknaderMedUnpublishedVedtak.map { soknad ->
            runCatching { publishBehandletSoknad(soknad) }
                .onFailure { log.error("Feil ved publisering av BEHANDLET-status for søknad ${soknad.id}", it) }
        }
    }

    private fun publishBehandletSoknad(soknad: Soknad) {
        val vedtak =
            checkNotNull(soknad.vedtak) {
                "Søknad ${soknad.id} har ikke fattet vedtak, kan ikke publisere BEHANDLET-status"
            }

        soknadstatusProducer.publish(SoknadstatusRecord.fromSoknadMedVedtak(soknad)).getOrThrow()

        soknadRepository.setVedtakPublished(vedtakId = vedtak.vedtakId, publishedAt = OffsetDateTime.now())
        log.info("Vedtak ${vedtak.vedtakId} for søknad ${soknad.id} publisert som BEHANDLET")
    }

    fun publishHenlagteSoknader(): List<Result<Unit>> {
        val soknaderMedUnpublishedHenleggelse = soknadRepository.getSoknaderMedUnpublishedHenleggelse()

        return soknaderMedUnpublishedHenleggelse.map { soknad ->
            runCatching { publishHenlagtSoknad(soknad) }
                .onFailure { log.error("Feil ved publisering av HENLAGT-status for søknad ${soknad.id}", it) }
        }
    }

    private fun publishHenlagtSoknad(soknad: Soknad) {
        val henleggelse =
            checkNotNull(soknad.henleggelse) {
                "Søknad ${soknad.id} er ikke henlagt, kan ikke publisere HENLAGT-status"
            }

        soknadstatusProducer.publish(SoknadstatusRecord.fromSoknadHenlagt(soknad)).getOrThrow()

        soknadRepository.setHenleggelsePublished(henleggelseId = henleggelse.henleggelseId, publishedAt = OffsetDateTime.now())
        log.info("Henleggelse ${henleggelse.henleggelseId} for søknad ${soknad.id} publisert som HENLAGT")
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishSoknadstatusService::class.java)
    }
}
