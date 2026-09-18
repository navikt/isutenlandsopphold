package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BehandlingTest {
    private val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

    @Test
    fun `innvilgelse uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> { Utfall.Innvilget(emptyList()) }
    }

    @Test
    fun `delvis innvilgelse uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            Utfall.DelvisInnvilget(emptyList(), "Delvis innvilget begrunnelse")
        }
    }

    @Test
    fun `delvis innvilgelse med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            Utfall.DelvisInnvilget(listOf(innvilgetPeriode), " ")
        }
    }

    @Test
    fun `avslag med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> { Utfall.Avslag("   ") }
    }

    @Test
    fun `henleggelse med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> { Utfall.Henlagt("   ") }
    }

    @Test
    fun `innvilgede perioder leses fra innvilgelsesutfallene og er tomme ellers`() {
        assertEquals(listOf(innvilgetPeriode), Utfall.Innvilget(listOf(innvilgetPeriode)).innvilgedePerioder())
        assertEquals(
            listOf(innvilgetPeriode),
            Utfall.DelvisInnvilget(listOf(innvilgetPeriode), "Begrunnelse").innvilgedePerioder(),
        )
        assertEquals(emptyList(), Utfall.Avslag("Begrunnelse").innvilgedePerioder())
        assertEquals(emptyList(), Utfall.Henlagt("Begrunnelse").innvilgedePerioder())
        assertEquals(emptyList(), Utfall.IkkeAktuell(IkkeAktuellGrunn.ANNET).innvilgedePerioder())
    }

    @Test
    fun `behandling med brev som ikke matcher utfallet kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = Utfall.Henlagt("Begrunnelse"),
                brev = lagBrev(brevtype = Brevtype.VEDTAK_AVSLAG),
            )
        }
    }

    @Test
    fun `behandling med utfall som skal ha brev kaster uten brev`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = Utfall.Henlagt("Begrunnelse"), brev = null)
        }
    }

    @Test
    fun `behandling merket ikke aktuell er gyldig uten brev`() {
        val behandling = lagBehandling(utfall = Utfall.IkkeAktuell(IkkeAktuellGrunn.DUPLIKAT))

        assertEquals(null, behandling.brev)
        assertEquals(IkkeAktuellGrunn.DUPLIKAT, (behandling.utfall as Utfall.IkkeAktuell).grunn)
    }

    @Test
    fun `behandling merket ikke aktuell med brev kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = Utfall.IkkeAktuell(IkkeAktuellGrunn.ANNET),
                brev = lagBrev(brevtype = Brevtype.HENLEGGELSE),
            )
        }
    }

    /**
     * Kompilatoren håndhever klassifiseringen statisk, så denne testen fanger kun det den
     * ikke kan: at noen flytter et utfall inn i eller ut av [Utfall.Vedtak]
     * uten å mene det.
     */
    @Test
    fun `innvilgelse, delvis innvilgelse og avslag er vedtak, henleggelse er det ikke`() {
        val innvilget = Utfall.Innvilget(listOf(innvilgetPeriode))
        val delvisInnvilget = Utfall.DelvisInnvilget(listOf(innvilgetPeriode), "Delvis innvilget begrunnelse")
        val avslag = Utfall.Avslag("Avslag begrunnelse")
        val henlagt = Utfall.Henlagt("Henleggelse begrunnelse")
        val ikkeAktuell = Utfall.IkkeAktuell(IkkeAktuellGrunn.DUPLIKAT)
        val alleUtfall: List<Utfall> =
            listOf(innvilget, delvisInnvilget, avslag, henlagt, ikkeAktuell)

        val (vedtak, ovrige) = alleUtfall.partition { it is Utfall.Vedtak }

        assertEquals(listOf(innvilget, delvisInnvilget, avslag), vedtak)
        assertEquals(listOf(henlagt, ikkeAktuell), ovrige)
    }

    @Test
    fun `brevtype utledes fra utfall`() {
        assertEquals(Brevtype.VEDTAK_INNVILGET, Utfall.Innvilget(listOf(innvilgetPeriode)).brevtype())
        assertEquals(
            Brevtype.VEDTAK_DELVIS_INNVILGET,
            Utfall.DelvisInnvilget(listOf(innvilgetPeriode), "Begrunnelse").brevtype(),
        )
        assertEquals(Brevtype.VEDTAK_AVSLAG, Utfall.Avslag("Begrunnelse").brevtype())
        assertEquals(Brevtype.HENLEGGELSE, Utfall.Henlagt("Begrunnelse").brevtype())
        assertEquals(null, Utfall.IkkeAktuell(IkkeAktuellGrunn.ANNET).brevtype())
    }

    @Test
    fun `VedtakOppretting from godtar de tre vedtaksutfallene, men ikke henleggelse eller ikke aktuell`() {
        assertEquals(VedtakOppretting.Innvilgelse, VedtakOppretting.from("INNVILGET", emptyList(), begrunnelse = null))
        assertEquals(
            VedtakOppretting.DelvisInnvilgelse(listOf(innvilgetPeriode), "Begrunnelse"),
            VedtakOppretting.from("DELVIS_INNVILGET", listOf(innvilgetPeriode), begrunnelse = "Begrunnelse"),
        )
        assertEquals(
            VedtakOppretting.Avslag("Begrunnelse"),
            VedtakOppretting.from("AVSLAG", emptyList(), begrunnelse = "Begrunnelse"),
        )

        assertFailsWith<IllegalArgumentException> {
            VedtakOppretting.from("HENLAGT", emptyList(), begrunnelse = "Begrunnelse")
        }
        assertFailsWith<IllegalArgumentException> {
            VedtakOppretting.from("IKKE_AKTUELL", emptyList(), begrunnelse = null)
        }
    }

    /**
     * Begrunnelsen kommer inn som nullbar fra API-et. Fabrikken er derfor det eneste
     * stedet et manglende eller overflødig begrunnelsesfelt kan oppstå i praksis.
     */
    @Test
    fun `VedtakOppretting from krever begrunnelse der utfallet trenger det, og avviser den ellers`() {
        assertFailsWith<IllegalArgumentException> {
            VedtakOppretting.from("INNVILGET", emptyList(), begrunnelse = "Skal ikke være satt")
        }
        assertFailsWith<IllegalArgumentException> {
            VedtakOppretting.from("AVSLAG", emptyList(), begrunnelse = null)
        }
        assertFailsWith<IllegalArgumentException> {
            VedtakOppretting.from("DELVIS_INNVILGET", listOf(innvilgetPeriode), begrunnelse = null)
        }
    }

    @Test
    fun `VedtakOppretting from avviser innvilgede perioder ved avslag`() {
        assertFailsWith<IllegalArgumentException> {
            VedtakOppretting.from("AVSLAG", listOf(innvilgetPeriode), begrunnelse = "Begrunnelse")
        }
    }

    @Test
    fun `paakrevdBegrunnelse avviser manglende og blank begrunnelse`() {
        assertEquals("Begrunnelse", paakrevdBegrunnelse("Begrunnelse", "henleggelse"))
        assertFailsWith<IllegalArgumentException> { paakrevdBegrunnelse(null, "henleggelse") }
        assertFailsWith<IllegalArgumentException> { paakrevdBegrunnelse("   ", "henleggelse") }
    }
}
