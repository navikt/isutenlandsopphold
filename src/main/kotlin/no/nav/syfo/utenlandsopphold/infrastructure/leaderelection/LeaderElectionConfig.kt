package no.nav.syfo.utenlandsopphold.infrastructure.leaderelection

import no.nav.syfo.utenlandsopphold.getEnvVar

/**
 * Konfigurasjon for [LeaderElection].
 *
 * @param electorGetUrl URL NAIS injiserer når `leaderElection: true` er satt i nais-manifestet.
 * Brukes til å slå opp hvilken pod som er valgt leder.
 */
data class LeaderElectionConfig(
    val electorGetUrl: String,
) {
    companion object {
        /**
         * Leser [LeaderElectionConfig] fra NAIS-injisert miljøvariabel `ELECTOR_GET_URL`.
         */
        fun fromEnv(): LeaderElectionConfig = LeaderElectionConfig(electorGetUrl = getEnvVar("ELECTOR_GET_URL"))
    }
}
