package no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.utenlandsopphold.application.IdenthendelseService
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.KafkaConsumerService
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger(IdenthendelseConsumer::class.java)

class IdenthendelseConsumer(
    private val identhendelseService: IdenthendelseService,
) : KafkaConsumerService<GenericRecord> {
    override val pollDurationInMillis: Long = 10_000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, GenericRecord>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            records.forEach { record ->
                val value = record.value()
                if (value != null) {
                    runBlocking { identhendelseService.handleIdenthendelse(value.toKafkaIdenthendelseDTO()) }
                } else {
                    logger.warn("Identhendelse: Value of ConsumerRecord from topic $PDL_AKTOR_TOPIC is null, probably due to a tombstone.")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE.increment()
                }
            }
            kafkaConsumer.commitSync()
        }
    }
}
