package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.journalforing

import no.nav.syfo.utenlandsopphold.application.BrevService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk journalfører og distribuerer brev fra behandlede søknader.
 *
 * Journalføring og distribusjon slås sammen i én cronjob (i motsetning til f.eks. journalføring
 * og publisering som separate jobber), fordi distribusjon alltid forutsetter en vellykket
 * journalføring av samme brev, se [no.nav.syfo.utenlandsopphold.domain.Brev.distribuer].
 * Selve leder-valget og planlegging av kjøringer håndteres generisk av [CronjobRunner].
 */
class JournalforBrevCronjob(
    private val brevService: BrevService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run() = brevService.journalforBrev() + brevService.distribuerBrev()
}
