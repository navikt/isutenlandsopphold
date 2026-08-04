package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.journalforing

import no.nav.syfo.utenlandsopphold.application.JournalforVedtakService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk journalfører og distribuerer fattede vedtak.
 *
 * Journalføring og distribusjon slås sammen i én cronjob (i motsetning til f.eks. journalføring
 * og publisering som separate jobber), fordi distribusjon alltid forutsetter en vellykket
 * journalføring for samme vedtak, se [no.nav.syfo.utenlandsopphold.domain.Vedtak.distribuer].
 * Selve leder-valget og planlegging av kjøringer håndteres generisk av [CronjobRunner].
 */
class JournalforVedtakCronjob(
    private val journalforVedtakService: JournalforVedtakService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run(): List<Result<Any>> =
        journalforVedtakService.journalforVedtak() + journalforVedtakService.distribuerVedtak()
}
