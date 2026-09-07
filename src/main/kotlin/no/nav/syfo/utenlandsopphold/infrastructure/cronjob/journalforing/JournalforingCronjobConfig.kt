package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.journalforing

import no.nav.syfo.utenlandsopphold.getEnvVar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Konfigurasjon for [JournalforBehandlingsutfallCronjob].
 *
 * @param initialDelayMinutes Tid fra applikasjonen er klar til cronjobben kjører første gang.
 * @param interval Tid mellom hver kjøring av cronjobben.
 * @param freshBehandlingsutfallGracePeriod Cronjobben plukker ikke opp behandlingsutfall som er
 * registrert innenfor dette tidsvinduet. Dette gir API-laget (som forsøker journalføring og deretter
 * distribusjon umiddelbart etter at et behandlingsutfall er registrert, se
 * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]) rom til å journalføre og
 * distribuere selv, uten at cronjobben forsøker det samme samtidig. Verdien bør være
 * komfortabelt større enn forventet samlet varighet på journalføring **og** distribusjon
 * (PDL + PDF-generering + dokarkiv + dokdistfordeling).
 */
data class JournalforingCronjobConfig(
    val initialDelayMinutes: Long,
    val interval: Duration,
    val freshBehandlingsutfallGracePeriod: Duration,
) {
    companion object {
        /**
         * Leser [JournalforingCronjobConfig] fra miljøvariabler
         * `JOURNALFORING_CRONJOB_INITIAL_DELAY_MINUTES` (default 2 minutter),
         * `JOURNALFORING_CRONJOB_INTERVAL_MINUTES` (default 10 minutter) og
         * `JOURNALFORING_FRESH_VEDTAK_GRACE_SECONDS` (default 60 sekunder).
         */
        fun fromEnv(): JournalforingCronjobConfig =
            JournalforingCronjobConfig(
                initialDelayMinutes = getEnvVar("JOURNALFORING_CRONJOB_INITIAL_DELAY_MINUTES", "2").toLong(),
                interval = getEnvVar("JOURNALFORING_CRONJOB_INTERVAL_MINUTES", "10").toLong().minutes,
                freshBehandlingsutfallGracePeriod =
                    getEnvVar("JOURNALFORING_FRESH_VEDTAK_GRACE_SECONDS", "60").toLong().seconds,
            )
    }
}
