package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.identhendelse

import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.IdenthendelseService
import no.nav.syfo.utenlandsopphold.infrastructure.cronjob.Cronjob
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Engangsjobb for å flytte søknader lagret på utdatert ident over til gjeldende ident fra PDL.
 * Personidenten som skal håndteres utledes fra de konfigurerte søknad-uuid'ene.
 * Jobben er idempotent: når identen er oppdatert, finnes det ikke lenger noe å flytte.
 */
class OutdatedIdentCronjob(
    private val soknadRepository: ISoknadRepository,
    private val identhendelseService: IdenthendelseService,
    private val soknadIds: List<UUID> = OUTDATED_IDENT_SOKNAD_UUIDS,
    override val initialDelayMinutes: Long = 5,
    override val intervalDelayMinutes: Long = 60 * 24,
) : Cronjob {
    override suspend fun run(): List<Result<Any>> {
        val identer =
            soknadIds
                .mapNotNull { soknadId ->
                    val soknad = soknadRepository.hentSoknad(soknadId)
                    if (soknad == null) {
                        log.warn("Fant ikke søknad med uuid $soknadId")
                    }
                    soknad?.personident
                }.distinct()

        return identer.map { ident ->
            runCatching { identhendelseService.handleOutdatedIdent(ident) }
        }
    }

    companion object {
        // Søknader med utdatert ident: personident som skal håndteres hentes fra disse
        // (trenger bare én søknad per ident som skal håndteres)
        private val OUTDATED_IDENT_SOKNAD_UUIDS: List<UUID> =
            listOf(
                "d0a1036a-d17b-46ea-9c36-ff6f4af07588",
            ).map { UUID.fromString(it) }

        private val log = LoggerFactory.getLogger(OutdatedIdentCronjob::class.java)
    }
}
