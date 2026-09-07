package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.JournalforingService.Companion.DEFAULT_FAILED_JP_ID
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.UtenlandsoppholdBrevkode
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Use case (application service) som orkestrerer journalføring av henleggelser:
 * henter u-journalførte henleggelser, genererer PDF, sender til dokarkiv, og
 * oppdaterer henleggelsen med journalpost-id og journalføringstidspunkt.
 *
 * Speiler [JournalforVedtakService], men en henleggelse er IKKE et vedtak — (se
 * [no.nav.syfo.utenlandsopphold.infrastructure.journalforing.UtenlandsoppholdBrevkode.HENLEGGELSE]),
 * og henleggelsen distribueres med [Distribusjonstype.VIKTIG] (ikke VEDTAK).
 *
 * Domenet ([Soknad.journalforHenleggelse]) håndhever invarianten om at en henleggelse
 * kun kan journalføres én gang — feiler denne tjenesten på én henleggelse stopper
 * det ikke journalføring av de øvrige.
 *
 * @param freshHenleggelseGracePeriod Brukes av [journalforHenleggelse] (den periodiske
 * cronjobben) til å ekskludere nylig henlagte søknader fra spørringen, slik at den ikke
 * journalfører en henleggelse som API-laget allerede forsøker å journalføre umiddelbart.
 */
