package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BehandlingTest {
    private val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))

    @Test
    fun `innvilgelse uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> { BehandlingsUtfall.Innvilget(emptyList()) }
    }

    @Test
    fun `delvis innvilgelse uten innvilgede perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            BehandlingsUtfall.DelvisInnvilget(emptyList(), "Delvis innvilget begrunnelse")
        }
    }

    @Test
    fun `delvis innvilgelse med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            BehandlingsUtfall.DelvisInnvilget(listOf(innvilgetPeriode), " ")
        }
    }

    @Test
    fun `avslag med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> { BehandlingsUtfall.Avslag("   ") }
    }

    @Test
    fun `henleggelse med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> { BehandlingsUtfall.Henlagt("   ") }
    }

    @Test
    fun `innvilgede perioder leses fra innvilgelsesutfallene og er tomme ellers`() {
        assertEquals(listOf(innvilgetPeriode), BehandlingsUtfall.Innvilget(listOf(innvilgetPeriode)).innvilgedePerioder())
        assertEquals(
            listOf(innvilgetPeriode),
            BehandlingsUtfall.DelvisInnvilget(listOf(innvilgetPeriode), "Begrunnelse").innvilgedePerioder(),
        )
        assertEquals(emptyList(), BehandlingsUtfall.Avslag("Begrunnelse").innvilgedePerioder())
        assertEquals(emptyList(), BehandlingsUtfall.Henlagt("Begrunnelse").innvilgedePerioder())
        assertEquals(emptyList(), BehandlingsUtfall.IkkeAktuell(IkkeAktuellArsak.ANNET).innvilgedePerioder())
    }

    @Test
    fun `behandling med brev som ikke matcher utfallet kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = BehandlingsUtfall.Henlagt("Begrunnelse"),
                brev = lagBrev(brevtype = Brevtype.VEDTAK_AVSLAG),
            )
        }
    }

    @Test
    fun `behandling med utfall som skal ha brev kaster uten brev`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(utfall = BehandlingsUtfall.Henlagt("Begrunnelse"), brev = null)
        }
    }

    @Test
    fun `behandling merket ikke aktuell er gyldig uten brev`() {
        val behandling = lagBehandling(utfall = BehandlingsUtfall.IkkeAktuell(IkkeAktuellArsak.DUPLIKAT))

        assertEquals(null, behandling.brev)
        assertEquals(IkkeAktuellArsak.DUPLIKAT, (behandling.utfall as BehandlingsUtfall.IkkeAktuell).arsak)
    }

    @Test
    fun `behandling merket ikke aktuell med brev kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBehandling(
                utfall = BehandlingsUtfall.IkkeAktuell(IkkeAktuellArsak.ANNET),
                brev = lagBrev(brevtype = Brevtype.HENLEGGELSE),
            )
        }
    }

    /**
     * Kompilatoren håndhever klassifiseringen statisk, så denne testen fanger kun det den
     * ikke kan: at noen flytter et utfall inn i eller ut av [BehandlingsUtfall.Vedtak]
     * uten å mene det.
     */
    @Test
    fun `innvilgelse, delvis innvilgelse og avslag er vedtak, henleggelse er det ikke`() {
        val innvilget = BehandlingsUtfall.Innvilget(listOf(innvilgetPeriode))
        val delvisInnvilget = BehandlingsUtfall.DelvisInnvilget(listOf(innvilgetPeriode), "Delvis innvilget begrunnelse")
        val avslag = BehandlingsUtfall.Avslag("Avslag begrunnelse")
        val henlagt = BehandlingsUtfall.Henlagt("Henleggelse begrunnelse")
        val ikkeAktuell = BehandlingsUtfall.IkkeAktuell(IkkeAktuellArsak.DUPLIKAT)
        val alleUtfall: List<BehandlingsUtfall> =
            listOf(innvilget, delvisInnvilget, avslag, henlagt, ikkeAktuell)

        val (vedtak, ovrige) = alleUtfall.partition { it is BehandlingsUtfall.Vedtak }

        assertEquals(listOf(innvilget, delvisInnvilget, avslag), vedtak)
        assertEquals(listOf(henlagt, ikkeAktuell), ovrige)
    }

    @Test
    fun `brevtype utledes fra utfall`() {
        assertEquals(Brevtype.VEDTAK_INNVILGET, BehandlingsUtfall.Innvilget(listOf(innvilgetPeriode)).brevtype())
        assertEquals(
            Brevtype.VEDTAK_DELVIS_INNVILGET,
            BehandlingsUtfall.DelvisInnvilget(listOf(innvilgetPeriode), "Begrunnelse").brevtype(),
        )
        assertEquals(Brevtype.VEDTAK_AVSLAG, BehandlingsUtfall.Avslag("Begrunnelse").brevtype())
        assertEquals(Brevtype.HENLEGGELSE, BehandlingsUtfall.Henlagt("Begrunnelse").brevtype())
        assertEquals(null, BehandlingsUtfall.IkkeAktuell(IkkeAktuellArsak.ANNET).brevtype())
    }

    @Test
    fun `paakrevdBegrunnelse avviser manglende og blank begrunnelse`() {
        assertEquals("Begrunnelse", paakrevdBegrunnelse("Begrunnelse", "henleggelse"))
        assertFailsWith<IllegalArgumentException> { paakrevdBegrunnelse(null, "henleggelse") }
        assertFailsWith<IllegalArgumentException> { paakrevdBegrunnelse("   ", "henleggelse") }
    }
}
