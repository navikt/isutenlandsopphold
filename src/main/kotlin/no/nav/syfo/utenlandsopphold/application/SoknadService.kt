package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

class SoknadService(
    private val transactionManager: TransactionManager,
    private val soknadRepository: ISoknadRepository,
    private val journalforBehandlingsutfallService: JournalforBehandlingsutfallService,
) {
    fun hentSoknad(soknadId: UUID): Soknad? = soknadRepository.hentSoknad(soknadId)

    fun hentSoknader(personident: Personident): List<Soknad> = soknadRepository.hentSoknader(personident)

    fun mottaSoknad(soknad: Soknad): LagreMottattSoknadResultat = soknadRepository.lagreMottattSoknad(soknad)

    fun registrerBehandlingsutfall(
        soknadId: UUID,
        fattetAv: Navident,
        utfall: Utfall,
        document: List<DocumentComponent>,
        begrunnelse: String?,
    ): Soknad {
        val lagretSoknad =
            transactionManager.inTransaction { transaction ->
                val soknad =
                    soknadRepository.hentSoknadForUpdate(
                        transaction = transaction,
                        soknadId = soknadId,
                    ) ?: throw IllegalArgumentException("Søknad med id $soknadId finnes ikke")

                val soknadMedBehandlingsutfall =
                    soknad.registrerBehandlingsutfall(
                        utfall = utfall,
                        fattetAv = fattetAv,
                        now = OffsetDateTime.now(),
                        document = document,
                        begrunnelse = begrunnelse,
                    )

                soknadRepository.lagreBehandlingsutfall(
                    transaction = transaction,
                    soknadMedBehandlingsutfall = soknadMedBehandlingsutfall,
                )
            }
        journalforOgDistribuerAsync(lagretSoknad)
        return lagretSoknad
    }

    private fun journalforOgDistribuerAsync(soknadMedBehandlingsutfall: Soknad) {
        launchAsyncTask {
            try {
                val journalfortSoknad =
                    journalforBehandlingsutfallService.journalforBehandlingsutfall(soknadMedBehandlingsutfall)
                journalforBehandlingsutfallService.distribuerBehandlingsutfall(journalfortSoknad)
            } catch (exception: Exception) {
                log.error(
                    "Feil ved umiddelbar journalføring/distribusjon av behandlingsutfall " +
                        "${soknadMedBehandlingsutfall.behandlingsutfall?.behandlingsutfallId} " +
                        "for søknad ${soknadMedBehandlingsutfall.id}",
                    exception,
                )
            }
        }
    }
}

private val log = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.application.SoknadService")
