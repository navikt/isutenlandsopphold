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
    private val journalforVedtakService: JournalforVedtakService,
    private val journalforHenleggelseService: JournalforHenleggelseService,
) {
    fun hentSoknad(soknadId: UUID): Soknad? = soknadRepository.hentSoknad(soknadId)

    fun hentSoknader(personident: Personident): List<Soknad> = soknadRepository.hentSoknader(personident)

    fun mottaSoknad(soknad: Soknad): LagreMottattSoknadResultat = soknadRepository.lagreMottattSoknad(soknad)

    fun fattVedtak(
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

                val soknadMedVedtak =
                    soknad.fattVedtak(
                        utfall = utfall,
                        fattetAv = fattetAv,
                        now = OffsetDateTime.now(),
                        document = document,
                        begrunnelse = begrunnelse,
                    )

                soknadRepository.lagreVedtak(
                    transaction = transaction,
                    soknadMedVedtak = soknadMedVedtak,
                )
            }
        journalforOgDistribuerVedtakAsync(lagretSoknad)
        return lagretSoknad
    }

    fun henleggSoknad(
        soknadId: UUID,
        henlagtAv: Navident,
        begrunnelse: String,
        document: List<DocumentComponent>,
    ): Soknad {
        val lagretSoknad =
            transactionManager.inTransaction { transaction ->
                val soknad =
                    soknadRepository.hentSoknadForUpdate(
                        transaction = transaction,
                        soknadId = soknadId,
                    ) ?: throw IllegalArgumentException("Søknad med id $soknadId finnes ikke")

                val soknadMedHenleggelse =
                    soknad.henlegg(
                        begrunnelse = begrunnelse,
                        henlagtAv = henlagtAv,
                        now = OffsetDateTime.now(),
                        document = document,
                    )

                soknadRepository.lagreHenleggelse(
                    transaction = transaction,
                    soknadMedHenleggelse = soknadMedHenleggelse,
                )
            }
        journalforOgDistribuerHenleggelseAsync(lagretSoknad)
        return lagretSoknad
    }

    private fun journalforOgDistribuerVedtakAsync(soknadMedVedtak: Soknad) {
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

    private fun journalforOgDistribuerHenleggelseAsync(soknadMedHenleggelse: Soknad) {
        launchAsyncTask {
            try {
                val journalfortSoknad = journalforHenleggelseService.journalforHenleggelse(soknadMedHenleggelse)
                journalforHenleggelseService.distribuerHenleggelse(journalfortSoknad)
            } catch (exception: Exception) {
                log.error(
                    "Feil ved umiddelbar journalføring/distribusjon av henleggelse " +
                        "${soknadMedHenleggelse.henleggelse?.henleggelseId} for søknad ${soknadMedHenleggelse.id}",
                    exception,
                )
            }
        }
    }
}

private val log = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.application.SoknadService")
