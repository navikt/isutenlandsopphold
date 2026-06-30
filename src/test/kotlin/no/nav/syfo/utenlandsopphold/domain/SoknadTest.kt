package no.nav.syfo.utenlandsopphold.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SoknadTest {
    @Test
    fun `fattVedtak paa mottatt soknad gir innvilget med vedtak`() {
        val naa = Instant.parse("2026-01-10T12:00:00Z")

        val resultat =
            soknad().fattVedtak(
                utfall = Utfall.FullInnvilgelse,
                av = veileder,
                naa = naa,
            )

        assertEquals(SoknadStatus.INNVILGET, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.FullInnvilgelse, vedtak.utfall)
        assertEquals(veileder, vedtak.fattetAv)
        assertEquals(naa, vedtak.fattetTidspunkt)
    }

    @Test
    fun `fattVedtak paa allerede innvilget soknad kaster`() {
        val alleredeInnvilget =
            soknad().fattVedtak(
                utfall = Utfall.FullInnvilgelse,
                av = veileder,
                naa = Instant.parse("2026-01-10T12:00:00Z"),
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.fattVedtak(
                utfall = Utfall.FullInnvilgelse,
                av = veileder,
                naa = Instant.parse("2026-01-11T12:00:00Z"),
            )
        }
    }

    @Test
    fun `soknad uten soekte perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            soknad(soktePerioder = emptyList())
        }
    }
}
