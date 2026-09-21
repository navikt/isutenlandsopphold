package no.nav.syfo.utenlandsopphold.infrastructure.kafka.soknadstatus

import com.fasterxml.jackson.databind.JsonNode
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellGrunn
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.BehandlingsUtfall
import no.nav.syfo.utenlandsopphold.domain.lagBehandling
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Låser den serialiserte formen på meldingene vi publiserer på
 * `teamsykefravr.utenlandsopphold-soknad-status`. Konsumentene (syfooversiktsrv og
 * datavarehuset) leser JSON-en direkte, så feltnavnene er en ekstern kontrakt selv om
 * domenetypene endrer navn internt.
 */
class SoknadstatusRecordTest {
    private val mapper = configuredJacksonMapper()

    private fun serialize(record: SoknadstatusRecord): JsonNode = mapper.readTree(mapper.writeValueAsBytes(record))

    @Test
    fun `MOTTATT-melding har forventede felter og ingen behandling`() {
        val soknad = lagSoknad()

        val json = serialize(SoknadstatusRecord.fromSoknad(soknad))

        assertEquals(soknad.eksternId.toString(), json["uuid"].asText())
        assertEquals(soknad.personident.value, json["personident"].asText())
        assertEquals("MOTTATT", json["status"].asText())
        assertTrue(json["behandling"] == null || json["behandling"].isNull)
    }

    @Test
    fun `BEHANDLET-melding har behandling med utfall og innvilgede perioder`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))
        val behandling =
            lagBehandling(
                utfall = BehandlingsUtfall.DelvisInnvilget(listOf(innvilgetPeriode), "Delvis innvilget begrunnelse"),
                behandletAv = Navident("Z999999"),
                behandletTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            )
        val soknad = lagSoknad(behandling = behandling)

        val json = serialize(SoknadstatusRecord.fromBehandletSoknad(soknad))

        assertEquals(soknad.eksternId.toString(), json["uuid"].asText())
        assertEquals("BEHANDLET", json["status"].asText())

        val behandlingJson = json["behandling"]
        assertEquals(behandling.behandlingId.toString(), behandlingJson["uuid"].asText())
        assertEquals("Z999999", behandlingJson["veilederident"].asText())
        assertEquals("DELVIS_INNVILGET", behandlingJson["utfall"].asText())
        assertEquals(1, behandlingJson["innvilgedePerioder"].size())
        assertEquals("2026-01-05", behandlingJson["innvilgedePerioder"][0]["fom"].asText())
        assertEquals("2026-01-07", behandlingJson["innvilgedePerioder"][0]["tom"].asText())
    }

    @Test
    fun `henleggelse publiseres med utfall HENLAGT og uten innvilgede perioder`() {
        val soknad = lagSoknad(behandling = lagBehandling(utfall = BehandlingsUtfall.Henlagt("Trukket")))

        val json = serialize(SoknadstatusRecord.fromBehandletSoknad(soknad))

        assertEquals("HENLAGT", json["behandling"]["utfall"].asText())
        assertEquals(0, json["behandling"]["innvilgedePerioder"].size())
    }

    @Test
    fun `begrunnelse og brev publiseres ikke på topicet`() {
        val soknad = lagSoknad(behandling = lagBehandling(utfall = BehandlingsUtfall.Avslag("Intern begrunnelse")))

        val json = serialize(SoknadstatusRecord.fromBehandletSoknad(soknad))

        assertTrue(json["behandling"]["begrunnelse"] == null)
        assertTrue(json["behandling"]["brev"] == null)
    }

    @Test
    fun `ikke aktuell publiseres med utfall IKKE_AKTUELL og grunn`() {
        val soknad =
            lagSoknad(
                behandling = lagBehandling(utfall = BehandlingsUtfall.IkkeAktuell(IkkeAktuellGrunn.BEHANDLET_I_INFOTRYGD)),
            )

        val json = serialize(SoknadstatusRecord.fromBehandletSoknad(soknad))

        assertEquals("BEHANDLET", json["status"].asText())
        assertEquals("IKKE_AKTUELL", json["behandling"]["utfall"].asText())
        assertEquals("BEHANDLET_I_INFOTRYGD", json["behandling"]["ikkeAktuellGrunn"].asText())
        assertEquals(0, json["behandling"]["innvilgedePerioder"].size())
    }

    @Test
    fun `andre utfall publiseres uten grunn`() {
        val soknad = lagSoknad(behandling = lagBehandling(utfall = BehandlingsUtfall.Henlagt("Trukket")))

        val grunn = serialize(SoknadstatusRecord.fromBehandletSoknad(soknad))["behandling"]["ikkeAktuellGrunn"]

        assertTrue(grunn == null || grunn.isNull, "ikkeAktuellGrunn skal ikke ha verdi for andre utfall enn ikke aktuell")
    }

    /**
     * `ikkeAktuellGrunn` serialiseres som `grunn.name`, og samme navn ligger lagret i
     * kolonnen `behandling.ikke_aktuell_grunn`. Enumnavnene er altså wire-kontrakt mot både
     * konsumentene og eksisterende rader. Et rename i IntelliJ ville endret begge deler uten
     * å gi kompileringsfeil. Feiler denne testen: varsle konsumentene og migrer dataene
     * før du endrer settet.
     */
    @Test
    fun `navnene på ikke-aktuell-grunnene er wire-kontrakt`() {
        assertEquals(
            setOf("BEHANDLET_I_INFOTRYGD", "DUPLIKAT", "ANNET"),
            IkkeAktuellGrunn.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `fromBehandletSoknad på ubehandlet soknad kaster`() {
        assertFailsWith<IllegalStateException> {
            SoknadstatusRecord.fromBehandletSoknad(lagSoknad())
        }
    }
}
