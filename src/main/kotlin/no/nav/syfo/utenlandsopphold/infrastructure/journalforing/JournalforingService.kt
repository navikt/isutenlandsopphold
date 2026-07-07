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

/**
 * Journalfører PDF-en for et vedtak i dokarkiv (Joark). `dokarkivClient` og
 * `isJournalforingRetryEnabled` (true i prod, false i dev-gcp) kobles inn fra
 * infrastructure/clients/ClientsModule.kt basert på Environment.
 */
class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val isJournalforingRetryEnabled: Boolean,
) : IJournalforingService {
    override suspend fun journalfor(
        personident: Personident,
        pdf: ByteArray,
        eksternReferanseId: String,
    ): Result<JournalpostId> =
        runCatching {
            val journalpostRequest =
                createJournalpostRequest(
                    bruker = Bruker(id = personident.value, idType = BrukerIdType.PERSONIDENT.value),
                    brevkode = UtenlandsoppholdBrevkode.VEDTAK,
                    tittel = "Vedtak om utenlandsopphold",
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
        val DEFAULT_FAILED_JP_ID = JournalpostId("0")
        private val log = LoggerFactory.getLogger(JournalforingService::class.java)
    }
}
