package no.nav.syfo.utenlandsopphold.infrastructure.cronjob

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.infrastructure.leaderelection.LeaderElection
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Generisk løkke for periodiske cronjobber: kjører kun på podden som er valgt leder (slik at ikke
 * alle replikaer utfører samme jobb samtidig), venter på at forrige kjøring er ferdig før neste
 * intervall starter, og logger antall vellykkede/feilede delresultater per kjøring.
 */
class CronjobRunner(
    private val applicationState: ApplicationState,
    private val leaderElection: LeaderElection,
) {
    private val log = LoggerFactory.getLogger(CronjobRunner::class.java)

    suspend fun start(cronjob: Cronjob) =
        coroutineScope {
            val cronjobName = cronjob.javaClass.simpleName
            val (initialDelay, intervalDelay) = delays(cronjob)
            log.info(
                "Scheduling start of $cronjobName: {} ms, {} ms",
                StructuredArguments.keyValue("initialDelay", initialDelay),
                StructuredArguments.keyValue("intervalDelay", intervalDelay),
            )
            delay(initialDelay)

            while (applicationState.ready) {
                val job =
                    launch {
                        try {
                            if (leaderElection.isLeader()) {
                                val results = cronjob.run()
                                val (success, failed) = results.partition { it.isSuccess }
                                failed.forEach {
                                    log.error("Exception caught in $cronjobName", it.exceptionOrNull())
                                }
                                if (failed.size + success.size > 0) {
                                    log.info(
                                        "Completed $cronjobName with result: {}, {}",
                                        StructuredArguments.keyValue("failed", failed.size),
                                        StructuredArguments.keyValue("updated", success.size),
                                    )
                                }
                            } else {
                                log.debug("Pod is not leader and will not perform cronjob")
                            }
                        } catch (ex: Exception) {
                            log.error("Exception in $cronjobName. Job will run again after delay.", ex)
                        }
                    }
                delay(intervalDelay)
                if (job.isActive) {
                    log.info("Waiting for job to finish")
                    job.join()
                }
            }
            log.info("Ending $cronjobName due to failed liveness check")
        }

    private fun delays(cronjob: Cronjob): Pair<Long, Long> {
        val initialDelay = Duration.ofMinutes(cronjob.initialDelayMinutes).toMillis()
        val intervalDelay = Duration.ofMinutes(cronjob.intervalDelayMinutes).toMillis()
        return Pair(initialDelay, intervalDelay)
    }
}
