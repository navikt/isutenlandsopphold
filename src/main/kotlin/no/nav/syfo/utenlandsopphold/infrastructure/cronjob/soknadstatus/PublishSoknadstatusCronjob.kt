package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.soknadstatus

import no.nav.syfo.utenlandsopphold.application.PublishSoknadstatusService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk publiserer status for søknader (MOTTATT) og registrerte behandlingsutfall (BEHANDLET)
 * til soknadstatus-topicet.
 */
class PublishSoknadstatusCronjob(
    private val publishSoknadstatusService: PublishSoknadstatusService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run(): List<Result<Any>> =
        publishSoknadstatusService.publishMottatteSoknader() + publishSoknadstatusService.publishBehandledeSoknader()
}
