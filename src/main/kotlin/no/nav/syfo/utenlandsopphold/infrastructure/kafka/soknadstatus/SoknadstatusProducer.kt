package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus

import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.application.ISoknadstatusProducer
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.kafkaAivenProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer

const val UTENLANDSOPPHOLD_SOKNAD_STATUS_TOPIC = "teamsykefravr.utenlandsopphold-soknad-status"

fun kafkaSoknadstatusProducer(kafkaEnvironment: KafkaEnvironment): Producer<String, SoknadstatusRecord> =
    KafkaProducer(kafkaAivenProducerConfig<KafkaSoknadstatusRecordSerializer>(kafkaEnvironment))

class SoknadstatusProducer(
    private val kafkaProducer: Producer<String, SoknadstatusRecord>,
) : ISoknadstatusProducer {
    override fun publiser(record: SoknadstatusRecord): Result<Unit> =
        runCatching {
            kafkaProducer
                .send(ProducerRecord(UTENLANDSOPPHOLD_SOKNAD_STATUS_TOPIC, record.uuid.toString(), record))
                .get()
            Unit
        }
}

class KafkaSoknadstatusRecordSerializer : Serializer<SoknadstatusRecord> {
    private val mapper = configuredJacksonMapper()

    override fun serialize(
        topic: String,
        data: SoknadstatusRecord,
    ): ByteArray = mapper.writeValueAsBytes(data)
}
