package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.soknadstatus

import no.nav.syfo.utenlandsopphold.getEnvVar

data class PublishSoknadstatusCronjobConfig(
    val initialDelayMinutes: Long,
    val intervalDelayMinutes: Long,
) {
    companion object {
        /**
         * Leser [PublishSoknadstatusCronjobConfig] fra miljøvariabler
         * `PUBLISH_SOKNADSTATUS_CRONJOB_INITIAL_DELAY_MINUTES` (default 2 minutter) og
         * `PUBLISH_SOKNADSTATUS_CRONJOB_INTERVAL_MINUTES` (default 10 minutter).
         */
        fun fromEnv(): PublishSoknadstatusCronjobConfig =
            PublishSoknadstatusCronjobConfig(
                initialDelayMinutes = getEnvVar("PUBLISH_SOKNADSTATUS_CRONJOB_INITIAL_DELAY_MINUTES", "2").toLong(),
                intervalDelayMinutes = getEnvVar("PUBLISH_SOKNADSTATUS_CRONJOB_INTERVAL_MINUTES", "10").toLong(),
            )
    }
}
