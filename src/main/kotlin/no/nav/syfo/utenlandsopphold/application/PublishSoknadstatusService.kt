package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.SoknadstatusRecord
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Publiserer MOTTATT og BEHANDLET for søknader til soknadstatus-topicet. MOTTATT publiseres av den
 * periodiske cronjobben. BEHANDLET publiseres umiddelbart fra [SoknadService] når søknaden
 * behandles, med cronjobben som fallback.
 *
 * All publisering skjer mens søknaden er låst (`SELECT ... FOR UPDATE`), og publiseringsstatus
 * sjekkes på nytt under låsen. Det gjør at samme status ikke publiseres samtidig fra flere steder,
 * og at MOTTATT aldri publiseres etter BEHANDLET for samme søknad. Hvis det skjedde, ville
 * oppgaven blitt åpnet på nytt i syfooversiktsrv.
 */
class PublishSoknadstatusService(
    private val transactionManager: TransactionManager,
    private val soknadRepository: ISoknadRepository,
    private val soknadstatusProducer: ISoknadstatusProducer,
) {
    fun publishMottatteSoknader(): List<Result<Unit>> {
        val unpublishedSoknader = soknadRepository.getUnpublishedSoknader()

        return unpublishedSoknader.map { soknad ->
            runCatching { publishMottattSoknad(soknad.id) }
                .onFailure { log.error("Feil ved publisering av MOTTATT-status for søknad ${soknad.id}", it) }
        }
    }

    fun publishMottattSoknad(soknadId: UUID) {
        transactionManager.inTransaction { transaction ->
            val soknad =
                soknadRepository.hentSoknadForUpdate(transaction, soknadId)
                    ?: throw SoknadFinnesIkkeException(soknadId)
            if (!soknad.shouldPublishMottatt()) {
                return@inTransaction
            }

            soknadstatusProducer.publish(SoknadstatusRecord.fromSoknad(soknad)).getOrThrow()
            soknadRepository.setSoknadPublished(transaction, soknadId, OffsetDateTime.now())
            log.info("Søknad $soknadId publisert som MOTTATT")
        }
    }

    fun publishBehandledeSoknader(): List<Result<Unit>> {
        val soknaderMedUpublisertBehandling = soknadRepository.getSoknaderMedUnpublishedBehandling()

        return soknaderMedUpublisertBehandling.map { soknad ->
            runCatching { publishBehandletSoknad(soknad.id) }
                .onFailure { log.error("Feil ved publisering av BEHANDLET-status for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Gjør ingenting hvis BEHANDLET allerede er publisert, eller hvis MOTTATT ikke er publisert ennå.
     * I siste tilfelle publiserer cronjobben begge senere i riktig rekkefølge.
     */
    fun publishBehandletSoknad(soknadId: UUID) {
        transactionManager.inTransaction { transaction ->
            val soknad =
                soknadRepository.hentSoknadForUpdate(transaction, soknadId)
                    ?: throw SoknadFinnesIkkeException(soknadId)
            if (!soknad.shouldPublishBehandlet()) {
                return@inTransaction
            }
            val behandling = checkNotNull(soknad.behandling)

            soknadstatusProducer.publish(SoknadstatusRecord.fromBehandletSoknad(soknad)).getOrThrow()
            soknadRepository.setBehandlingPublished(transaction, behandling.behandlingId, OffsetDateTime.now())
            log.info("Behandling ${behandling.behandlingId} for søknad $soknadId publisert som BEHANDLET")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishSoknadstatusService::class.java)
    }
}
