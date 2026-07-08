package no.nav.syfo.utenlandsopphold.infrastructure.pdl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import no.nav.syfo.common.http.defaultHttpClient
import no.nav.syfo.common.token.SystemTokenProvider
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.bearerHeader
import no.nav.syfo.utenlandsopphold.application.IPdlClient

private const val HENT_PERSON_QUERY =
    """
    query(${'$'}ident: ID!) {
        hentPerson(ident: ${'$'}ident) {
            navn(historikk: false) {
                fornavn
                mellomnavn
                etternavn
            }
        }
    }
    """

/**
 * Client for PDL (Persondataløsningen) — henter navn på innbygger for bruk i
 * journalførte dokumenter.
 *
 * Rød sone: fødselsnummer og navn er personopplysninger (PII). Ikke logg
 * request/response-innhold fra denne klienten.
 *
 * Bruker en [SystemTokenProvider] (typisk [no.nav.syfo.common.token.azuread.AzureAdClient])
 * til å hente system-token for PDL, siden oppslaget skjer som applikasjonen selv
 * (bak en cronjob), ikke på vegne av en innlogget veileder.
 */
class PdlClient(
    private val systemTokenProvider: SystemTokenProvider,
    private val config: PdlClientConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
) : IPdlClient {
    override suspend fun getNavn(personident: Personident): String {
        val systemToken =
            systemTokenProvider.getSystemToken(config.clientId)
                ?: error("Failed to get navn from PDL: Failed to get system token for PDL")

        val response =
            httpClient.post(config.baseUrl) {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                header(BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER_UTENLANDSOPPHOLD)
                contentType(ContentType.Application.Json)
                setBody(PdlHentPersonRequest(variables = PdlHentPersonVariables(ident = personident.value)))
            }

        val pdlResponse = response.body<PdlHentPersonResponse>()
        val navn =
            pdlResponse.data
                ?.hentPerson
                ?.navn
                ?.firstOrNull()
                ?: error("Fant ikke navn for person i PDL")

        return listOfNotNull(navn.fornavn, navn.mellomnavn, navn.etternavn).joinToString(" ")
    }

    companion object {
        private const val BEHANDLINGSNUMMER_HEADER = "behandlingsnummer"

        private const val BEHANDLINGSNUMMER_UTENLANDSOPPHOLD = "B426"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlHentPersonRequest(
        val query: String = HENT_PERSON_QUERY,
        val variables: PdlHentPersonVariables,
    )

    data class PdlHentPersonVariables(
        val ident: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlHentPersonResponse(
        val data: PdlData?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlData(
        val hentPerson: PdlHentPerson?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlHentPerson(
        val navn: List<PdlNavn>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlNavn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )
}
