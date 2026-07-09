package no.nav.syfo.utenlandsopphold.infrastructure.distribusjon

import no.nav.syfo.common.distribusjon.client.DokdistfordelingClient
import no.nav.syfo.common.distribusjon.createDistribuerJournalpostRequest
import no.nav.syfo.common.distribusjon.dto.Distribusjonstidspunkt
import no.nav.syfo.common.distribusjon.dto.Distribusjonstype
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.utenlandsopphold.application.IDistribusjonService
import org.slf4j.LoggerFactory

/**
 * Bestiller distribusjon av en journalført journalpost via dokdistfordeling.
 * `dokdistfordelingClient` kobles inn fra infrastructure/clients/ClientsModule.kt.
 */
class DistribusjonService(
    private val dokdistfordelingClient: DokdistfordelingClient,
    private val bestillendeFagsystem: String,
) : IDistribusjonService {
    override suspend fun distribuer(journalpostId: JournalpostId): Result<String> =
        runCatching {
            val request =
                createDistribuerJournalpostRequest(
                    journalpostId = journalpostId.value,
                    bestillendeFagsystem = bestillendeFagsystem,
                    distribusjonstype = Distribusjonstype.VEDTAK,
                    distribusjonstidspunkt = Distribusjonstidspunkt.UMIDDELBART,
                )

            dokdistfordelingClient.distribuer(request).bestillingsId
        }.onFailure { exc ->
            log.error("Feil ved distribusjon av journalpost ${journalpostId.value}", exc)
        }

    companion object {
        private val log = LoggerFactory.getLogger(DistribusjonService::class.java)
    }
}
