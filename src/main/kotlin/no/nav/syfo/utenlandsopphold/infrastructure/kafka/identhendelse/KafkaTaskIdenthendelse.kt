package no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.launchKafkaTask
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"

fun launchKafkaTaskIdenthendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    identhendelseConsumer: IdenthendelseConsumer,
) {
    val consumerProperties =
        kafkaAivenConsumerConfig<KafkaAvroDeserializer>(kafkaEnvironment = kafkaEnvironment).apply {
            this[ConsumerConfig.GROUP_ID_CONFIG] = "isutenlandsopphold-identhendelse-v1"
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
            this[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = kafkaEnvironment.aivenSchemaRegistryUrl
            this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = false
            this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] =
                "${kafkaEnvironment.aivenRegistryUser}:${kafkaEnvironment.aivenRegistryPassword}"
            this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
        }

    launchKafkaTask<GenericRecord>(
        applicationState = applicationState,
        topic = PDL_AKTOR_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = identhendelseConsumer,
    )
}
