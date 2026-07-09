package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadshendelse

import no.nav.syfo.utenlandsopphold.domain.ManglerSendtNavException
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private fun kafkaSykepengesoknad(
    status: KafkaSoknadstatusDTO = KafkaSoknadstatusDTO.SENDT,
    type: KafkaSoknadstypeDTO = KafkaSoknadstypeDTO.OPPHOLD_UTLAND,
    sporsmal: List<KafkaSporsmalDTO> = emptyList(),
    sendtNav: LocalDateTime? = LocalDateTime.parse("2026-01-02T08:00:00"),
): KafkaSykepengesoknadDTO =
    KafkaSykepengesoknadDTO(
        id = "0e7ef2ba-fbc4-4d76-9f52-2c37f37e40a4",
        fnr = "11111111111",
        status = status,
        type = type,
        sporsmal = sporsmal,
        sendtNav = sendtNav,
    )

private fun periodeUtlandSporsmal(vararg perioder: Pair<String, String>): KafkaSporsmalDTO =
    KafkaSporsmalDTO(
        tag = "PERIODEUTLAND",
        svar =
            perioder.map { (fom, tom) ->
                KafkaSvarDTO(verdi = """{"fom":"$fom","tom":"$tom"}""")
            },
    )

class KafkaSoknadshendelseDTOTest {
    @Test
    fun `mapper soknad av typen OPPHOLD_UTLAND med status SENDT til Soknad med soktePerioder`() {
        val kafkaSoknad =
            kafkaSykepengesoknad(
                sporsmal = listOf(periodeUtlandSporsmal("2026-01-05" to "2026-01-09")),
            )

        val soknad = assertNotNull(kafkaSoknad.toSoknad())

        assertEquals(UUID.fromString(kafkaSoknad.id), soknad.eksternId)
        assertEquals(kafkaSoknad.fnr, soknad.personident.value)
        assertEquals(1, soknad.soktePerioder.size)
        assertEquals(LocalDate.of(2026, 1, 5), soknad.soktePerioder.first().fom)
        assertEquals(LocalDate.of(2026, 1, 9), soknad.soktePerioder.first().tom)
    }

    @Test
    fun `mapper flere svar for PERIODEUTLAND til flere soktePerioder`() {
        val kafkaSoknad =
            kafkaSykepengesoknad(
                sporsmal =
                    listOf(
                        periodeUtlandSporsmal(
                            "2026-01-05" to "2026-01-09",
                            "2026-02-01" to "2026-02-10",
                        ),
                    ),
            )

        val soknad = assertNotNull(kafkaSoknad.toSoknad())

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5) to LocalDate.of(2026, 1, 9),
                LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 10),
            ),
            soknad.soktePerioder.map { it.fom to it.tom },
        )
    }

    @Test
    fun `deserialiserer JSON med enum-felter og perioder korrekt`() {
        val json =
            """
            {
                "id": "0e7ef2ba-fbc4-4d76-9f52-2c37f37e40a4",
                "fnr": "11111111111",
                "status": "SENDT",
                "type": "OPPHOLD_UTLAND",
                "sendtNav": "2026-01-02T08:00:00",
                "sporsmal": [
                    {
                        "tag": "PERIODEUTLAND",
                        "svar": [
                            { "verdi": "{\"fom\":\"2026-01-05\",\"tom\":\"2026-01-09\"}" }
                        ]
                    },
                    {
                        "tag": "LAND",
                        "svar": [
                            { "verdi": "SWE" }
                        ]
                    }
                ]
            }
            """.trimIndent()

        val deserializer = KafkaSykepengesoknadDeserializer()
        val kafkaSoknad = deserializer.deserialize("flex.sykepengesoknad", json.toByteArray())

        assertEquals(KafkaSoknadstatusDTO.SENDT, kafkaSoknad.status)
        assertEquals(KafkaSoknadstypeDTO.OPPHOLD_UTLAND, kafkaSoknad.type)

        val soknad = assertNotNull(kafkaSoknad.toSoknad())
        assertEquals(1, soknad.soktePerioder.size)
        assertEquals(LocalDate.of(2026, 1, 5), soknad.soktePerioder.first().fom)
        assertEquals(LocalDate.of(2026, 1, 9), soknad.soktePerioder.first().tom)
    }

    @Test
    fun `soknad uten PERIODEUTLAND-sporsmal throws exception`() {
        val kafkaSoknad = kafkaSykepengesoknad(sporsmal = emptyList())

        val exception = assertThrows<IllegalArgumentException> { kafkaSoknad.toSoknad() }
        assertEquals("Søknad må ha minst en søkt periode", exception.message)
    }

    @Test
    fun `soknad med status SENDT men uten sendtNav throws exception`() {
        val kafkaSoknad =
            kafkaSykepengesoknad(
                sendtNav = null,
                sporsmal = listOf(periodeUtlandSporsmal("2026-01-05" to "2026-01-09")),
            )

        assertThrows<ManglerSendtNavException> { kafkaSoknad.toSoknad() }
    }
}
