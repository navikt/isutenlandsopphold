package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SoknadTest {
    @Test
    fun `behandle om innvilgelse på mottatt søknad gir behandling med utfall innvilget`() {
        val now = OffsetDateTime.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().behandle(
                utfall = Utfall.Innvilget,
                behandletAv = veileder,
                now = now,
                document = brevDocument,
                begrunnelse = null,
            )

        assertEquals(SoknadStatus.INNVILGET, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(Utfall.Innvilget, behandling.utfall)
        assertEquals(veileder, behandling.behandletAv)
        assertEquals(now, behandling.behandletTidspunkt)
        assertEquals(resultat.soktePerioder, behandling.innvilgedePerioder)
        assertEquals(null, behandling.begrunnelse)
    }

    @Test
    fun `behandle om delvis innvilgelse setter innvilgede perioder`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7))

        val resultat =
            lagSoknad().behandle(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), behandling.utfall)
        assertEquals(listOf(innvilgetPeriode), behandling.innvilgedePerioder)
        assertEquals("Delvis innvilget begrunnelse", behandling.begrunnelse)
    }

    @Test
    fun `behandle om delvis innvilgelse kan gå på tvers av sammenhengende søkte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14)),
            )
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12))

        val resultat =
            lagSoknad(soktePerioder = soktePerioder).behandle(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        assertEquals(listOf(innvilgetPeriode), resultat.behandling?.innvilgedePerioder)
    }

    @Test
    fun `behandle om delvis innvilgelse kan ikke gå gjennom hull mellom søkte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 14)),
            )

        assertFailsWith<IllegalArgumentException> {
            lagSoknad(soktePerioder = soktePerioder).behandle(
                utfall = Utfall.DelvisInnvilget(listOf(Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12)))),
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `behandle om delvis innvilgelse med overlappende innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().behandle(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(
                            Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7)),
                            Periode(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9)),
                        ),
                    ),
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `behandle om delvis innvilgelse uten innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().behandle(
                utfall = Utfall.DelvisInnvilget(emptyList()),
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `behandle om delvis innvilgelse med periode utenfor søkte perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().behandle(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(Periode(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 7))),
                    ),
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `behandle om avslag gir avslag uten innvilgede perioder`() {
        val resultat =
            lagSoknad().behandle(
                utfall = Utfall.Avslag,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Avslag begrunnelse",
            )

        assertEquals(SoknadStatus.AVSLAG, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(Utfall.Avslag, behandling.utfall)
        assertEquals(emptyList(), behandling.innvilgedePerioder)
        assertEquals("Avslag begrunnelse", behandling.begrunnelse)
    }

    @Test
    fun `behandle om avslag uten begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().behandle(
                utfall = Utfall.Avslag,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `behandle om henleggelse gir henlagt status uten innvilgede perioder`() {
        val resultat =
            lagSoknad().behandle(
                utfall = Utfall.Henlagt,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Søker har trukket søknaden",
            )

        assertEquals(SoknadStatus.HENLAGT, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(Utfall.Henlagt, behandling.utfall)
        assertEquals(emptyList(), behandling.innvilgedePerioder)
        assertEquals("Søker har trukket søknaden", behandling.begrunnelse)
    }

    @Test
    fun `behandle om henleggelse uten begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().behandle(
                utfall = Utfall.Henlagt,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `behandle om innvilgelse med begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().behandle(
                utfall = Utfall.Innvilget,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Skal ikke være satt",
            )
        }
    }

    @Test
    fun `behandle på allerede innvilget søknad kaster`() {
        val alleredeInnvilget =
            lagSoknad().behandle(
                utfall = Utfall.Innvilget,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = null,
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.behandle(
                utfall = Utfall.Innvilget,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = brevDocument,
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `søknad uten søkte perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad(soktePerioder = emptyList())
        }
    }

    @Test
    fun `journalforBrev setter journalpostId på brevet`() {
        val innvilget =
            lagSoknad().behandle(
                utfall = Utfall.Innvilget,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = null,
            )
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = innvilget.journalforDokument(journalpostId, journalfortTidspunkt)

        val brev = assertNotNull(journalfort.behandling).dokument
        assertEquals(journalpostId, brev.journalpostId)
        assertEquals(journalfortTidspunkt, brev.journalfortTidspunkt)
    }

    @Test
    fun `journalforBrev på ubehandlet søknad kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().journalforDokument(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }

    @Test
    fun `behandling lager brev med brevtype som matcher utfallet`() {
        val henlagt =
            lagSoknad().behandle(
                utfall = Utfall.Henlagt,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Søker har trukket søknaden",
            )

        val brev = assertNotNull(henlagt.behandling).dokument
        assertEquals(Brevtype.HENLEGGELSE, brev.brevtype)
        assertEquals(brevDocument, brev.document)
    }

    @Test
    fun `distribuerBrev på ubehandlet søknad kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().distribuerDokument(OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }
}
