package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.utenlandsopphold.getEnvVar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Konfigurasjon for [launchJournalforVedtakCronjob].
 *
 * @param interval Tid mellom hver kjøring av cronjobben.
 * @param freshVedtakGracePeriod Cronjobben plukker ikke opp vedtak som er fattet innenfor dette
 * tidsvinduet. Dette gir API-laget (som forsøker journalføring og deretter distribusjon
 * umiddelbart etter at et vedtak er fattet, se
 * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]) rom til å journalføre og
 * distribuere vedtaket selv, uten at cronjobben forsøker det samme samtidig. Verdien bør være
 * komfortabelt større enn forventet samlet varighet på journalføring **og** distribusjon
 * (PDL + PDF-generering + dokarkiv + dokdistfordeling).
 */
data class JournalforingCronjobConfig(
    val interval: Duration,
    val freshVedtakGracePeriod: Duration,
) {
    companion object {
        /**
         * Leser [JournalforingCronjobConfig] fra miljøvariabler
         * `JOURNALFORING_CRONJOB_INTERVAL_MINUTES` (default 10 minutter) og
         * `JOURNALFORING_FRESH_VEDTAK_GRACE_SECONDS` (default 30 sekunder).
         */
        fun fromEnv(): JournalforingCronjobConfig =
            JournalforingCronjobConfig(
                interval = getEnvVar("JOURNALFORING_CRONJOB_INTERVAL_MINUTES", "10").toLong().minutes,
                freshVedtakGracePeriod = getEnvVar("JOURNALFORING_FRESH_VEDTAK_GRACE_SECONDS", "60").toLong().seconds,
            )
    }
}
