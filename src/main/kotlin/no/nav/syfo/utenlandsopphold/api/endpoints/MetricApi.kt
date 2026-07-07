package no.nav.syfo.utenlandsopphold.api.endpoints

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.utenlandsopphold.infrastructure.metric.METRICS_REGISTRY

const val METRICS_PATH = "/internal/metrics"

fun Routing.registerMetricApi() {
    get(METRICS_PATH) {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
