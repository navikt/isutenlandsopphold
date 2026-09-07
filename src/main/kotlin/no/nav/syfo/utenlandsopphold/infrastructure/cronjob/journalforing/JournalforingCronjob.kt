package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.journalforing

import no.nav.syfo.utenlandsopphold.application.JournalforHenleggelseService
import no.nav.syfo.utenlandsopphold.application.JournalforVedtakService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob

/**
 * Cronjob som periodisk journalfører og distribuerer både fattede vedtak og henleggelser.
 *
 * Journalføring og distribusjon slås sammen i én cronjob (i motsetning til f.eks. journalføring
 * og publisering som separate jobber), fordi distribusjon alltid forutsetter en vellykket
 * journalføring for samme vedtak/henleggelse, se
 * [no.nav.syfo.utenlandsopphold.domain.Vedtak.distribuer] og
 * [no.nav.syfo.utenlandsopphold.domain.Henleggelse.distribuer]. Vedtak og henleggelser
 * behandles i samme jobb siden de deler samme mønster og planlegging. Feil isoleres per
 * delresultat, slik at én feil ikke stopper resten. Selve leder-valget og planlegging av
 * kjøringer håndteres generisk av [no.nav.syfo.utenlandsopphold.infrastructure.cronjob.CronjobRunner].
 */
class JournalforingCronjob(
    private val journalforVedtakService: JournalforVedtakService,
    private val journalforHenleggelseService: JournalforHenleggelseService,
    override val initialDelayMinutes: Long,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override suspend fun run(): List<Result<Any>> =
        journalforVedtakService.journalforVedtak() +
            journalforVedtakService.distribuerVedtak() +
            journalforHenleggelseService.journalforHenleggelse() +
            journalforHenleggelseService.distribuerHenleggelse()
}
