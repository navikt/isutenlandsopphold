package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val soknadRepository: ISoknadRepository,
    private val pdlClient: IPdlClient,
) {
    suspend fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size <= 1) return

        val aktivIdent = identhendelse.getActivePersonident()
        if (aktivIdent == null) {
            log.warn("Identhendelse: Mangler gjeldende folkeregisterident fra PDL")
            return
        }

        val inaktiveIdenter = identhendelse.getInactivePersonidenter()
        if (inaktiveIdenter.isEmpty() || !soknadRepository.finnesSoknaderMedPersonident(inaktiveIdenter)) return

        if (!erAktivIdentIPdl(aktivIdent)) return

        val antallOppdatert = soknadRepository.oppdaterPersonident(aktiv = aktivIdent, inaktive = inaktiveIdenter)
        if (antallOppdatert > 0) {
            log.info("Identhendelse: Oppdaterte personident på $antallOppdatert søknader basert på identhendelse fra PDL")
            COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(antallOppdatert.toDouble())
        }
    }

    // Erfaringer fra andre team tilsier at vi bør dobbeltsjekke at PDL er oppdatert før vi endrer data.
    // Kaster hvis PDL ikke (ennå) har ident som aktiv, slik at konsumenten prøver igjen.
    // Er identen historisk i PDL er hendelsen utdatert, og en senere hendelse tar seg av oppdateringen.
    private suspend fun erAktivIdentIPdl(nyIdent: Personident): Boolean {
        val pdlIdenter = pdlClient.hentIdenter(nyIdent) ?: throw IllegalStateException("Fant ingen identer fra PDL")
        return when {
            pdlIdenter.any { it.ident == nyIdent.value && !it.historisk } -> true

            pdlIdenter.any { it.ident == nyIdent.value && it.historisk } -> {
                log.warn("Identhendelse: Ny ident er historisk i PDL, hopper over utdatert hendelse")
                false
            }

            else -> throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(IdenthendelseService::class.java)
    }
}
