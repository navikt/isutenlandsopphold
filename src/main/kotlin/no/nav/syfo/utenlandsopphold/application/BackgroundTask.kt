package no.nav.syfo.utenlandsopphold.application

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.application.BackgroundTask")

@OptIn(DelicateCoroutinesApi::class)
fun launchBackgroundTask(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit,
): Job =
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (ex: Exception) {
            logger.error("Uncaught exception received while launching background task. Terminating application.", ex)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

/**
 * Starter en enkeltstående (one-shot), fire-and-forget bakgrunnsoppgave, f.eks. et umiddelbart
 * forsøk på journalføring rett etter at et vedtak er fattet via API-et.
 */
@OptIn(DelicateCoroutinesApi::class)
fun launchAsyncTask(action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (ex: Exception) {
            logger.error("Uncaught exception in async task, ignoring", ex)
        }
    }

/*
Use Dispatchers.Unbounded to allow unlimited number of coroutines to be dispatched. Without this
only a few will be allowed simultaneously (depending on the number of available cores) which may result
in cronjobs or Kafka-consumers not starting as intended.
*/
val Dispatchers.Unbounded get() = UnboundedDispatcher.unboundedDispatcher

class UnboundedDispatcher private constructor() : CoroutineDispatcher() {
    companion object {
        val unboundedDispatcher = UnboundedDispatcher()
    }

    private val threadPool = Executors.newCachedThreadPool()
    private val dispatcher = threadPool.asCoroutineDispatcher()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        dispatcher.dispatch(context, block)
    }
}
