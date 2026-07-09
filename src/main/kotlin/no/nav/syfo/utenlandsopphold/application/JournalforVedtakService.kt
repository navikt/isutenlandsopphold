package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Soknad
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Use case (application service) som orkestrerer journalføring av vedtak:
 * henter u-journalførte søknader, genererer PDF, sender til dokarkiv, og
 * oppdaterer vedtaket med journalpost-id og journalføringstidspunkt.
 *
 * Domenet ([Soknad.journalforVedtak]) håndhever invarianten om at et vedtak
 * kun kan journalføres én gang — feiler denne tjenesten på ett vedtak stopper
 * det ikke journalføring av de øvrige.
 */
class JournalforVedtakService(
    private val soknadRepository: ISoknadRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
    private val distribusjonService: IDistribusjonService,
) {
    suspend fun journalforVedtak() {
        log.info("Starter journalføring av u-journalførte vedtak")
        val soknaderMedIkkeJournalforteVedtak = soknadRepository.getIkkeJournalforteSoknader()

        soknaderMedIkkeJournalforteVedtak.forEach { soknad ->
            try {
                journalforVedtak(soknad)
            } catch (exception: Exception) {
                log.error("Feil ved journalføring av vedtak for søknad ${soknad.id}", exception)
            }
        }
    }

    private suspend fun journalforVedtak(soknad: Soknad) {
        val vedtak =
            checkNotNull(soknad.vedtak) {
                "Søknad ${soknad.id} har ikke fattet vedtak, kan ikke journalføre"
            }

        val mottakerNavn = personInfoClient.getNavn(soknad.personident)

        val pdf =
            pdfClient.createVedtakPdf(
                mottakerFodselsnummer = soknad.personident,
                mottakerNavn = mottakerNavn,
                documentComponents = vedtak.document,
            )

        val journalpostId: JournalpostId =
            journalforingService
                .journalfor(
                    personident = soknad.personident,
                    pdf = pdf,
                    eksternReferanseId = vedtak.vedtakId.toString(),
                ).getOrThrow()

        val journalfortTidspunkt = Instant.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val journalfortSoknad = soknad.journalforVedtak(journalpostId, journalfortTidspunkt)
        val journalfortVedtak = checkNotNull(journalfortSoknad.vedtak)

        soknadRepository.setVedtakJournalfort(
            vedtakId = journalfortVedtak.vedtakId,
            journalpostId = journalpostId,
            journalfortTidspunkt = checkNotNull(journalfortVedtak.journalfortTidspunkt),
        )
    }

    /**
     * Bestiller distribusjon (utsending til mottaker) av vedtak som er journalført, men ennå
     * ikke distribuert. Idempotent pass: kjøres på nytt ved neste intervall for vedtak som
     * feilet, siden dokdistfordeling selv behandler gjentatte bestillinger på samme
     * journalpost som suksess (409 Conflict).
     */
    suspend fun distribuerVedtak() {
        log.info("Starter distribusjon av journalførte, ikke-distribuerte vedtak")
        val soknaderMedIkkeDistribuerteVedtak = soknadRepository.getIkkeDistribuerteSoknader()

        soknaderMedIkkeDistribuerteVedtak.forEach { soknad ->
            try {
                distribuerVedtak(soknad)
            } catch (exception: Exception) {
                log.error("Feil ved distribusjon av vedtak for søknad ${soknad.id}", exception)
            }
        }
    }

    private suspend fun distribuerVedtak(soknad: Soknad) {
        val vedtak =
            checkNotNull(soknad.vedtak) {
                "Søknad ${soknad.id} har ikke fattet vedtak, kan ikke distribuere"
            }

        val journalpostId =
            checkNotNull(vedtak.journalpostId) {
                "Vedtak ${vedtak.vedtakId} er ikke journalført, kan ikke distribuere"
            }

        distribusjonService.distribuer(journalpostId).getOrThrow()

        val distribuertTidspunkt = Instant.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val distribuertSoknad = soknad.distribuerVedtak(distribuertTidspunkt)
        val distribuertVedtak = checkNotNull(distribuertSoknad.vedtak)

        soknadRepository.setVedtakDistribuert(
            vedtakId = distribuertVedtak.vedtakId,
            distribuertTidspunkt = checkNotNull(distribuertVedtak.distribuertTidspunkt),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforVedtakService::class.java)
    }
}
