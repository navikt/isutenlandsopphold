package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VedtakTest {
    private fun lagVedtak() =
        Vedtak(
            utfall = Utfall.Innvilget,
            fattetAv = veileder,
            fattetTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9))),
            document = vedtakDocument,
        )

    @Test
    fun `nyfattet vedtak er ikke journalført`() {
        val vedtak = lagVedtak()

        assertFalse(vedtak.erJournalfort)
    }

    @Test
    fun `delvis innvilget vedtak krever samme innvilgede perioder på utfall og vedtak`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

        val vedtak =
            lagVedtak().copy(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), vedtak.utfall)
        assertEquals(listOf(innvilgetPeriode), vedtak.innvilgedePerioder)
    }

    @Test
    fun `delvis innvilget vedtak uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(
                utfall = Utfall.DelvisInnvilget(emptyList()),
                innvilgedePerioder = emptyList(),
            )
        }
    }

    @Test
    fun `avslatt vedtak med innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(utfall = Utfall.Avslag, begrunnelse = "Avslag begrunnelse")
        }
    }

    @Test
    fun `avslatt vedtak uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(utfall = Utfall.Avslag, innvilgedePerioder = emptyList(), begrunnelse = null)
        }
    }

    @Test
    fun `avslatt vedtak med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(utfall = Utfall.Avslag, innvilgedePerioder = emptyList(), begrunnelse = "   ")
        }
    }

    @Test
    fun `henlagt vedtak uten innvilgede perioder og med begrunnelse er gyldig`() {
        val vedtak =
            lagVedtak().copy(
                utfall = Utfall.Henlagt,
                innvilgedePerioder = emptyList(),
                begrunnelse = "Søker har trukket søknaden",
            )

        assertEquals(Utfall.Henlagt, vedtak.utfall)
    }

    @Test
    fun `henlagt vedtak med innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(utfall = Utfall.Henlagt, begrunnelse = "Henleggelse begrunnelse")
        }
    }

    @Test
    fun `henlagt vedtak uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(utfall = Utfall.Henlagt, innvilgedePerioder = emptyList(), begrunnelse = null)
        }
    }

    @Test
    fun `henlagt vedtak med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(utfall = Utfall.Henlagt, innvilgedePerioder = emptyList(), begrunnelse = "   ")
        }
    }

    @Test
    fun `delvis innvilget vedtak uten begrunnelse kaster`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `delvis innvilget vedtak med blank begrunnelse kaster`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = " ",
            )
        }
    }

    @Test
    fun `innvilget vedtak med begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagVedtak().copy(begrunnelse = "Skal ikke være satt")
        }
    }

    @Test
    fun `journalfor setter journalpostId og journalfortTidspunkt`() {
        val vedtak = lagVedtak()
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = vedtak.journalfor(journalpostId, journalfortTidspunkt)

        assertTrue(journalfort.erJournalfort)
        assertEquals(journalpostId, journalfort.journalpostId)
        assertEquals(journalfortTidspunkt, journalfort.journalfortTidspunkt)
    }

    @Test
    fun `journalfor på allerede journalført vedtak kaster`() {
        val journalfort = lagVedtak().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            journalfort.journalfor(JournalpostId("456"), OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `nyfattet vedtak er ikke distribuert`() {
        val vedtak = lagVedtak()

        assertFalse(vedtak.erDistribuert)
    }

    @Test
    fun `distribuer på ikke-journalført vedtak kaster`() {
        val vedtak = lagVedtak()

        assertFailsWith<IllegalStateException> {
            vedtak.distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `distribuer setter distribuertTidspunkt for journalført vedtak`() {
        val journalfort = lagVedtak().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        val distribuertTidspunkt = OffsetDateTime.parse("2026-01-12T08:00:00Z")

        val distribuert = journalfort.distribuer(distribuertTidspunkt)

        assertTrue(distribuert.erDistribuert)
        assertEquals(distribuertTidspunkt, distribuert.distribuertTidspunkt)
    }

    @Test
    fun `distribuer på allerede distribuert vedtak kaster`() {
        val distribuert =
            lagVedtak()
                .journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
                .distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            distribuert.distribuer(OffsetDateTime.parse("2026-01-13T08:00:00Z"))
        }
    }
}
