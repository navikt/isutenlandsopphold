package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class SoknadService(
    private val transactionManager: TransactionManager,
    private val soknadRepository: ISoknadRepository,
    private val journalforVedtakService: JournalforVedtakService,
) {
    fun hentSoknad(soknadId: UUID): Soknad? = soknadRepository.hentSoknad(soknadId)

    fun hentSoknader(personident: Personident): List<Soknad> = soknadRepository.hentSoknader(personident)

    fun mottaSoknad(soknad: Soknad): LagreMottattSoknadResultat = soknadRepository.lagreMottattSoknad(soknad)

    fun fattVedtak(
        soknadId: UUID,
        fattetAv: Navident,
        utfall: Utfall,
        document: List<DocumentComponent>,
    ): Soknad {
        val lagretSoknad =
            transactionManager.inTransaction { transaction ->
                val soknad =
                    soknadRepository.hentSoknadForUpdate(
                        transaction = transaction,
                        soknadId = soknadId,
                    ) ?: throw IllegalArgumentException("Søknad med id $soknadId finnes ikke")

                val soknadMedVedtak =
                    soknad.fattVedtak(
                        utfall = utfall,
                        fattetAv = fattetAv,
                        now = Instant.now(),
                        document = document,
                    )

                soknadRepository.lagreVedtak(
                    transaction = transaction,
                    soknadMedVedtak = soknadMedVedtak,
                )
            }
        journalforOgDistribuerAsync(lagretSoknad)
        return lagretSoknad
    }

    private fun journalforOgDistribuerAsync(soknadMedVedtak: Soknad) {
        launchAsyncTask {
            try {
                val journalfortSoknad = journalforVedtakService.journalforVedtak(soknadMedVedtak)
                journalforVedtakService.distribuerVedtak(journalfortSoknad)
            } catch (exception: Exception) {
                log.error(
                    "Feil ved umiddelbar journalføring/distribusjon av vedtak ${soknadMedVedtak.vedtak?.vedtakId} for søknad ${soknadMedVedtak.id}",
                    exception,
                )
            }
        }
    }
}

private val log = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.application.SoknadService")
