package no.nav.syfo.utenlandsopphold.infrastructure.kafka

import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold")

inline fun <reified ConsumerRecordValue> launchKafkaConsumer(
    applicationState: ApplicationState,
    topic: String,
    consumerProperties: Properties,
    kafkaConsumerService: KafkaConsumerService<ConsumerRecordValue>,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        log.info("Setting up kafka consumer for ${ConsumerRecordValue::class.java.simpleName}")

        val kafkaConsumer = KafkaConsumer<String, ConsumerRecordValue>(consumerProperties)

        while (applicationState.ready) {
            if (kafkaConsumer.subscription().isEmpty()) {
                kafkaConsumer.subscribe(listOf(topic))
            }
            kafkaConsumerService.pollAndProcessRecords(kafkaConsumer)
        }
    }
}
