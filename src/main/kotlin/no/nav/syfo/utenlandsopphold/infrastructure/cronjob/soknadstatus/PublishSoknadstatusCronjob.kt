package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.soknadstatus

import no.nav.syfo.utenlandsopphold.application.PublishSoknadstatusService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk publiserer status for søknader (MOTTATT) og behandlede søknader (BEHANDLET)
 * til soknadstatus-topicet. BEHANDLET publiseres normalt umiddelbart fra
 * [no.nav.syfo.utenlandsopphold.application.SoknadService]. For BEHANDLET er denne jobben en
 * fallback som plukker opp publiseringer som har feilet.
 */
class PublishSoknadstatusCronjob(
    private val publishSoknadstatusService: PublishSoknadstatusService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run(): List<Result<Any>> =
        publishSoknadstatusService.publishMottatteSoknader() + publishSoknadstatusService.publishBehandledeSoknader()
}
