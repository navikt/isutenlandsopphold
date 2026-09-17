package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.journalforing

import no.nav.syfo.utenlandsopphold.application.DokumentService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk journalfører og distribuerer brev fra behandlede søknader.
 *
 * Journalføring og distribusjon slås sammen i én cronjob (i motsetning til f.eks. journalføring
 * og publisering som separate jobber), fordi distribusjon alltid forutsetter en vellykket
 * journalføring av samme dokument, se [no.nav.syfo.utenlandsopphold.domain.Dokument.distribuer].
 * Selve leder-valget og planlegging av kjøringer håndteres generisk av [CronjobRunner].
 */
class JournalforDokumentCronjob(
    private val dokumentService: DokumentService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run() = dokumentService.journalforDokument() + dokumentService.distribuerDokument()
}
