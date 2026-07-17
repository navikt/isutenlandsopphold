package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.JournalforingService.Companion.DEFAULT_FAILED_JP_ID
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Use case (application service) som orkestrerer journalføring av vedtak:
 * henter u-journalførte søknader, genererer PDF, sender til dokarkiv, og
 * oppdaterer vedtaket med journalpost-id og journalføringstidspunkt.
 *
 * Domenet ([Soknad.journalforVedtak]) håndhever invarianten om at et vedtak
 * kun kan journalføres én gang — feiler denne tjenesten på ett vedtak stopper
 * det ikke journalføring av de øvrige.
 *
 * @param freshVedtakGracePeriod Brukes av [journalforVedtak] (den periodiske cronjobben) til å
 * ekskludere nylig fattede vedtak fra spørringen, slik at den ikke journalfører et vedtak som
 * API-laget allerede forsøker å journalføre umiddelbart (se [journalforVedtak] med `soknad`-parameter).
 */
class JournalforVedtakService(
    private val soknadRepository: ISoknadRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
    private val distribusjonService: IDistribusjonService,
    private val freshVedtakGracePeriod: Duration = Duration.ZERO,
) {
    suspend fun journalforVedtak() {
        log.debug("Starter journalføring av ujournalførte vedtak")
        val fattetBefore = Instant.now().minus(freshVedtakGracePeriod.toJavaDuration())
        val soknaderMedIkkeJournalforteVedtak = soknadRepository.getIkkeJournalforteSoknader(fattetBefore)

        soknaderMedIkkeJournalforteVedtak.forEach { soknad ->
            try {
                journalforVedtak(soknad)
                log.info("Vedtak for søknad ${soknad.id} journalført med journalpostId ${soknad.vedtak?.journalpostId?.value}")
            } catch (exception: Exception) {
                log.error("Feil ved journalføring av vedtak for søknad ${soknad.id}", exception)
            }
        }
    }

    /**
     * Journalfører vedtaket for én søknad. Gjenbrukes både av den periodiske cronjobben
     * ([journalforVedtak]) og av API-laget, som forsøker journalføring umiddelbart etter at
     * vedtaket er fattet (se [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.journalforVedtak] håndhever at et vedtak kun journalføres én gang,
     * og dokarkiv dedupliserer på `eksternReferanseId` (vedtakId) dersom denne likevel
     * skulle bli kalt samtidig fra flere steder for samme vedtak.
     */
    suspend fun journalforVedtak(soknad: Soknad) {
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
        log.debug("Starter distribusjon av journalførte, ikke-distribuerte vedtak")
        val soknaderMedIkkeDistribuerteVedtak = soknadRepository.getSoknaderMedIkkeDistribuerteVedtak()

        soknaderMedIkkeDistribuerteVedtak.forEach { soknad ->
            try {
                distribuerVedtak(soknad)
                log.info("Vedtak for søknad ${soknad.id} distribuert med journalpostId ${soknad.vedtak?.journalpostId?.value}")
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

        if (journalpostId.value == DEFAULT_FAILED_JP_ID.value) {
            // Hvis journalpostId er DEFAULT_FAILED_JP_ID, betyr det at journalføringen feilet i dev-gcp, og vi skal ikke forsøke å distribuere dette vedtaket.
            soknadRepository.setVedtakDistribuert(
                vedtakId = vedtak.vedtakId,
                distribuertTidspunkt = Instant.now(),
            )
            return
        }

        val bestillingsId = distribusjonService.distribuer(journalpostId).getOrThrow()

        log.info("Distribusjon av vedtak ${vedtak.vedtakId} for søknad ${soknad.id} bestilt, bestillingsId: $bestillingsId")

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
