package no.nav.syfo.utenlandsopphold.application

import kotlinx.coroutines.delay
import no.nav.syfo.utenlandsopphold.infrastructure.leaderelection.LeaderElection
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Cronjob (in-process, leder-valgt løkke) som periodisk journalfører vedtak.
 *
 * Kjører kun på podden som er valgt leder (NAIS `leaderElection: true`), slik at ikke
 * alle replikaer journalfører de samme vedtakene samtidig og skaper duplikate
 * journalposter i dokarkiv.
 */
fun launchJournalforVedtakCronjob(
    applicationState: ApplicationState,
    leaderElection: LeaderElection,
    journalforVedtakService: JournalforVedtakService,
    interval: Duration,
) {
    launchBackgroundTask(applicationState = applicationState) {
        while (applicationState.ready) {
            try {
                if (leaderElection.isLeader()) {
                    journalforVedtakService.journalforVedtak()
                    journalforVedtakService.distribuerVedtak()
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                log.error("Uventet feil i journalføring-cronjob, prøver igjen ved neste intervall", ex)
            }
            delay(interval)
        }
    }
}

private val log = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.application.JournalforVedtakCronjob")
