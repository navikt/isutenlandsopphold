package no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse

import io.micrometer.core.instrument.Counter
import no.nav.syfo.utenlandsopphold.infrastructure.metric.METRICS_NS
import no.nav.syfo.utenlandsopphold.infrastructure.metric.METRICS_REGISTRY

val COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES: Counter =
    Counter
        .builder("${METRICS_NS}_kafka_consumer_pdl_aktor_updates")
        .description("Antall søknader som har fått oppdatert personident basert på identhendelse fra PDL")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE: Counter =
    Counter
        .builder("${METRICS_NS}_kafka_consumer_pdl_aktor_tombstone")
        .description("Antall tombstones mottatt fra pdl.aktor-v2")
        .register(METRICS_REGISTRY)
