package no.nav.syfo.utenlandsopphold.infrastructure.leaderelection

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.slf4j.LoggerFactory
import java.net.InetAddress

/**
 * Rød sone: leder-valg avgjør hvilken pod som får kjøre cronjobben. Uten dette
 * ville alle replikaer journalført samme vedtak samtidig og risikert duplikate
 * journalposter i Joark. NAIS eksponerer `ELECTOR_GET_URL` når `leaderElection: true`
 * er satt i nais-manifestet.
 */
class LeaderElection(
    private val httpClient: HttpClient,
    private val config: LeaderElectionConfig,
) {
    suspend fun isLeader(): Boolean =
        try {
            val leader = httpClient.get(config.electorGetUrl).body<LeaderElectionResponse>()
            leader.name == hostname
        } catch (exc: Exception) {
            log.error("Failed to fetch leader from ${config.electorGetUrl}, assuming this pod is not leader", exc)
            false
        }

    companion object {
        private val log = LoggerFactory.getLogger(LeaderElection::class.java)
        private val hostname: String = InetAddress.getLocalHost().hostName
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LeaderElectionResponse(
    val name: String,
)
