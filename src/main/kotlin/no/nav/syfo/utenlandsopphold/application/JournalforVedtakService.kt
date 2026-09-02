package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.Brevkode
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Behandlingsutfall
import no.nav.syfo.utenlandsopphold.domain.Henleggelse
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.JournalforingService.Companion.DEFAULT_FAILED_JP_ID
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.UtenlandsoppholdBrevkode
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Use case (application service) som orkestrerer journalføring av behandlingsutfall (vedtak
 * eller henleggelse): henter u-journalførte søknader, genererer PDF, sender til dokarkiv, og
 * oppdaterer behandlingsutfallet med journalpost-id og journalføringstidspunkt.
 *
 * Domenet ([Soknad.journalforBehandlingsutfall]) håndhever invarianten om at et
 * behandlingsutfall kun kan journalføres én gang — feiler denne tjenesten på ett
 * behandlingsutfall stopper det ikke journalføring av de øvrige.
 *
 * @param freshVedtakGracePeriod Brukes av [journalforVedtak] (den periodiske cronjobben) til å
 * ekskludere nylig fattede behandlingsutfall fra spørringen, slik at den ikke journalfører et
 * behandlingsutfall som API-laget allerede forsøker å journalføre umiddelbart (se
 * [journalforVedtak] med `soknad`-parameter).
 */
