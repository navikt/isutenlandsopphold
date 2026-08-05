package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.soknadstatus

import no.nav.syfo.utenlandsopphold.getEnvVar

data class PubliserSoknadstatusCronjobConfig(
    val initialDelayMinutes: Long,
    val intervalDelayMinutes: Long,
) {
    companion object {
        /**
         * Leser [PubliserSoknadstatusCronjobConfig] fra miljøvariabler
         * `PUBLISER_SOKNADSTATUS_CRONJOB_INITIAL_DELAY_MINUTES` (default 2 minutter) og
         * `PUBLISER_SOKNADSTATUS_CRONJOB_INTERVAL_MINUTES` (default 10 minutter).
         */
        fun fromEnv(): PubliserSoknadstatusCronjobConfig =
            PubliserSoknadstatusCronjobConfig(
                initialDelayMinutes = getEnvVar("PUBLISER_SOKNADSTATUS_CRONJOB_INITIAL_DELAY_MINUTES", "2").toLong(),
                intervalDelayMinutes = getEnvVar("PUBLISER_SOKNADSTATUS_CRONJOB_INTERVAL_MINUTES", "10").toLong(),
            )
    }
}
