package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.soknadstatus

import no.nav.syfo.utenlandsopphold.application.PubliserSoknadstatusService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk publiserer status for søknader (MOTTATT) og fattede vedtak (BEHANDLET)
 * til soknadstatus-topicet.
 */
class PubliserSoknadstatusCronjob(
    private val publiserSoknadstatusService: PubliserSoknadstatusService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run(): List<Result<Any>> =
        publiserSoknadstatusService.publiserMottatteSoknader() + publiserSoknadstatusService.publiserBehandledeSoknader()
}
