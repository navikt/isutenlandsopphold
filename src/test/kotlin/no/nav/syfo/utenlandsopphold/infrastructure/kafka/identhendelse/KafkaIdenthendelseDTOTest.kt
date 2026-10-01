package no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse

import no.nav.syfo.common.types.ident.Personident
import org.apache.avro.Schema
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KafkaIdenthendelseDTOTest {
    private val identSchema: Schema =
        SchemaBuilder
            .record("Identifikator")
            .fields()
            .requiredString("idnummer")
            .requiredBoolean("gjeldende")
            .requiredString("type")
            .endRecord()
    private val aktorSchema: Schema =
        SchemaBuilder
            .record("Aktor")
            .fields()
            .name("identifikatorer")
            .type()
            .array()
            .items(identSchema)
            .noDefault()
            .endRecord()

    private fun ident(
        idnummer: String,
        gjeldende: Boolean,
        type: String,
    ): GenericRecord =
        GenericData.Record(identSchema).apply {
            put("idnummer", idnummer)
            put("gjeldende", gjeldende)
            put("type", type)
        }

    private fun aktor(vararg identer: GenericRecord): GenericRecord =
        GenericData.Record(aktorSchema).apply {
            put("identifikatorer", GenericData.Array(aktorSchema.getField("identifikatorer").schema(), identer.toList()))
        }

    @Test
    fun `mapper avro-record og skiller aktive og inaktive folkeregisteridenter`() {
        val dto =
            aktor(
                ident("22222222222", true, "FOLKEREGISTERIDENT"),
                ident("11111111111", false, "FOLKEREGISTERIDENT"),
                ident("9999999999999", true, "AKTORID"),
            ).toKafkaIdenthendelseDTO()

        assertEquals(3, dto.identifikatorer.size)
        assertEquals(Personident("22222222222"), dto.getActivePersonident())
        assertEquals(listOf(Personident("11111111111")), dto.getInactivePersonidenter())
    }

    @Test
    fun `kaster ved ukjent identtype`() {
        assertFailsWith<IllegalStateException> {
            aktor(ident("1", true, "UKJENT")).toKafkaIdenthendelseDTO()
        }
    }
}