class JournalforVedtakService(
    private val soknadRepository: ISoknadRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
    private val distribusjonService: IDistribusjonService,
    private val freshVedtakGracePeriod: Duration = Duration.ZERO,
) {
    suspend fun journalforVedtak(): List<Result<Soknad>> {
        log.debug("Starter journalføring av ujournalførte behandlingsutfall")
        val fattetBefore = OffsetDateTime.now().minus(freshVedtakGracePeriod.toJavaDuration())
        val soknaderMedIkkeJournalforteVedtak = soknadRepository.getIkkeJournalforteSoknader(fattetBefore)

        return soknaderMedIkkeJournalforteVedtak.map { soknad ->
            runCatching { journalforVedtak(soknad) }
                .onFailure { log.error("Feil ved journalføring av behandlingsutfall for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Journalfører behandlingsutfallet for én søknad. Gjenbrukes både av den periodiske
     * cronjobben ([journalforVedtak]) og av API-laget, som forsøker journalføring umiddelbart
     * etter at behandlingsutfallet er fattet (se
     * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.journalforBehandlingsutfall] håndhever at et behandlingsutfall kun
     * journalføres én gang, og dokarkiv dedupliserer på `eksternReferanseId`
     * (behandlingsutfallId) dersom denne likevel skulle bli kalt samtidig fra flere steder for
     * samme behandlingsutfall.
     *
     * @return den oppdaterte [Soknad]-en med det journalførte behandlingsutfallet, slik at
     * API-laget kan kjede umiddelbar distribusjon ([distribuerVedtak]) rett etter.
     */
    suspend fun journalforVedtak(soknad: Soknad): Soknad {
        val behandlingsutfall =
            checkNotNull(soknad.behandlingsutfall) {
                "Søknad ${soknad.id} har ikke fattet behandlingsutfall, kan ikke journalføre"
            }

        val mottakerNavn = personInfoClient.getNavn(soknad.personident)

        val pdf =
            pdfClient.createPdf(
                mottakerFodselsnummer = soknad.personident,
                mottakerNavn = mottakerNavn,
                behandlingsutfall = behandlingsutfall,
            )

        val journalpostId: JournalpostId =
            journalforingService
                .journalfor(
                    personident = soknad.personident,
                    pdf = pdf,
                    eksternReferanseId = behandlingsutfall.behandlingsutfallId.toString(),
                    brevkode = behandlingsutfall.brevkode(),
                    tittel = behandlingsutfall.journalpostTittel(),
                ).getOrThrow()

        val journalfortTidspunkt = OffsetDateTime.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val journalfortSoknad = soknad.journalforBehandlingsutfall(journalpostId, journalfortTidspunkt)
        val journalfortBehandlingsutfall = checkNotNull(journalfortSoknad.behandlingsutfall)

        soknadRepository.setBehandlingsutfallJournalfort(
            behandlingsutfallId = journalfortBehandlingsutfall.behandlingsutfallId,
            journalpostId = journalpostId,
            journalfortTidspunkt = checkNotNull(journalfortBehandlingsutfall.journalfortTidspunkt),
        )
        log.info(
            "Behandlingsutfall ${journalfortBehandlingsutfall.behandlingsutfallId} for søknad ${soknad.id} " +
                "journalført med journalpostId ${journalfortBehandlingsutfall.journalpostId?.value}",
        )
        return journalfortSoknad
    }

    /**
     * Bestiller distribusjon (utsending til mottaker) av behandlingsutfall som er
     * journalført, men ennå ikke distribuert. Idempotent pass: kjøres på nytt ved neste
     * intervall for behandlingsutfall som feilet, siden dokdistfordeling selv behandler
     * gjentatte bestillinger på samme journalpost som suksess (409 Conflict).
     */
    suspend fun distribuerVedtak(): List<Result<Unit>> {
        log.debug("Starter distribusjon av journalførte, ikke-distribuerte behandlingsutfall")
        val fattetBefore = OffsetDateTime.now().minus(freshVedtakGracePeriod.toJavaDuration())
        val soknaderMedIkkeDistribuerteVedtak =
            soknadRepository.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore)

        return soknaderMedIkkeDistribuerteVedtak.map { soknad ->
            runCatching { distribuerVedtak(soknad) }
                .onFailure { log.error("Feil ved distribusjon av behandlingsutfall for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Distribuerer (bestiller utsending av) behandlingsutfallet for én søknad. Gjenbrukes
     * både av den periodiske cronjobben ([distribuerVedtak]) og av API-laget, som forsøker
     * distribusjon umiddelbart etter en vellykket umiddelbar journalføring (se
     * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.distribuerBehandlingsutfall] håndhever at et behandlingsutfall må
     * være journalført og kun distribueres én gang, og dokdistfordeling behandler gjentatte
     * bestillinger på samme journalpost som suksess (409 Conflict) dersom denne likevel
     * skulle bli kalt samtidig fra flere steder for samme behandlingsutfall.
     */
    suspend fun distribuerVedtak(soknad: Soknad) {
        val behandlingsutfall =
            checkNotNull(soknad.behandlingsutfall) {
                "Søknad ${soknad.id} har ikke fattet behandlingsutfall, kan ikke distribuere"
            }

        val journalpostId =
            checkNotNull(behandlingsutfall.journalpostId) {
                "Behandlingsutfall ${behandlingsutfall.behandlingsutfallId} er ikke journalført, kan ikke distribuere"
            }

        if (journalpostId.value == DEFAULT_FAILED_JP_ID.value) {
            // Hvis journalpostId er DEFAULT_FAILED_JP_ID, betyr det at journalføringen feilet i dev-gcp, og vi skal ikke forsøke å distribuere dette behandlingsutfallet.
            soknadRepository.setBehandlingsutfallDistribuert(
                behandlingsutfallId = behandlingsutfall.behandlingsutfallId,
                distribuertTidspunkt = OffsetDateTime.now(),
            )
            return
        }

        val bestillingsId =
            distribusjonService.distribuer(journalpostId, behandlingsutfall.distribusjonstype()).getOrThrow()

        log.info(
            "Distribusjon av behandlingsutfall ${behandlingsutfall.behandlingsutfallId} for søknad ${soknad.id} " +
                "bestilt, bestillingsId: $bestillingsId",
        )

        val distribuertTidspunkt = OffsetDateTime.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val distribuertSoknad = soknad.distribuerBehandlingsutfall(distribuertTidspunkt)
        val distribuertBehandlingsutfall = checkNotNull(distribuertSoknad.behandlingsutfall)

        soknadRepository.setBehandlingsutfallDistribuert(
            behandlingsutfallId = distribuertBehandlingsutfall.behandlingsutfallId,
            distribuertTidspunkt = checkNotNull(distribuertBehandlingsutfall.distribuertTidspunkt),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforVedtakService::class.java)
    }
}

private fun Behandlingsutfall.brevkode(): Brevkode =
    when (this) {
        is Vedtak -> UtenlandsoppholdBrevkode.VEDTAK
        is Henleggelse -> UtenlandsoppholdBrevkode.HENLEGGELSE
    }

private fun Behandlingsutfall.journalpostTittel(): String =
    when (this) {
        is Vedtak -> "Vedtak om utenlandsopphold"
        is Henleggelse -> "Henleggelse av søknad om utenlandsopphold"
    }

private fun Behandlingsutfall.distribusjonstype(): Distribusjonstype =
    when (this) {
        is Vedtak -> Distribusjonstype.VEDTAK
        is Henleggelse -> Distribusjonstype.VIKTIG
    }