class JournalforHenleggelseService(
    private val soknadRepository: ISoknadRepository,
    private val personInfoClient: IPdlClient,
    private val pdfClient: IPdfClient,
    private val journalforingService: IJournalforingService,
    private val distribusjonService: IDistribusjonService,
    private val freshHenleggelseGracePeriod: Duration = Duration.ZERO,
) {
    suspend fun journalforHenleggelse(): List<Result<Soknad>> {
        log.debug("Starter journalføring av ujournalførte henleggelser")
        val henlagtBefore = OffsetDateTime.now().minus(freshHenleggelseGracePeriod.toJavaDuration())
        val soknaderMedIkkeJournalforteHenleggelser = soknadRepository.getIkkeJournalforteHenleggelser(henlagtBefore)

        return soknaderMedIkkeJournalforteHenleggelser.map { soknad ->
            runCatching { journalforHenleggelse(soknad) }
                .onFailure { log.error("Feil ved journalføring av henleggelse for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Journalfører henleggelsen for én søknad. Gjenbrukes både av den periodiske cronjobben
     * ([journalforHenleggelse]) og av API-laget, som forsøker journalføring umiddelbart etter
     * at søknaden er henlagt (se [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.journalforHenleggelse] håndhever at en henleggelse kun journalføres
     * én gang, og dokarkiv dedupliserer på `eksternReferanseId` (henleggelseId) dersom denne
     * likevel skulle bli kalt samtidig fra flere steder for samme henleggelse.
     *
     * @return den oppdaterte [Soknad]-en med den journalførte henleggelsen, slik at
     * API-laget kan kjede umiddelbar distribusjon ([distribuerHenleggelse]) rett etter.
     */
    suspend fun journalforHenleggelse(soknad: Soknad): Soknad {
        val henleggelse =
            checkNotNull(soknad.henleggelse) {
                "Søknad ${soknad.id} er ikke henlagt, kan ikke journalføre"
            }

        val mottakerNavn = personInfoClient.getNavn(soknad.personident)

        val pdf =
            pdfClient.createHenleggelsePdf(
                mottakerFodselsnummer = soknad.personident,
                mottakerNavn = mottakerNavn,
                documentComponents = henleggelse.document,
            )

        val journalpostId: JournalpostId =
            journalforingService
                .journalfor(
                    personident = soknad.personident,
                    pdf = pdf,
                    eksternReferanseId = henleggelse.henleggelseId.toString(),
                    brevkode = UtenlandsoppholdBrevkode.HENLEGGELSE,
                    tittel = "Henleggelse av søknad om utenlandsopphold",
                ).getOrThrow()

        val journalfortTidspunkt = OffsetDateTime.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val journalfortSoknad = soknad.journalforHenleggelse(journalpostId, journalfortTidspunkt)
        val journalfortHenleggelse = checkNotNull(journalfortSoknad.henleggelse)

        soknadRepository.setHenleggelseJournalfort(
            henleggelseId = journalfortHenleggelse.henleggelseId,
            journalpostId = journalpostId,
            journalfortTidspunkt = checkNotNull(journalfortHenleggelse.journalfortTidspunkt),
        )
        log.info(
            "Henleggelse ${journalfortHenleggelse.henleggelseId} for søknad ${soknad.id} journalført med " +
                "journalpostId ${journalfortHenleggelse.journalpostId?.value}",
        )
        return journalfortSoknad
    }

    /**
     * Bestiller distribusjon (utsending til mottaker) av henleggelser som er journalført, men
     * ennå ikke distribuert. Idempotent pass: kjøres på nytt ved neste intervall for
     * henleggelser som feilet, siden dokdistfordeling selv behandler gjentatte bestillinger på
     * samme journalpost som suksess (409 Conflict).
     */
    suspend fun distribuerHenleggelse(): List<Result<Unit>> {
        log.debug("Starter distribusjon av journalførte, ikke-distribuerte henleggelser")
        val henlagtBefore = OffsetDateTime.now().minus(freshHenleggelseGracePeriod.toJavaDuration())
        val soknaderMedIkkeDistribuerteHenleggelser = soknadRepository.getHenleggelserMedIkkeDistribuert(henlagtBefore)

        return soknaderMedIkkeDistribuerteHenleggelser.map { soknad ->
            runCatching { distribuerHenleggelse(soknad) }
                .onFailure { log.error("Feil ved distribusjon av henleggelse for søknad ${soknad.id}", it) }
        }
    }

    /**
     * Distribuerer (bestiller utsending av) henleggelsen for én søknad med
     * [Distribusjonstype.VIKTIG]. Gjenbrukes både av den periodiske cronjobben
     * ([distribuerHenleggelse]) og av API-laget, som forsøker distribusjon umiddelbart etter en
     * vellykket umiddelbar journalføring (se
     * [no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi]).
     * Idempotent: [Soknad.distribuerHenleggelse] håndhever at en henleggelse må være
     * journalført og kun distribueres én gang, og dokdistfordeling behandler gjentatte
     * bestillinger på samme journalpost som suksess (409 Conflict) dersom denne likevel skulle
     * bli kalt samtidig fra flere steder for samme henleggelse.
     */
    suspend fun distribuerHenleggelse(soknad: Soknad) {
        val henleggelse =
            checkNotNull(soknad.henleggelse) {
                "Søknad ${soknad.id} er ikke henlagt, kan ikke distribuere"
            }

        val journalpostId =
            checkNotNull(henleggelse.journalpostId) {
                "Henleggelse ${henleggelse.henleggelseId} er ikke journalført, kan ikke distribuere"
            }

        if (journalpostId.value == DEFAULT_FAILED_JP_ID.value) {
            // Hvis journalpostId er DEFAULT_FAILED_JP_ID, betyr det at journalføringen feilet i dev-gcp, og vi skal ikke forsøke å distribuere denne henleggelsen.
            soknadRepository.setHenleggelseDistribuert(
                henleggelseId = henleggelse.henleggelseId,
                distribuertTidspunkt = OffsetDateTime.now(),
            )
            return
        }

        val bestillingsId = distribusjonService.distribuer(journalpostId, Distribusjonstype.VIKTIG).getOrThrow()

        log.info(
            "Distribusjon av henleggelse ${henleggelse.henleggelseId} for søknad ${soknad.id} bestilt, bestillingsId: $bestillingsId",
        )

        val distribuertTidspunkt = OffsetDateTime.now()

        // Bygger den oppdaterte søknaden gjennom aggregatroten for å håndheve
        // idempotens-invarianten før vi lar den slå gjennom i databasen.
        val distribuertSoknad = soknad.distribuerHenleggelse(distribuertTidspunkt)
        val distribuertHenleggelse = checkNotNull(distribuertSoknad.henleggelse)

        soknadRepository.setHenleggelseDistribuert(
            henleggelseId = distribuertHenleggelse.henleggelseId,
            distribuertTidspunkt = checkNotNull(distribuertHenleggelse.distribuertTidspunkt),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforHenleggelseService::class.java)
    }
}
