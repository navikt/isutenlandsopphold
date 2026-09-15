package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Brevtype
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.JournalforingService.Companion.DEFAULT_FAILED_JP_ID
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Use case (application service) som orkestrerer journalføring og distribusjon av brev:
 * henter behandlinger med ujournalførte brev, genererer PDF, sender til dokarkiv, og
 * oppdaterer brevet med journalpost-id og journalføringstidspunkt.
 *
 * Domenet ([Soknad.journalforBrev]) håndhever invarianten om at et brev kun kan
 * journalføres én gang — feiler denne tjenesten på ett brev stopper det ikke
 * journalføring av de øvrige.
 *
 * @param freshBehandlingGracePeriod Brukes av [journalforBrev] (den periodiske cronjobben) til å
 * ekskludere nylig behandlede søknader fra spørringen, slik at den ikke journalfører et brev som
 * API-laget allerede forsøker å journalføre umiddelbart (se [journalforBrev] med `soknad`-parameter).
 */
class BrevService(
    private val soknadRepository: ISoknadRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
    private val distribusjonService: IDistribusjonService,
    private val freshBehandlingGracePeriod: Duration = Duration.ZERO,
) {
    suspend fun journalforBrev(): List<Result<Soknad>> {
        log.debug("Starter journalføring av ujournalførte brev")
        val behandletBefore = OffsetDateTime.now().minus(freshBehandlingGracePeriod.toJavaDuration())
        val soknaderMedIkkeJournalforteBrev = soknadRepository.getIkkeJournalforteSoknader(behandletBefore)

        return soknaderMedIkkeJournalforteBrev.map { soknad ->
            runCatching { journalforBrev(soknad) }
                .onFailure { log.error("Feil ved journalføring av brev for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Journalfører brevet for én søknad. Gjenbrukes både av den periodiske cronjobben
     * ([journalforBrev]) og av API-laget, som forsøker journalføring umiddelbart etter at
     * søknaden er behandlet (se [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.journalforBrev] håndhever at et brev kun journalføres én gang,
     * og dokarkiv dedupliserer på `eksternReferanseId` (brevId) dersom denne likevel
     * skulle bli kalt samtidig fra flere steder for samme brev.
     *
     * @return den oppdaterte [Soknad]-en med det journalførte brevet, slik at API-laget kan
     * kjede umiddelbar distribusjon ([distribuerBrev]) rett etter.
     */
    suspend fun journalforBrev(soknad: Soknad): Soknad {
        val brev =
            checkNotNull(soknad.behandling?.brev) {
                "Søknad ${soknad.id} er ikke behandlet, kan ikke journalføre brev"
            }

        val mottakerNavn = personInfoClient.getNavn(soknad.personident)

        val pdf =
            pdfClient.createBrevPdf(
                mottakerFodselsnummer = soknad.personident,
                mottakerNavn = mottakerNavn,
                brevtype = brev.brevtype,
                documentComponents = brev.document,
            )

        val journalpostId: JournalpostId =
            journalforingService
                .journalfor(
                    personident = soknad.personident,
                    pdf = pdf,
                    eksternReferanseId = brev.brevId.toString(),
                    dokumenttype = brev.brevtype.tilJournalforingDokumenttype(),
                ).getOrThrow()

        val journalfortTidspunkt = OffsetDateTime.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val journalfortSoknad = soknad.journalforBrev(journalpostId, journalfortTidspunkt)
        val journalfortBrev = checkNotNull(journalfortSoknad.behandling?.brev)

        soknadRepository.setBrevJournalfort(
            brevId = journalfortBrev.brevId,
            journalpostId = journalpostId,
            journalfortTidspunkt = checkNotNull(journalfortBrev.journalfortTidspunkt),
        )
        log.info(
            "Brev ${journalfortBrev.brevId} for søknad ${soknad.id} journalført med journalpostId ${journalfortBrev.journalpostId?.value}",
        )
        return journalfortSoknad
    }

    /**
     * Bestiller distribusjon (utsending til mottaker) av brev som er journalført, men ennå
     * ikke distribuert. Idempotent pass: kjøres på nytt ved neste intervall for brev som
     * feilet, siden dokdistfordeling selv behandler gjentatte bestillinger på samme
     * journalpost som suksess (409 Conflict).
     */
    suspend fun distribuerBrev(): List<Result<Unit>> {
        log.debug("Starter distribusjon av journalførte, ikke-distribuerte brev")
        val behandletBefore = OffsetDateTime.now().minus(freshBehandlingGracePeriod.toJavaDuration())
        val soknaderMedIkkeDistribuerteBrev = soknadRepository.getSoknaderMedIkkeDistribuerteBrev(behandletBefore)

        return soknaderMedIkkeDistribuerteBrev.map { soknad ->
            runCatching { distribuerBrev(soknad) }
                .onFailure { log.error("Feil ved distribusjon av brev for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Distribuerer (bestiller utsending av) brevet for én søknad. Gjenbrukes både av den
     * periodiske cronjobben ([distribuerBrev]) og av API-laget, som forsøker distribusjon
     * umiddelbart etter en vellykket umiddelbar journalføring (se
     * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.distribuerBrev] håndhever at et brev må være journalført og kun
     * distribueres én gang, og dokdistfordeling behandler gjentatte bestillinger på samme
     * journalpost som suksess (409 Conflict) dersom denne likevel skulle bli kalt samtidig
     * fra flere steder for samme brev.
     */
    suspend fun distribuerBrev(soknad: Soknad) {
        val brev =
            checkNotNull(soknad.behandling?.brev) {
                "Søknad ${soknad.id} er ikke behandlet, kan ikke distribuere brev"
            }

        val journalpostId =
            checkNotNull(brev.journalpostId) {
                "Brev ${brev.brevId} er ikke journalført, kan ikke distribuere"
            }

        if (journalpostId.value == DEFAULT_FAILED_JP_ID.value) {
            // Hvis journalpostId er DEFAULT_FAILED_JP_ID, betyr det at journalføringen feilet i dev-gcp, og vi skal ikke forsøke å distribuere dette brevet.
            soknadRepository.setBrevDistribuert(
                brevId = brev.brevId,
                distribuertTidspunkt = OffsetDateTime.now(),
            )
            return
        }

        val bestillingsId =
            distribusjonService.distribuer(journalpostId, brev.brevtype.tilDistribusjonstype()).getOrThrow()

        log.info("Distribusjon av brev ${brev.brevId} for søknad ${soknad.id} bestilt, bestillingsId: $bestillingsId")

        val distribuertTidspunkt = OffsetDateTime.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val distribuertSoknad = soknad.distribuerBrev(distribuertTidspunkt)
        val distribuertBrev = checkNotNull(distribuertSoknad.behandling?.brev)

        soknadRepository.setBrevDistribuert(
            brevId = distribuertBrev.brevId,
            distribuertTidspunkt = checkNotNull(distribuertBrev.distribuertTidspunkt),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevService::class.java)
    }
}

/**
 * En henleggelse er ikke et vedtak i folketrygdrettslig forstand, og skal derfor distribueres
 * med distribusjonstype VIKTIG i stedet for VEDTAK.
 */
private fun Brevtype.tilDistribusjonstype(): Distribusjonstype =
    when (this) {
        Brevtype.HENLEGGELSE -> Distribusjonstype.VIKTIG
        Brevtype.VEDTAK_INNVILGET, Brevtype.VEDTAK_DELVIS_INNVILGET, Brevtype.VEDTAK_AVSLAG ->
            Distribusjonstype.VEDTAK
    }

private fun Brevtype.tilJournalforingDokumenttype(): JournalforingDokumenttype =
    when (this) {
        Brevtype.HENLEGGELSE -> JournalforingDokumenttype.HENLEGGELSE
        Brevtype.VEDTAK_INNVILGET, Brevtype.VEDTAK_DELVIS_INNVILGET, Brevtype.VEDTAK_AVSLAG ->
            JournalforingDokumenttype.VEDTAK
    }
