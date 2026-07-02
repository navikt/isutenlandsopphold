package no.nav.syfo.utenlandsopphold.domain

import java.time.Instant
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
            )

        assertEquals(SoknadStatus.INNVILGET, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.Innvilget, vedtak.utfall)
        assertEquals(veileder, vedtak.fattetAv)
        assertEquals(now, vedtak.fattetTidspunkt)
    }

    @Test
    fun `fattVedtak på allerede innvilget søknad kaster`() {
        val alleredeInnvilget =
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = Instant.parse("2026-01-10T12:00:00Z"),
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = Instant.parse("2026-01-11T12:00:00Z"),
            )
        }
    }

    @Test
    fun `søknad uten søkte perioder kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad(soktePerioder = emptyList())
        }
    }
}
