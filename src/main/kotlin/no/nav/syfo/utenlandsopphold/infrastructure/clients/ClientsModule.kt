package no.nav.syfo.utenlandsopphold.infrastructure.clients

import no.nav.syfo.common.distribusjon.client.DokdistfordelingClient
import no.nav.syfo.common.http.defaultHttpClient
import no.nav.syfo.common.http.proxyHttpClient
import no.nav.syfo.common.journalforing.client.DokarkivClient
import no.nav.syfo.common.token.azuread.AzureAdClient
import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.utenlandsopphold.application.IDistribusjonService
import no.nav.syfo.utenlandsopphold.application.IJournalforingService
import no.nav.syfo.utenlandsopphold.application.IPdfClient
import no.nav.syfo.utenlandsopphold.application.IPdlClient
import no.nav.syfo.utenlandsopphold.infrastructure.distribusjon.DistribusjonService
import no.nav.syfo.utenlandsopphold.infrastructure.distribusjon.DokdistfordelingClientConfig
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.DokarkivClientConfig
import no.nav.syfo.utenlandsopphold.infrastructure.journalforing.JournalforingService
import no.nav.syfo.utenlandsopphold.infrastructure.leaderelection.LeaderElection
import no.nav.syfo.utenlandsopphold.infrastructure.leaderelection.LeaderElectionConfig
import no.nav.syfo.utenlandsopphold.infrastructure.pdfgen.PdfClient
import no.nav.syfo.utenlandsopphold.infrastructure.pdfgen.PdfClientConfig
import no.nav.syfo.utenlandsopphold.infrastructure.pdl.PdlClient
import no.nav.syfo.utenlandsopphold.infrastructure.pdl.PdlClientConfig

/**
 * Samler oppsett av alle eksterne klienter journalføringsjobben trenger.
 * Holdes utenfor App.kt for å gjøre wiring av infrastruktur enkel å teste/erstatte isolert.
 */
class ClientsModule(
    dokarkivClientConfig: DokarkivClientConfig = DokarkivClientConfig.fromEnv(),
    dokdistfordelingClientConfig: DokdistfordelingClientConfig = DokdistfordelingClientConfig.fromEnv(),
    pdlClientConfig: PdlClientConfig = PdlClientConfig.fromEnv(),
    pdfClientConfig: PdfClientConfig = PdfClientConfig.fromEnv(),
    leaderElectionConfig: LeaderElectionConfig = LeaderElectionConfig.fromEnv(),
) {
    // AzureAdClient brukes for både PDL og dokarkiv, siden begge autentiseres via Entra ID
    // system-token (client credentials) — appen kaller dem som seg selv, ikke på vegne av en bruker.
    private val azureAdClient = AzureAdClient()

    val journalforingService: IJournalforingService =
        JournalforingService(
            dokarkivClient =
                DokarkivClient(
                    systemTokenProvider = azureAdClient,
                    // Dokarkiv nås som ekstern host (utenfor NAIS-clusteret), derfor proxyHttpClient.
                    clientConfig = ClientConfig(baseUrl = dokarkivClientConfig.baseUrl, clientId = dokarkivClientConfig.clientId),
                    httpClient = proxyHttpClient(),
                ),
            isJournalforingRetryEnabled = dokarkivClientConfig.isRetryEnabled,
        )

    val distribusjonService: IDistribusjonService =
        DistribusjonService(
            dokdistfordelingClient =
                DokdistfordelingClient(
                    systemTokenProvider = azureAdClient,
                    // Dokdistfordeling nås som ekstern host (utenfor NAIS-clusteret), derfor proxyHttpClient.
                    clientConfig =
                        ClientConfig(
                            baseUrl = dokdistfordelingClientConfig.baseUrl,
                            clientId = dokdistfordelingClientConfig.clientId,
                        ),
                    httpClient = proxyHttpClient(),
                ),
            bestillendeFagsystem = BESTILLENDE_FAGSYSTEM,
        )

    val personInfoClient: IPdlClient =
        PdlClient(
            systemTokenProvider = azureAdClient,
            config = pdlClientConfig,
            httpClient = defaultHttpClient(),
        )

    val pdfClient: IPdfClient =
        PdfClient(
            config = pdfClientConfig,
            httpClient = defaultHttpClient(),
        )

    val leaderElection =
        LeaderElection(
            httpClient = defaultHttpClient(),
            config = leaderElectionConfig,
        )

    companion object {
        private const val BESTILLENDE_FAGSYSTEM = "MODIA_SYFO"
    }
}
