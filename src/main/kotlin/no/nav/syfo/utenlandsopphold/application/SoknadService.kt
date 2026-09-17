package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.dokumentId
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

class SoknadService(
    private val transactionManager: TransactionManager,
    private val soknadRepository: ISoknadRepository,
    private val dokumentService: DokumentService,
) {
    fun hentSoknad(soknadId: UUID): Soknad? = soknadRepository.hentSoknad(soknadId)

    fun hentSoknader(personident: Personident): List<Soknad> = soknadRepository.hentSoknader(personident)

    fun mottaSoknad(soknad: Soknad): LagreMottattSoknadResultat = soknadRepository.lagreMottattSoknad(soknad)

    fun behandleSoknad(
        soknadId: UUID,
        behandletAv: Navident,
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

                val behandletSoknad =
                    soknad.behandle(
                        utfall = utfall,
                        behandletAv = behandletAv,
                        now = OffsetDateTime.now(),
                        document = document,
                        begrunnelse = begrunnelse,
                    )

                soknadRepository.lagreBehandling(
                    transaction = transaction,
                    behandletSoknad = behandletSoknad,
                )
            }
        journalforOgDistribuerAsync(lagretSoknad)
        return lagretSoknad
    }

    private fun journalforOgDistribuerAsync(behandletSoknad: Soknad) {
        launchAsyncTask {
            try {
                val journalfortSoknad = dokumentService.journalforDokument(behandletSoknad)
                dokumentService.distribuerDokument(journalfortSoknad)
            } catch (exception: Exception) {
                log.error(
                    "Feil ved umiddelbar journalføring/distribusjon av dokument ${behandletSoknad.behandling?.dokument?.dokumentId} for søknad ${behandletSoknad.id}",
                    exception,
                )
            }
        }
    }
}

private val log = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.application.SoknadService")
