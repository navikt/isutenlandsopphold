package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.utenlandsopphold.getEnvVar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Konfigurasjon for [launchJournalforVedtakCronjob].
 *
 * @param interval Tid mellom hver kjøring av cronjobben.
 */
data class JournalforingCronjobConfig(
    val interval: Duration,
) {
    companion object {
        /**
         * Leser [JournalforingCronjobConfig] fra NAIS-injisert miljøvariabel
         * `JOURNALFORING_CRONJOB_INTERVAL_MINUTES` (default 10 minutter).
         */
        fun fromEnv(): JournalforingCronjobConfig =
            JournalforingCronjobConfig(
                interval = getEnvVar("JOURNALFORING_CRONJOB_INTERVAL_MINUTES", "10").toLong().minutes,
            )
    }
}
