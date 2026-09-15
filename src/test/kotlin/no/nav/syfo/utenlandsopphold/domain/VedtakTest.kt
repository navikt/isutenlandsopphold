package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrevTest {
    @Test
    fun `nytt brev er verken journalført eller distribuert`() {
        val brev = lagBrev()

        assertFalse(brev.erJournalfort)
        assertFalse(brev.erDistribuert)
    }

    @Test
    fun `brev uten dokumentinnhold kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagBrev(document = emptyList())
        }
    }

    @Test
    fun `journalfor setter journalpostId og journalfortTidspunkt`() {
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = lagBrev().journalfor(journalpostId, journalfortTidspunkt)

        assertTrue(journalfort.erJournalfort)
        assertEquals(journalpostId, journalfort.journalpostId)
        assertEquals(journalfortTidspunkt, journalfort.journalfortTidspunkt)
    }

    @Test
    fun `journalfor på allerede journalført brev kaster`() {
        val journalfort = lagBrev().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            journalfort.journalfor(JournalpostId("456"), OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `distribuer på ikke-journalført brev kaster`() {
        assertFailsWith<IllegalStateException> {
            lagBrev().distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `distribuer setter distribuertTidspunkt for journalført brev`() {
        val journalfort = lagBrev().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        val distribuertTidspunkt = OffsetDateTime.parse("2026-01-12T08:00:00Z")

        val distribuert = journalfort.distribuer(distribuertTidspunkt)

        assertTrue(distribuert.erDistribuert)
        assertEquals(distribuertTidspunkt, distribuert.distribuertTidspunkt)
    }

    @Test
    fun `distribuer på allerede distribuert brev kaster`() {
        val distribuert =
            lagBrev()
                .journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
                .distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            distribuert.distribuer(OffsetDateTime.parse("2026-01-13T08:00:00Z"))
        }
    }
}
