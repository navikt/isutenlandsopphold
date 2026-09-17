package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Dokumenttype
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.dokumentId
import no.nav.syfo.utenlandsopphold.domain.dokumenttype
import no.nav.syfo.utenlandsopphold.domain.innhold
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.JournalforingService.Companion.DEFAULT_FAILED_JP_ID
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Use case (application service) som orkestrerer journalføring og distribusjon av dokumenter:
 * henter behandlinger med ujournalførte dokumenter, genererer PDF, sender til dokarkiv, og
 * oppdaterer dokumentetet med journalpost-id og journalføringstidspunkt.
 *
 * Domenet ([Soknad.journalforDokument]) håndhever invarianten om at et dokument kun kan
 * journalføres én gang — feiler denne servicen på ett dokument stopper det ikke
 * journalføring av de øvrige.
 *
 * @param freshBehandlingGracePeriod Brukes av [journalforDokument] (den periodiske cronjobben) til å
 * ekskludere nylig behandlede søknader fra spørringen, slik at den ikke journalfører et dokument som
 * API-laget allerede forsøker å journalføre umiddelbart (se [journalforDokument] med `soknad`-parameter).
 */
class DokumentService(
    private val soknadRepository: ISoknadRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
    private val distribusjonService: IDistribusjonService,
    private val freshBehandlingGracePeriod: Duration = Duration.ZERO,
) {
    suspend fun journalforDokument(): List<Result<Soknad>> {
        log.debug("Starter journalføring av ujournalførte dokumenter")
        val behandletBefore = OffsetDateTime.now().minus(freshBehandlingGracePeriod.toJavaDuration())
        val soknaderMedIkkeJournalforteBrev = soknadRepository.getIkkeJournalforteSoknader(behandletBefore)

        return soknaderMedIkkeJournalforteBrev.map { soknad ->
            runCatching { journalforDokument(soknad) }
                .onFailure { log.error("Feil ved journalføring av brev for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Journalfører brevet for én søknad. Gjenbrukes både av den periodiske cronjobben
     * ([journalforDokument]) og av API-laget, som forsøker journalføring umiddelbart etter at
     * søknaden er behandlet (se [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.journalforBrev] håndhever at et brev kun journalføres én gang,
     * og dokarkiv dedupliserer på `eksternReferanseId` (brevId) dersom denne likevel
     * skulle bli kalt samtidig fra flere steder for samme brev.
     *
     * @return den oppdaterte [Soknad]-en med det journalførte brevet, slik at API-laget kan
     * kjede umiddelbar distribusjon ([distribuerDokument]) rett etter.
     */
    suspend fun journalforDokument(soknad: Soknad): Soknad {
        val dokument =
            checkNotNull(soknad.behandling?.dokument) {
                "Søknad ${soknad.id} er ikke behandlet, kan ikke journalføre dokument"
            }

        val mottakerNavn = personInfoClient.getNavn(soknad.personident)

        val pdf =
            pdfClient.createDokumentPdf(
                mottakerFodselsnummer = soknad.personident,
                mottakerNavn = mottakerNavn,
                dokumenttype = dokument.dokumenttype,
                documentComponents = dokument.innhold,
            )

        val journalpostId: JournalpostId =
            journalforingService
                .journalfor(
                    personident = soknad.personident,
                    pdf = pdf,
                    eksternReferanseId = dokument.dokumentId.toString(),
                    dokumenttype = dokument.dokumenttype.tilJournalforingDokumenttype(),
                ).getOrThrow()

        val journalfortTidspunkt = OffsetDateTime.now()

        val journalfortSoknad = soknad.journalforDokument(journalpostId, journalfortTidspunkt)
        val journalfortDokument = checkNotNull(journalfortSoknad.behandling?.dokument)

        soknadRepository.setBrevJournalfort(
            dokumentId = journalfortDokument.dokumentId,
            journalpostId = journalpostId,
            journalfortTidspunkt = checkNotNull(journalfortDokument.journalfortTidspunkt),
        )
        log.info(
            "Dokument ${journalfortDokument.dokumentId} for søknad ${soknad.id} journalført med journalpostId ${journalfortDokument.journalpostId?.value}",
        )
        return journalfortSoknad
    }

    /**
     * Bestiller distribusjon (utsending til mottaker) av brev som er journalført, men ennå
     * ikke distribuert. Idempotent pass: kjøres på nytt ved neste intervall for brev som
     * feilet, siden dokdistfordeling selv behandler gjentatte bestillinger på samme
     * journalpost som suksess (409 Conflict).
     */
    suspend fun distribuerDokument(): List<Result<Unit>> {
        log.debug("Starter distribusjon av journalførte, ikke-distribuerte dokumenter")
        val behandletBefore = OffsetDateTime.now().minus(freshBehandlingGracePeriod.toJavaDuration())
        val soknaderMedIkkeDistribuerteDokumenter = soknadRepository.getSoknaderMedIkkeDistribuerteDokumenter(behandletBefore)

        return soknaderMedIkkeDistribuerteDokumenter.map { soknad ->
            runCatching { distribuerDokument(soknad) }
                .onFailure { log.error("Feil ved distribusjon av dokumenter for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Distribuerer (bestiller utsending av) dokumentet for én søknad. Gjenbrukes både av den
     * periodiske cronjobben ([distribuerDokument]) og av API-laget, som forsøker distribusjon
     * umiddelbart etter en vellykket umiddelbar journalføring (se
     * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.distribuerDokument] håndhever at et dokument må være journalført og kun
     * distribueres én gang, og dokdistfordeling behandler gjentatte bestillinger på samme
     * journalpost som suksess (409 Conflict) dersom denne likevel skulle bli kalt samtidig
     * fra flere steder for samme dokument.
     */
    suspend fun distribuerDokument(soknad: Soknad) {
        val dokument =
            checkNotNull(soknad.behandling?.dokument) {
                "Søknad ${soknad.id} er ikke behandlet, kan ikke distribuere dokument"
            }

        val journalpostId =
            checkNotNull(dokument.journalpostId) {
                "Dokument ${dokument.dokumentId} er ikke journalført, kan ikke distribuere"
            }

        if (journalpostId.value == DEFAULT_FAILED_JP_ID.value) {
            // Hvis journalpostId er DEFAULT_FAILED_JP_ID, betyr det at journalføringen feilet i dev-gcp, og vi skal ikke forsøke å distribuere dette brevet.
            soknadRepository.setBrevDistribuert(
                dokumentId = dokument.dokumentId,
                distribuertTidspunkt = OffsetDateTime.now(),
            )
            return
        }

        val bestillingsId =
            distribusjonService.distribuer(journalpostId, dokument.dokumenttype.tilDistribusjonstype()).getOrThrow()

        log.info("Distribusjon av dokument ${dokument.dokumentId} for søknad ${soknad.id} bestilt, bestillingsId: $bestillingsId")

        val distribuertTidspunkt = OffsetDateTime.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val distribuertSoknad = soknad.distribuerDokument(distribuertTidspunkt)
        val distribuertDokument = checkNotNull(distribuertSoknad.behandling?.dokument)

        soknadRepository.setBrevDistribuert(
            dokumentId = distribuertDokument.dokumentId,
            distribuertTidspunkt = checkNotNull(distribuertDokument.distribuertTidspunkt),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(DokumentService::class.java)
    }
}

/**
 * En henleggelse er ikke et vedtak i folketrygdrettslig forstand, og skal derfor distribueres
 * med distribusjonstype VIKTIG i stedet for VEDTAK.
 */
private fun Dokumenttype.tilDistribusjonstype(): Distribusjonstype =
    when (this) {
        Dokumenttype.HENLEGGELSE -> Distribusjonstype.VIKTIG
        Dokumenttype.VEDTAK_INNVILGET, Dokumenttype.VEDTAK_DELVIS_INNVILGET, Dokumenttype.VEDTAK_AVSLAG ->
            Distribusjonstype.VEDTAK
    }

private fun Dokumenttype.tilJournalforingDokumenttype(): JournalforingDokumenttype =
    when (this) {
        Dokumenttype.HENLEGGELSE -> JournalforingDokumenttype.HENLEGGELSE
        Dokumenttype.VEDTAK_INNVILGET, Dokumenttype.VEDTAK_DELVIS_INNVILGET, Dokumenttype.VEDTAK_AVSLAG ->
            JournalforingDokumenttype.VEDTAK
    }
