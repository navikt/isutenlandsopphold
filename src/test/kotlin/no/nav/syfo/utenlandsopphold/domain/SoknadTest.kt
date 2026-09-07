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
    fun `fattVedtak om innvilgelse på mottatt søknad gir behandlingsutfall om innvilgelse`() {
        val now = OffsetDateTime.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = now,
                document = behandlingsutfallDocument,
                begrunnelse = null,
            )

        assertEquals(SoknadStatus.INNVILGET, resultat.status)
        val behandlingsutfall = assertNotNull(resultat.behandlingsutfall)
        assertEquals(Utfall.Innvilget, behandlingsutfall.utfall)
        assertEquals(veileder, behandlingsutfall.fattetAv)
        assertEquals(now, behandlingsutfall.fattetTidspunkt)
        assertEquals(resultat.soktePerioder, behandlingsutfall.innvilgedePerioder)
        assertEquals(null, behandlingsutfall.begrunnelse)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse setter innvilgede perioder`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7))

        val resultat =
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        val behandlingsutfall = assertNotNull(resultat.behandlingsutfall)
        assertEquals(Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), behandlingsutfall.utfall)
        assertEquals(listOf(innvilgetPeriode), behandlingsutfall.innvilgedePerioder)
        assertEquals("Delvis innvilget begrunnelse", behandlingsutfall.begrunnelse)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse kan gå på tvers av sammenhengende søkte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14)),
            )
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12))

        val resultat =
            lagSoknad(soktePerioder = soktePerioder).registrerBehandlingsutfall(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        assertEquals(listOf(innvilgetPeriode), resultat.behandlingsutfall?.innvilgedePerioder)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse kan ikke gå gjennom hull mellom søkte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 14)),
            )

        assertFailsWith<IllegalArgumentException> {
            lagSoknad(soktePerioder = soktePerioder).registrerBehandlingsutfall(
                utfall = Utfall.DelvisInnvilget(listOf(Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12)))),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med overlappende innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().registrerBehandlingsutfall(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(
                            Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7)),
                            Periode(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9)),
                        ),
                    ),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse uten innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.DelvisInnvilget(emptyList()),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med periode utenfor søkte perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().registrerBehandlingsutfall(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(Periode(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 7))),
                    ),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om avslag gir avslag uten innvilgede perioder`() {
        val resultat =
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Avslag,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Avslag begrunnelse",
            )

        assertEquals(SoknadStatus.AVSLAG, resultat.status)
        val behandlingsutfall = assertNotNull(resultat.behandlingsutfall)
        assertEquals(Utfall.Avslag, behandlingsutfall.utfall)
        assertEquals(emptyList(), behandlingsutfall.innvilgedePerioder)
        assertEquals("Avslag begrunnelse", behandlingsutfall.begrunnelse)
    }

    @Test
    fun `fattVedtak om avslag uten begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Avslag,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `fattVedtak om henleggelse gir henlagt status uten innvilgede perioder`() {
        val resultat =
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Henlagt,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Søker har trukket søknaden",
            )

        assertEquals(SoknadStatus.HENLAGT, resultat.status)
        val behandlingsutfall = assertNotNull(resultat.behandlingsutfall)
        assertEquals(Utfall.Henlagt, behandlingsutfall.utfall)
        assertEquals(emptyList(), behandlingsutfall.innvilgedePerioder)
        assertEquals("Søker har trukket søknaden", behandlingsutfall.begrunnelse)
    }

    @Test
    fun `fattVedtak om henleggelse uten begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Henlagt,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `fattVedtak om innvilgelse med begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = "Skal ikke være satt",
            )
        }
    }

    @Test
    fun `fattVedtak på allerede innvilget søknad kaster`() {
        val alleredeInnvilget =
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = null,
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.registrerBehandlingsutfall(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = behandlingsutfallDocument,
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
    fun `journalforVedtak setter journalpostId på vedtaket`() {
        val innvilget =
            lagSoknad().registrerBehandlingsutfall(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = behandlingsutfallDocument,
                begrunnelse = null,
            )
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = innvilget.journalforBehandlingsutfall(journalpostId, journalfortTidspunkt)

        val behandlingsutfall = assertNotNull(journalfort.behandlingsutfall)
        assertEquals(journalpostId, behandlingsutfall.journalpostId)
        assertEquals(journalfortTidspunkt, behandlingsutfall.journalfortTidspunkt)
    }

    @Test
    fun `journalforVedtak på søknad uten behandlingsutfall kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().journalforBehandlingsutfall(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }
}
