package no.nav.syfo.utenlandsopphold.infrastructure.journalforing

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.journalforing.JournalpostMottaker
import no.nav.syfo.common.journalforing.client.DokarkivClient
import no.nav.syfo.common.journalforing.createJournalpostRequest
import no.nav.syfo.common.journalforing.dto.Bruker
import no.nav.syfo.common.journalforing.dto.BrukerIdType
import no.nav.syfo.common.journalforing.dto.JournalpostKanal
import no.nav.syfo.common.journalforing.dto.JournalpostType
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.IJournalforingService
import org.slf4j.LoggerFactory

// TODO: Hente inn PDLClient for å få navn på innbygger
// TODO: Kalle denne fra en cronjob
// TODO: For å kunne sende inn PDFen her må vi også ha PdfClient
// TODO: Legge inn env-variabler i repoet for å kunne newe opp DokarkivClient
// TODO: Få tilgang til å kalle dokarkiv
class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val isJournalforingRetryEnabled: Boolean, // TODO: Denne må komme fra miljøvariabel som er true i prod og false i dev
) : IJournalforingService {
    override suspend fun journalfor(
        personident: Personident,
        pdf: ByteArray,
        eksternReferanseId: String,
    ): Result<JournalpostId> =
        runCatching {
            // TODO: definer tittel
            val journalpostRequest =
                createJournalpostRequest(
                    bruker = Bruker(id = personident.value, idType = BrukerIdType.PERSONIDENT.value),
                    brevkode = UtenlandsoppholdBrevkode.VEDTAK,
                    tittel = "TODO: tittel for utenlandsopphold journalpost",
                    pdf = pdf,
                    eksternReferanseId = eksternReferanseId,
                    journalpostType = JournalpostType.UTGAAENDE,
                    kanal = JournalpostKanal.DITT_NAV,
                    mottaker = JournalpostMottaker.Person(personident = personident),
                )

            try {
                dokarkivClient.journalfor(journalpostRequest).journalpostId
            } catch (exc: Exception) {
                if (isJournalforingRetryEnabled) {
                    throw exc
                } else {
                    log.error("Journalforing failed, skipping retry (should only happen in dev-gcp)", exc)
                    // Defaulting to DEFAULT_FAILED_JP_ID should only happen in dev-gcp: without it we'd
                    // spam dokarkiv with retries for persons missing an aktør-id.
                    DEFAULT_FAILED_JP_ID
                }
            }
        }

    companion object {
        val DEFAULT_FAILED_JP_ID = JournalpostId(0)
        private val log = LoggerFactory.getLogger(JournalforingService::class.java)
    }
}
