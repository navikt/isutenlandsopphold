package no.nav.syfo.utenlandsopphold.infrastructure.kafka

import no.nav.syfo.utenlandsopphold.Environment
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadshendelse.SoknadshendelseConsumer
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadshendelse.launchKafkaTaskSoknadshendelse
import no.nav.syfo.utenlandsopphold.isKafkaSoknadConsumerEnabled

fun launchKafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
    soknadService: SoknadService,
) {
    val soknadshendelseConsumer =
        SoknadshendelseConsumer(
            soknadService = soknadService,
        )

    if (isKafkaSoknadConsumerEnabled) {
        launchKafkaTaskSoknadshendelse(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            soknadshendelseConsumer = soknadshendelseConsumer,
        )
    }
}
