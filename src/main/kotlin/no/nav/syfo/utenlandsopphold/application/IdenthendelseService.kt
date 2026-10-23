package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import org.slf4j.LoggerFactory
import java.util.UUID

class IdenthendelseService(
    private val soknadRepository: ISoknadRepository,
    private val pdlClient: IPdlClient,
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

    /**
     * Flytter søknader lagret på en utdatert ident over til gjeldende ident fra PDL.
     * Returnerer uuid-ene til søknadene som ble oppdatert.
     */
    suspend fun handleOutdatedIdent(ident: Personident): List<UUID> {
        val aktivIdent =
            pdlClient.getAktivIdent(ident)
                ?: throw RuntimeException("Fant ingen aktiv ident fra PDL")
        if (aktivIdent == ident) {
            log.info("Ident er allerede aktiv ident i PDL, ingenting å oppdatere")
            return emptyList()
        }
        if (!soknadRepository.finnesSoknaderMedPersonident(listOf(ident))) {
            log.info("Fant ingen søknader på utdatert ident, ingenting å oppdatere")
            return emptyList()
        }
        // Dobbeltsjekker at ny ident faktisk er gjeldende ident i PDL før vi gjør endringer
        val aktivIdentForNyIdent = pdlClient.getAktivIdent(aktivIdent)
        if (aktivIdentForNyIdent != aktivIdent) {
            throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
        }

        val oppdaterteSoknader = soknadRepository.oppdaterPersonident(aktiv = aktivIdent, inaktive = listOf(ident))
        log.info("Utdatert ident: Oppdaterte personident på ${oppdaterteSoknader.size} søknader: $oppdaterteSoknader")
        return oppdaterteSoknader
    }

    companion object {
        private val log = LoggerFactory.getLogger(IdenthendelseService::class.java)
    }
}
