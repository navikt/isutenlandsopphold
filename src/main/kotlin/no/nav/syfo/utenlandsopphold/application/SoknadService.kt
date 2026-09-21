package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellGrunn
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.VedtaksUtfall
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

class SoknadService(
    private val transactionManager: TransactionManager,
    private val soknadRepository: ISoknadRepository,
    private val brevService: BrevService,
) {
    fun hentSoknad(soknadId: UUID): Soknad? = soknadRepository.hentSoknad(soknadId)

    fun hentSoknader(personident: Personident): List<Soknad> = soknadRepository.hentSoknader(personident)

    fun mottaSoknad(soknad: Soknad): LagreMottattSoknadResultat = soknadRepository.lagreMottattSoknad(soknad)

    fun fattVedtak(
        soknadId: UUID,
        behandletAv: Navident,
        utfall: VedtaksUtfall,
        innvilgedePerioder: List<Periode>,
        begrunnelse: String?,
        document: List<DocumentComponent>,
    ): Soknad {
        val lagretSoknad =
            behandleOgLagreSoknad(soknadId) { soknad ->
                soknad.fattVedtak(
                    utfall = utfall,
                    innvilgedePerioder = innvilgedePerioder,
                    begrunnelse = begrunnelse,
                    behandletAv = behandletAv,
                    now = OffsetDateTime.now(),
                    document = document,
                )
            }
        journalforOgDistribuerAsync(lagretSoknad)
        return lagretSoknad
    }

    fun henlegg(
        soknadId: UUID,
        behandletAv: Navident,
        document: List<DocumentComponent>,
        begrunnelse: String,
    ): Soknad {
        val lagretSoknad =
            behandleOgLagreSoknad(soknadId) { soknad ->
                soknad.henlegg(
                    behandletAv = behandletAv,
                    now = OffsetDateTime.now(),
                    document = document,
                    begrunnelse = begrunnelse,
                )
            }
        journalforOgDistribuerAsync(lagretSoknad)
        return lagretSoknad
    }

    /**
     * Markerer at søknaden ikke skal realitetsbehandles her. Det sendes ikke brev, så
     * det er ingenting å journalføre eller distribuere.
     */
    fun merkIkkeAktuell(
        soknadId: UUID,
        behandletAv: Navident,
        grunn: IkkeAktuellGrunn,
    ): Soknad =
        behandleOgLagreSoknad(soknadId) { soknad ->
            soknad.merkIkkeAktuell(
                grunn = grunn,
                behandletAv = behandletAv,
                now = OffsetDateTime.now(),
            )
        }

    private fun behandleOgLagreSoknad(
        soknadId: UUID,
        behandle: (Soknad) -> Soknad,
    ): Soknad =
        transactionManager.inTransaction { transaction ->
            val soknad =
                soknadRepository.hentSoknadForUpdate(
                    transaction = transaction,
                    soknadId = soknadId,
                ) ?: throw IllegalArgumentException("Søknad med id $soknadId finnes ikke")

            val behandletSoknad = behandle(soknad)

            soknadRepository.lagreBehandling(
                transaction = transaction,
                behandletSoknad = behandletSoknad,
            )
        }

    private fun journalforOgDistribuerAsync(behandletSoknad: Soknad) {
        launchAsyncTask {
            try {
                val journalfortSoknad = brevService.journalforBrev(behandletSoknad)
                brevService.distribuerBrev(journalfortSoknad)
            } catch (exception: Exception) {
                log.error(
                    "Feil ved umiddelbar journalføring/distribusjon av brev ${behandletSoknad.behandling?.brev?.brevId} for søknad ${behandletSoknad.id}",
                    exception,
                )
            }
        }
    }
}

private val log = LoggerFactory.getLogger("no.nav.syfo.utenlandsopphold.application.SoknadService")
