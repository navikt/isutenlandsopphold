package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
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
                document = vedtakDocument,
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
