package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.JournalforingService.Companion.DEFAULT_FAILED_JP_ID
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Use case (application service) som orkestrerer journalføring av behandlingsutfall:
 * henter u-journalførte søknader, genererer PDF, sender til dokarkiv, og
 * oppdaterer behandlingsutfallet med journalpost-id og journalføringstidspunkt.
 *
 * Gjelder alle utfall, også de som ikke er vedtak (f.eks. henleggelse).
 *
 * Domenet ([Soknad.journalforBehandlingsutfall]) håndhever invarianten om at et behandlingsutfall
 * kun kan journalføres én gang — feiler denne tjenesten på ett behandlingsutfall stopper
 * det ikke journalføring av de øvrige.
 *
 * @param freshBehandlingsutfallGracePeriod Brukes av [journalforBehandlingsutfall] (den periodiske
 * cronjobben) til å ekskludere nylig registrerte behandlingsutfall fra spørringen, slik at den ikke
 * journalfører et behandlingsutfall som API-laget allerede forsøker å journalføre umiddelbart
 * (se [journalforBehandlingsutfall] med `soknad`-parameter).
 */
class JournalforBehandlingsutfallService(
    private val soknadRepository: ISoknadRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
    private val distribusjonService: IDistribusjonService,
    private val freshBehandlingsutfallGracePeriod: Duration = Duration.ZERO,
) {
    suspend fun journalforBehandlingsutfall(): List<Result<Soknad>> {
        log.debug("Starter journalføring av ujournalførte behandlingsutfall")
        val fattetBefore = OffsetDateTime.now().minus(freshBehandlingsutfallGracePeriod.toJavaDuration())
        val soknaderMedIkkeJournalforteBehandlingsutfall = soknadRepository.getIkkeJournalforteSoknader(fattetBefore)

        return soknaderMedIkkeJournalforteBehandlingsutfall.map { soknad ->
            runCatching { journalforBehandlingsutfall(soknad) }
                .onFailure { log.error("Feil ved journalføring av behandlingsutfall for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Journalfører behandlingsutfallet for én søknad. Gjenbrukes både av den periodiske cronjobben
     * ([journalforBehandlingsutfall]) og av API-laget, som forsøker journalføring umiddelbart etter at
     * behandlingsutfallet er registrert (se [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.journalforBehandlingsutfall] håndhever at et behandlingsutfall kun journalføres
     * én gang, og dokarkiv dedupliserer på `eksternReferanseId` (behandlingsutfallId) dersom denne likevel
     * skulle bli kalt samtidig fra flere steder for samme behandlingsutfall.
     *
     * @return den oppdaterte [Soknad]-en med det journalførte behandlingsutfallet, slik at API-laget kan
     * kjede umiddelbar distribusjon ([distribuerBehandlingsutfall]) rett etter.
     */
    suspend fun journalforBehandlingsutfall(soknad: Soknad): Soknad {
        val behandlingsutfall =
            checkNotNull(soknad.behandlingsutfall) {
                "Søknad ${soknad.id} er ikke ferdigbehandlet, kan ikke journalføre"
            }

        val mottakerNavn = personInfoClient.getNavn(soknad.personident)

        val pdf =
            pdfClient.createBehandlingsutfallPdf(
                mottakerFodselsnummer = soknad.personident,
                mottakerNavn = mottakerNavn,
                utfall = behandlingsutfall.utfall,
                documentComponents = behandlingsutfall.document,
            )

        val journalpostId: JournalpostId =
            journalforingService
                .journalfor(
                    personident = soknad.personident,
                    pdf = pdf,
                    eksternReferanseId = behandlingsutfall.behandlingsutfallId.toString(),
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
     * Bestiller distribusjon (utsending til mottaker) av behandlingsutfall som er journalført, men ennå
     * ikke distribuert. Idempotent pass: kjøres på nytt ved neste intervall for behandlingsutfall som
     * feilet, siden dokdistfordeling selv behandler gjentatte bestillinger på samme
     * journalpost som suksess (409 Conflict).
     */
    suspend fun distribuerBehandlingsutfall(): List<Result<Unit>> {
        log.debug("Starter distribusjon av journalførte, ikke-distribuerte behandlingsutfall")
        val fattetBefore = OffsetDateTime.now().minus(freshBehandlingsutfallGracePeriod.toJavaDuration())
        val soknaderMedIkkeDistribuerteBehandlingsutfall =
            soknadRepository.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore)

        return soknaderMedIkkeDistribuerteBehandlingsutfall.map { soknad ->
            runCatching { distribuerBehandlingsutfall(soknad) }
                .onFailure { log.error("Feil ved distribusjon av behandlingsutfall for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Distribuerer (bestiller utsending av) behandlingsutfallet for én søknad. Gjenbrukes både av den
     * periodiske cronjobben ([distribuerBehandlingsutfall]) og av API-laget, som forsøker distribusjon
     * umiddelbart etter en vellykket umiddelbar journalføring (se
     * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.distribuerBehandlingsutfall] håndhever at et behandlingsutfall må være
     * journalført og kun distribueres én gang, og dokdistfordeling behandler gjentatte bestillinger på
     * samme journalpost som suksess (409 Conflict) dersom denne likevel skulle bli kalt samtidig
     * fra flere steder for samme behandlingsutfall.
     */
    suspend fun distribuerBehandlingsutfall(soknad: Soknad) {
        val behandlingsutfall =
            checkNotNull(soknad.behandlingsutfall) {
                "Søknad ${soknad.id} er ikke ferdigbehandlet, kan ikke distribuere"
            }

        val journalpostId =
            checkNotNull(behandlingsutfall.journalpostId) {
                "Behandlingsutfall ${behandlingsutfall.behandlingsutfallId} er ikke journalført, kan ikke distribuere"
            }

        if (journalpostId.value == DEFAULT_FAILED_JP_ID.value) {
            // Hvis journalpostId er DEFAULT_FAILED_JP_ID, betyr det at journalføringen feilet i dev-gcp,
            // og vi skal ikke forsøke å distribuere dette behandlingsutfallet.
            soknadRepository.setBehandlingsutfallDistribuert(
                behandlingsutfallId = behandlingsutfall.behandlingsutfallId,
                distribuertTidspunkt = OffsetDateTime.now(),
            )
            return
        }

        val bestillingsId =
            distribusjonService.distribuer(journalpostId, behandlingsutfall.utfall.tilDistribusjonstype()).getOrThrow()

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
        private val log = LoggerFactory.getLogger(JournalforBehandlingsutfallService::class.java)
    }
}

/**
 * En henleggelse er ikke et vedtak i folketrygdrettslig forstand, og skal derfor distribueres
 * med distribusjonstype VIKTIG i stedet for VEDTAK.
 */
private fun Utfall.tilDistribusjonstype(): Distribusjonstype =
    when (this) {
        Utfall.Henlagt -> Distribusjonstype.VIKTIG
        Utfall.Innvilget, is Utfall.DelvisInnvilget, Utfall.Avslag -> Distribusjonstype.VEDTAK
    }
