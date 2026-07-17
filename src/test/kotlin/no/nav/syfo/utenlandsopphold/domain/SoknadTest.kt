package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SoknadTest {
    @Test
    fun `fattVedtak om innvilgelse på mottatt søknad gir vedtak om innvilgelse`() {
        val now = Instant.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = now,
                document = vedtakDocument,
            )

        assertEquals(SoknadStatus.INNVILGET, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.Innvilget, vedtak.utfall)
        assertEquals(veileder, vedtak.fattetAv)
        assertEquals(now, vedtak.fattetTidspunkt)
        assertEquals(resultat.soktePerioder, vedtak.innvilgetePerioder)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse lagrer innvilgede perioder`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7))

        val resultat =
            lagSoknad().fattVedtak(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), vedtak.utfall)
        assertEquals(listOf(innvilgetPeriode), vedtak.innvilgetePerioder)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse kan gaa paa tvers av sammenhengende sokte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14)),
            )
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12))

        val resultat =
            lagSoknad(soktePerioder = soktePerioder).fattVedtak(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        assertEquals(listOf(innvilgetPeriode), resultat.vedtak?.innvilgetePerioder)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse kan ikke gaa gjennom hull mellom sokte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 14)),
            )

        assertFailsWith<IllegalArgumentException> {
            lagSoknad(soktePerioder = soktePerioder).fattVedtak(
                utfall = Utfall.DelvisInnvilget(listOf(Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12)))),
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med overlappende innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(
                            Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7)),
                            Periode(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9)),
                        ),
                    ),
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = Utfall.DelvisInnvilget(emptyList()),
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med periode utenfor sokte perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(Periode(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 7))),
                    ),
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om avslag gir avslag uten innvilgede perioder`() {
        val resultat =
            lagSoknad().fattVedtak(
                utfall = Utfall.Avslag,
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )

        assertEquals(SoknadStatus.AVSLATT, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.Avslag, vedtak.utfall)
        assertEquals(emptyList(), vedtak.innvilgetePerioder)
    }

    @Test
    fun `fattVedtak på allerede innvilget søknad kaster`() {
        val alleredeInnvilget =
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = Instant.parse("2026-01-11T12:00:00Z"),
                document = vedtakDocument,
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
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
            )
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = Instant.parse("2026-01-11T08:00:00Z")

        val journalfort = innvilget.journalforVedtak(journalpostId, journalfortTidspunkt)

        val vedtak = assertNotNull(journalfort.vedtak)
        assertEquals(journalpostId, vedtak.journalpostId)
        assertEquals(journalfortTidspunkt, vedtak.journalfortTidspunkt)
    }

    @Test
    fun `journalforVedtak på søknad uten vedtak kaster`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().journalforVedtak(JournalpostId("123"), Instant.parse("2026-01-11T08:00:00Z"))
        }
    }
}
