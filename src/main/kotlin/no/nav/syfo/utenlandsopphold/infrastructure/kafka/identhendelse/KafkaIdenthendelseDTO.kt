package no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse

import no.nav.syfo.common.types.ident.Personident
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord

// Basert på https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/avro/no/nav/person/pdl/aktor/AktorV2.avdl

data class KafkaIdenthendelseDTO(
    val identifikatorer: List<Identifikator>,
) {
    val folkeregisterIdenter = identifikatorer.filter { it.type == IdentType.FOLKEREGISTERIDENT }

    fun getActivePersonident(): Personident? =
        folkeregisterIdenter
            .find { it.gjeldende }
            ?.let { Personident(it.idnummer) }

    fun getInactivePersonidenter(): List<Personident> =
        folkeregisterIdenter
            .filter { !it.gjeldende }
            .map { Personident(it.idnummer) }
}

data class Identifikator(
    val idnummer: String,
    val type: IdentType,
    val gjeldende: Boolean,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}

fun GenericRecord.toKafkaIdenthendelseDTO(): KafkaIdenthendelseDTO {
    @Suppress("UNCHECKED_CAST")
    val identifikatorer =
        (get("identifikatorer") as GenericData.Array<GenericRecord>).map {
            Identifikator(
                idnummer = it.get("idnummer").toString(),
                gjeldende = it.get("gjeldende").toString().toBoolean(),
                type =
                    when (val type = it.get("type").toString()) {
                        "FOLKEREGISTERIDENT" -> IdentType.FOLKEREGISTERIDENT
                        "AKTORID" -> IdentType.AKTORID
                        "NPID" -> IdentType.NPID
                        else -> throw IllegalStateException("Har mottatt ident med ukjent type: $type")
                    },
            )
        }
    return KafkaIdenthendelseDTO(identifikatorer)
}
