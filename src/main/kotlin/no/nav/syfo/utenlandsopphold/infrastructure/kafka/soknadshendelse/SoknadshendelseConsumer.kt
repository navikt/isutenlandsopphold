package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadshendelse

import io.micrometer.core.instrument.Counter
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.domain.ManglerSoktePerioderException
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.KafkaConsumerService
import no.nav.syfo.utenlandsopphold.infrastructure.metric.METRICS_NS
import no.nav.syfo.utenlandsopphold.infrastructure.metric.METRICS_REGISTRY
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger(SoknadshendelseConsumer::class.java)

private val soknadOppdatertCounter: Counter =
    Counter
        .builder("${METRICS_NS}_soknad_oppdatert_count")
        .description("Antall mottatte søknader fra Kafka som allerede var lagret og ble oppdatert")
        .register(METRICS_REGISTRY)

class SoknadshendelseConsumer(
    private val soknadService: SoknadService,
) : KafkaConsumerService<KafkaSykepengesoknadDTO> {
    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaSykepengesoknadDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(records)
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, KafkaSykepengesoknadDTO>) {
        records
            .mapNotNull { it.value() }
            .filter { it.type == KafkaSoknadstypeDTO.OPPHOLD_UTLAND && it.status == KafkaSoknadstatusDTO.SENDT }
            .mapNotNull { kafkaSoknad ->
                try {
                    kafkaSoknad.toSoknad()
                } catch (_: ManglerSoktePerioderException) {
                    logger.error("Søknad med id ${kafkaSoknad.id} har ikke søkte perioder for utenlandsopphold.")
                    return@mapNotNull null
                } catch (exception: Exception) {
                    logger.error(
                        "Feil ved mapping av søknad med id ${kafkaSoknad.id} til Soknad: ${exception.message}",
                        exception,
                    )
                    throw exception
                }
            }.forEach { soknad ->
                if (soknadService.mottaSoknad(soknad = soknad) == LagreMottattSoknadResultat.OPPDATERT) {
                    logger.warn(
                        "Mottok søknad som allerede er lagret fra før, oppdaterer. eksternId=${soknad.eksternId}",
                    )
                    soknadOppdatertCounter.increment()
                }
            }
    }
}
