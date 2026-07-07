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
    private val vedtakRepository: IVedtakRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
) {
    suspend fun journalforUjournalforteVedtak() {
        val soknaderMedUjournalforteVedtak = vedtakRepository.getUjournalforteSoknader()

        soknaderMedUjournalforteVedtak.forEach { soknad ->
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

        vedtakRepository.setVedtakJournalfort(
            vedtakId = journalfortVedtak.vedtakId,
            journalpostId = journalpostId,
            journalfortTidspunkt = checkNotNull(journalfortVedtak.journalfortTidspunkt),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforVedtakService::class.java)
    }
}
