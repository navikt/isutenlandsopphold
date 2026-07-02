package no.nav.syfo.utenlandsopphold.infrastructure.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@PublishedApi
internal val logger: Logger =
    LoggerFactory.getLogger(
        "no.nav.syfo.utenlandsopphold.infrastructure.kafka.KafkaTask",
    )

inline fun <reified ConsumerRecordValue> launchKafkaTask(
    applicationState: ApplicationState,
    topic: String,
    consumerProperties: Properties,
    kafkaConsumerService: KafkaConsumerService<ConsumerRecordValue>,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        logger.info("Setting up kafka consumer for ${ConsumerRecordValue::class.java.simpleName}")

        var consecutiveErrors = 0
        while (applicationState.ready) {
            var kafkaConsumer: KafkaConsumer<String, ConsumerRecordValue>? = null
            try {
                kafkaConsumer = KafkaConsumer(consumerProperties)
                kafkaConsumer.subscribe(listOf(topic))
                consecutiveErrors = 0
                pollWithRetry(applicationState, topic, kafkaConsumer, kafkaConsumerService)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                logger.error(
                    "Failed to create or subscribe kafka consumer for topic $topic. Recreating consumer.",
                    ex,
                )
                consecutiveErrors++
                val delayMs = minOf(consecutiveErrors * 2000L, 120_000L)
                if (applicationState.ready) {
                    delay(delayMs.milliseconds)
                }
            } finally {
                kafkaConsumer?.close()
            }
        }
    }
}

@PublishedApi
internal suspend fun <ConsumerRecordValue> pollWithRetry(
    applicationState: ApplicationState,
    topic: String,
    kafkaConsumer: KafkaConsumer<String, ConsumerRecordValue>,
    kafkaConsumerService: KafkaConsumerService<ConsumerRecordValue>,
) {
    var consecutiveErrors = 0
    while (applicationState.ready) {
        try {
            kafkaConsumerService.pollAndProcessRecords(kafkaConsumer)
            consecutiveErrors = 0
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            consecutiveErrors++
            val delayMs = minOf(consecutiveErrors * 2000L, 120_000L)
            logger.error(
                "Exception in kafka consumer for topic $topic (consecutive errors: $consecutiveErrors). Retrying after ${delayMs}ms.",
                ex,
            )
            if (applicationState.ready) {
                delay(delayMs.milliseconds)
            }
        }
    }
}
