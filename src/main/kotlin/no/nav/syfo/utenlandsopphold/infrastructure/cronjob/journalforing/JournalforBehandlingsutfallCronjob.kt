package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.journalforing

import no.nav.syfo.utenlandsopphold.application.JournalforBehandlingsutfallService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk journalfører og distribuerer registrerte behandlingsutfall.
 *
 * Journalføring og distribusjon slås sammen i én cronjob (i motsetning til f.eks. journalføring
 * og publisering som separate jobber), fordi distribusjon alltid forutsetter en vellykket
 * journalføring for samme behandlingsutfall, se
 * [no.nav.syfo.utenlandsopphold.domain.Behandlingsutfall.distribuer].
 * Selve leder-valget og planlegging av kjøringer håndteres generisk av [CronjobRunner].
 */
class JournalforBehandlingsutfallCronjob(
    private val journalforBehandlingsutfallService: JournalforBehandlingsutfallService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run() =
        journalforBehandlingsutfallService.journalforBehandlingsutfall() +
            journalforBehandlingsutfallService.distribuerBehandlingsutfall()
}
