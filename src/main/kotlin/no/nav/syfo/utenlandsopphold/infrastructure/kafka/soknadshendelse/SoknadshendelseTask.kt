package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadshendelse

import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.launchKafkaTask
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Deserializer

const val FLEX_SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"

fun launchKafkaTaskSoknadshendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    soknadshendelseConsumer: SoknadshendelseConsumer,
) {
    val consumerProperties =
        kafkaAivenConsumerConfig<KafkaSykepengesoknadDeserializer>(
            kafkaEnvironment = kafkaEnvironment,
        ).apply {
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
        }

    launchKafkaTask(
        applicationState = applicationState,
        topic = FLEX_SYKEPENGESOKNAD_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = soknadshendelseConsumer,
    )
}

class KafkaSykepengesoknadDeserializer : Deserializer<KafkaSykepengesoknadDTO> {
    private val mapper = configuredJacksonMapper()

    override fun deserialize(
        topic: String,
        data: ByteArray,
    ): KafkaSykepengesoknadDTO = mapper.readValue(data, KafkaSykepengesoknadDTO::class.java)
}
