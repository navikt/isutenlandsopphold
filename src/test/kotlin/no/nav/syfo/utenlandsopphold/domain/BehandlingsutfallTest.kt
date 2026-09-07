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
    private fun lagBehandlingsutfall() =
        Behandlingsutfall(
            utfall = Utfall.Innvilget,
            fattetAv = veileder,
            fattetTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9))),
            document = behandlingsutfallDocument,
        )

    @Test
    fun `nyfattet behandlingsutfall er ikke journalført`() {
        val behandlingsutfall = lagBehandlingsutfall()

        assertFalse(behandlingsutfall.erJournalfort)
    }

    @Test
    fun `delvis innvilget behandlingsutfall krever samme innvilgede perioder på utfall og behandlingsutfall`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

        val behandlingsutfall =
            lagBehandlingsutfall().copy(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), behandlingsutfall.utfall)
        assertEquals(listOf(innvilgetPeriode), behandlingsutfall.innvilgedePerioder)
    }

    @Test
    fun `delvis innvilget behandlingsutfall uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(
                utfall = Utfall.DelvisInnvilget(emptyList()),
                innvilgedePerioder = emptyList(),
            )
        }
    }

    @Test
    fun `avslatt behandlingsutfall med innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(utfall = Utfall.Avslag, begrunnelse = "Avslag begrunnelse")
        }
    }

    @Test
    fun `avslatt behandlingsutfall uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(utfall = Utfall.Avslag, innvilgedePerioder = emptyList(), begrunnelse = null)
        }
    }

    @Test
    fun `avslatt behandlingsutfall med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(utfall = Utfall.Avslag, innvilgedePerioder = emptyList(), begrunnelse = "   ")
        }
    }

    @Test
    fun `henlagt behandlingsutfall uten innvilgede perioder og med begrunnelse er gyldig`() {
        val behandlingsutfall =
            lagBehandlingsutfall().copy(
                utfall = Utfall.Henlagt,
                innvilgedePerioder = emptyList(),
                begrunnelse = "Søker har trukket søknaden",
            )

        assertEquals(Utfall.Henlagt, behandlingsutfall.utfall)
    }

    @Test
    fun `henlagt behandlingsutfall med innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(utfall = Utfall.Henlagt, begrunnelse = "Henleggelse begrunnelse")
        }
    }

    @Test
    fun `henlagt behandlingsutfall uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(utfall = Utfall.Henlagt, innvilgedePerioder = emptyList(), begrunnelse = null)
        }
    }

    @Test
    fun `henlagt behandlingsutfall med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(utfall = Utfall.Henlagt, innvilgedePerioder = emptyList(), begrunnelse = "   ")
        }
    }

    @Test
    fun `delvis innvilget behandlingsutfall uten begrunnelse kaster`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `delvis innvilget behandlingsutfall med blank begrunnelse kaster`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = " ",
            )
        }
    }

    @Test
    fun `innvilget behandlingsutfall med begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandlingsutfall().copy(begrunnelse = "Skal ikke være satt")
        }
    }

    @Test
    fun `journalfor setter journalpostId og journalfortTidspunkt`() {
        val behandlingsutfall = lagBehandlingsutfall()
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = behandlingsutfall.journalfor(journalpostId, journalfortTidspunkt)

        assertTrue(journalfort.erJournalfort)
        assertEquals(journalpostId, journalfort.journalpostId)
        assertEquals(journalfortTidspunkt, journalfort.journalfortTidspunkt)
    }

    @Test
    fun `journalfor på allerede journalført behandlingsutfall kaster`() {
        val journalfort = lagBehandlingsutfall().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            journalfort.journalfor(JournalpostId("456"), OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `nyfattet behandlingsutfall er ikke distribuert`() {
        val behandlingsutfall = lagBehandlingsutfall()

        assertFalse(behandlingsutfall.erDistribuert)
    }

    @Test
    fun `distribuer på ikke-journalført behandlingsutfall kaster`() {
        val behandlingsutfall = lagBehandlingsutfall()

        assertFailsWith<IllegalStateException> {
            behandlingsutfall.distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `distribuer setter distribuertTidspunkt for journalført behandlingsutfall`() {
        val journalfort = lagBehandlingsutfall().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        val distribuertTidspunkt = OffsetDateTime.parse("2026-01-12T08:00:00Z")

        val distribuert = journalfort.distribuer(distribuertTidspunkt)

        assertTrue(distribuert.erDistribuert)
        assertEquals(distribuertTidspunkt, distribuert.distribuertTidspunkt)
    }

    @Test
    fun `distribuer på allerede distribuert behandlingsutfall kaster`() {
        val distribuert =
            lagBehandlingsutfall()
                .journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
                .distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            distribuert.distribuer(OffsetDateTime.parse("2026-01-13T08:00:00Z"))
        }
    }
}
