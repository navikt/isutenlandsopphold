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
import no.nav.syfo.utenlandsopphold.application.PdlIdent

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

private const val HENT_IDENTER_QUERY =
    """
    query(${'$'}ident: ID!) {
        hentIdenter(ident: ${'$'}ident, grupper: [FOLKEREGISTERIDENT], historikk: true) {
            identer {
                ident
                historisk
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

    override suspend fun hentIdenter(personident: Personident): List<PdlIdent>? {
        val systemToken =
            systemTokenProvider.getSystemToken(config.clientId)
                ?: error("Failed to get identer from PDL: Failed to get system token for PDL")

        val response =
            httpClient.post(config.baseUrl) {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                header(BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER_UTENLANDSOPPHOLD)
                contentType(ContentType.Application.Json)
                setBody(PdlHentIdenterRequest(variables = PdlHentPersonVariables(ident = personident.value)))
            }

        return response
            .body<PdlHentIdenterResponse>()
            .data
            ?.hentIdenter
            ?.identer
            ?.map { PdlIdent(ident = it.ident, historisk = it.historisk) }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlHentIdenterRequest(
        val query: String = HENT_IDENTER_QUERY,
        val variables: PdlHentPersonVariables,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlHentIdenterResponse(
        val data: PdlIdenterData?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlIdenterData(
        val hentIdenter: PdlIdenter?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlIdenter(
        val identer: List<PdlIdentDTO>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PdlIdentDTO(
        val ident: String,
        val historisk: Boolean,
    )
}
