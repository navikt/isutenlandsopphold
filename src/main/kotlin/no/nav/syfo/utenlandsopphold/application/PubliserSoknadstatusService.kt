package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.SoknadstatusRecord
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

class PubliserSoknadstatusService(
    private val soknadRepository: ISoknadRepository,
    private val soknadstatusProducer: ISoknadstatusProducer,
) {
    fun publiserMottatteSoknader(): List<Result<Unit>> {
        val upubliserteSoknader = soknadRepository.getUpubliserteSoknader()

        return upubliserteSoknader.map { soknad ->
            runCatching { publiserMottattSoknad(soknad) }
                .onFailure { log.error("Feil ved publisering av MOTTATT-status for søknad ${soknad.id}", it) }
        }
    }

    private fun publiserMottattSoknad(soknad: Soknad) {
        soknadstatusProducer.publiser(SoknadstatusRecord.fromSoknad(soknad)).getOrThrow()

        soknadRepository.setSoknadPublisert(soknadId = soknad.id, publisertTidspunkt = OffsetDateTime.now())
        log.info("Søknad ${soknad.id} publisert som MOTTATT")
    }

    fun publiserBehandledeSoknader(): List<Result<Unit>> {
        val soknaderMedUpublisertVedtak = soknadRepository.getSoknaderMedUpublisertVedtak()

        return soknaderMedUpublisertVedtak.map { soknad ->
            runCatching { publiserBehandletSoknad(soknad) }
                .onFailure { log.error("Feil ved publisering av BEHANDLET-status for søknad ${soknad.id}", it) }
        }
    }

    private fun publiserBehandletSoknad(soknad: Soknad) {
        val vedtak =
            checkNotNull(soknad.vedtak) {
                "Søknad ${soknad.id} har ikke fattet vedtak, kan ikke publisere BEHANDLET-status"
            }

        soknadstatusProducer.publiser(SoknadstatusRecord.fromSoknadMedVedtak(soknad)).getOrThrow()

        soknadRepository.setVedtakPublisert(vedtakId = vedtak.vedtakId, publisertTidspunkt = OffsetDateTime.now())
        log.info("Vedtak ${vedtak.vedtakId} for søknad ${soknad.id} publisert som BEHANDLET")
    }

    companion object {
        private val log = LoggerFactory.getLogger(PubliserSoknadstatusService::class.java)
    }
}
