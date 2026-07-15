package no.nav.syfo.utenlandsopphold.api

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import no.nav.syfo.common.auth.JwtIssuer
import no.nav.syfo.common.auth.JwtIssuerType
import no.nav.syfo.common.auth.WellKnown
import no.nav.syfo.common.auth.installJwtAuthentication
import no.nav.syfo.common.tilgangskontroll.TilgangDeniedException
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.util.applyCommonJacksonConfig
import no.nav.syfo.common.util.consumerClientId
import no.nav.syfo.utenlandsopphold.api.endpoints.registerMetricApi
import no.nav.syfo.utenlandsopphold.api.endpoints.registerPodApi
import no.nav.syfo.utenlandsopphold.api.soknad.registerSoknadApi
import no.nav.syfo.utenlandsopphold.application.ApplicationState
import no.nav.syfo.utenlandsopphold.application.SoknadService
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.metric.METRICS_REGISTRY
import java.time.Duration

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    soknadService: SoknadService,
    tilgangskontrollClient: TilgangskontrollClient,
    azureAppClientId: String,
    wellKnownInternalAzureAD: WellKnown,
) {
    installContentNegotiation()
    installStatusPages()
    installMetrics()

    installJwtAuthentication(
        jwtIssuerList =
            listOf(
                JwtIssuer(
                    acceptedAudienceList = listOf(azureAppClientId),
                    jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                    wellKnown = wellKnownInternalAzureAD,
                ),
            ),
    )

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database,
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerSoknadApi(
                soknadService = soknadService,
                tilgangskontrollClient = tilgangskontrollClient,
            )
        }
    }
}

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson {
            applyCommonJacksonConfig()
        }
    }
}

fun Application.installMetrics() {
    install(MicrometerMetrics) {
        registry = METRICS_REGISTRY
        distributionStatisticConfig =
            DistributionStatisticConfig
                .Builder()
                .percentilesHistogram(true)
                .maximumExpectedValue(Duration.ofSeconds(20).toNanos().toDouble())
                .build()
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val log = call.application.log
            log
                .atError()
                .setCause(cause)
                .setMessage("Caught exception: ${cause.message}")
                .addKeyValue("consumerClientId", call.consumerClientId)
                .log()

            var isUnexpectedException = false
            val responseStatus =
                when (cause) {
                    is BadRequestException -> HttpStatusCode.BadRequest
                    is NotFoundException -> HttpStatusCode.NotFound
                    is IllegalArgumentException -> HttpStatusCode.BadRequest
                    is TilgangDeniedException -> HttpStatusCode.Forbidden
                    else -> {
                        isUnexpectedException = true
                        HttpStatusCode.InternalServerError
                    }
                }

            val message =
                if (isUnexpectedException) {
                    "The server reported an unexpected error and cannot complete the request."
                } else {
                    cause.message ?: "Unknown error"
                }
            call.respond(responseStatus, message)
        }
    }
}
