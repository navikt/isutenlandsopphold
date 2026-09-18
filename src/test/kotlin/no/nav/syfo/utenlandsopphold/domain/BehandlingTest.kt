package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BehandlingTest {
    private val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

    @Test
    fun `delvis innvilget behandling krever samme innvilgede perioder på utfall og behandling`() {
        val behandling =
            lagBehandling(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), behandling.utfall)
        assertEquals(listOf(innvilgetPeriode), behandling.innvilgedePerioder)
    }

    @Test
    fun `delvis innvilget behandling med avvikende innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9))),
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `delvis innvilget behandling uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.DelvisInnvilget(emptyList()), begrunnelse = "Begrunnelse")
        }
    }

    @Test
    fun `delvis innvilget behandling uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), begrunnelse = null)
        }
    }

    @Test
    fun `delvis innvilget behandling med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), begrunnelse = " ")
        }
    }

    @Test
    fun `avslått behandling med innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = Utfall.Avslag,
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = "Avslag begrunnelse",
            )
        }
    }

    @Test
    fun `avslått behandling uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.Avslag, begrunnelse = null)
        }
    }

    @Test
    fun `avslått behandling med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.Avslag, begrunnelse = "   ")
        }
    }

    @Test
    fun `henlagt behandling uten innvilgede perioder og med begrunnelse er gyldig`() {
        val behandling = lagBehandling(utfall = Utfall.Henlagt, begrunnelse = "Søker har trukket søknaden")

        assertEquals(Utfall.Henlagt, behandling.utfall)
    }

    @Test
    fun `henlagt behandling med innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = Utfall.Henlagt,
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = "Henleggelse begrunnelse",
            )
        }
    }

    @Test
    fun `henlagt behandling uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.Henlagt, begrunnelse = null)
        }
    }

    @Test
    fun `henlagt behandling med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.Henlagt, begrunnelse = "   ")
        }
    }

    @Test
    fun `innvilget behandling med begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.Innvilget, begrunnelse = "Skal ikke være satt")
        }
    }

    @Test
    fun `innvilget behandling uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.Innvilget, innvilgedePerioder = emptyList())
        }
    }

    @Test
    fun `behandling med brev som ikke matcher utfallet kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = Utfall.Henlagt,
                begrunnelse = "Begrunnelse",
                brev = lagBrev(brevtype = Brevtype.VEDTAK_AVSLAG),
            )
        }
    }

    /**
     * Kompilatoren håndhever klassifiseringen statisk, så denne testen fanger kun det den
     * ikke kan: at noen flytter et utfall inn i eller ut av [Utfall.Vedtak]
     * uten å mene det.
     */
    @Test
    fun `innvilgelse, delvis innvilgelse og avslag er realitetsbehandling, henleggelse er det ikke`() {
        val delvisInnvilget = Utfall.DelvisInnvilget(listOf(innvilgetPeriode))
        val alleUtfall: List<Utfall> = listOf(Utfall.Innvilget, delvisInnvilget, Utfall.Avslag, Utfall.Henlagt)

        val (realitetsbehandlinger, ovrige) = alleUtfall.partition { it is Utfall.Vedtak }

        assertEquals(listOf(Utfall.Innvilget, delvisInnvilget, Utfall.Avslag), realitetsbehandlinger)
        assertEquals(listOf(Utfall.Henlagt), ovrige)
    }

    @Test
    fun `brevtype utledes fra utfall`() {
        assertEquals(Brevtype.VEDTAK_INNVILGET, Utfall.Innvilget.brevtype())
        assertEquals(Brevtype.VEDTAK_DELVIS_INNVILGET, Utfall.DelvisInnvilget(listOf(innvilgetPeriode)).brevtype())
        assertEquals(Brevtype.VEDTAK_AVSLAG, Utfall.Avslag.brevtype())
        assertEquals(Brevtype.HENLEGGELSE, Utfall.Henlagt.brevtype())
    }
}
