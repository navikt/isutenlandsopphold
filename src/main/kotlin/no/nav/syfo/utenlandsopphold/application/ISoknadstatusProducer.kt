package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus.SoknadstatusRecord

interface ISoknadstatusProducer {
    fun publiser(record: SoknadstatusRecord): Result<Unit>
}
