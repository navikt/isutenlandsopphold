package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VedtakTest {
    private fun lagVedtak() =
        Vedtak(
            utfall = Utfall.FullInnvilgelse,
            fattetAv = veileder,
            fattetTidspunkt = Instant.parse("2026-01-10T12:00:00Z"),
        )

    @Test
    fun `nyfattet vedtak er ikke journalført`() {
        val vedtak = lagVedtak()

        assertFalse(vedtak.erJournalfort)
    }

    @Test
    fun `journalfor setter journalpostId og journalfortTidspunkt`() {
        val vedtak = lagVedtak()
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = Instant.parse("2026-01-11T08:00:00Z")

        val journalfort = vedtak.journalfor(journalpostId, journalfortTidspunkt)

        assertTrue(journalfort.erJournalfort)
        assertEquals(journalpostId, journalfort.journalpostId)
        assertEquals(journalfortTidspunkt, journalfort.journalfortTidspunkt)
    }

    @Test
    fun `journalfor på allerede journalført vedtak kaster`() {
        val journalfort = lagVedtak().journalfor(JournalpostId("123"), Instant.parse("2026-01-11T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            journalfort.journalfor(JournalpostId("456"), Instant.parse("2026-01-12T08:00:00Z"))
        }
    }
}
