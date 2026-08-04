package no.nav.syfo.utenlandsopphold.infrastructure.cronjob

import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.launchBackgroundTask
import no.nav.syfo.utenlandsopphold.infrastructure.leaderelection.LeaderElection

/**
 * Starter alle registrerte cronjobber, hver i sin egen bakgrunnsoppgave, gjennom en felles
 * [CronjobRunner]. Nye cronjobber tas i bruk ved å legge dem til i [cronjobs]-listen på kallstedet.
 */
fun launchCronjobs(
    applicationState: ApplicationState,
    leaderElection: LeaderElection,
    cronjobs: List<Cronjob>,
) {
    val cronjobRunner =
        CronjobRunner(
            applicationState = applicationState,
            leaderElection = leaderElection,
        )

    cronjobs.forEach { cronjob ->
        launchBackgroundTask(applicationState = applicationState) {
            cronjobRunner.start(cronjob = cronjob)
        }
    }
}
