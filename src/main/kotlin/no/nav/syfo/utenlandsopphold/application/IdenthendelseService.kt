package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val soknadRepository: ISoknadRepository,
) {
    fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size <= 1) return

        val aktivIdent = identhendelse.getActivePersonident()
        if (aktivIdent == null) {
            log.warn("Identhendelse: Mangler gjeldende folkeregisterident fra PDL")
            return
        }

        val inaktiveIdenter = identhendelse.getInactivePersonidenter()
        if (inaktiveIdenter.isEmpty() || !soknadRepository.finnesSoknaderMedPersonident(inaktiveIdenter)) return

        val oppdaterteSoknader = soknadRepository.oppdaterPersonident(aktiv = aktivIdent, inaktive = inaktiveIdenter)
        if (oppdaterteSoknader.isNotEmpty()) {
            log.info("Identhendelse: Oppdaterte personident på ${oppdaterteSoknader.size} søknader: $oppdaterteSoknader")
            COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(oppdaterteSoknader.size.toDouble())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(IdenthendelseService::class.java)
    }
}
