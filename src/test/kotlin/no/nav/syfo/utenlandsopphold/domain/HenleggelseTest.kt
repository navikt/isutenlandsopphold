package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HenleggelseTest {
    private fun lagHenleggelse(begrunnelse: String = "Trukket av søker") =
        Henleggelse(
            fattetAv = veileder,
            fattetTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            begrunnelse = begrunnelse,
            utsending = Utsending(document = henleggelseDocument),
        )

    @Test
    fun `henleggelse uten begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagHenleggelse(begrunnelse = "")
        }
    }

    @Test
    fun `henleggelse med blank begrunnelse kaster`() {
        assertFailsWith<IllegalArgumentException> {
            lagHenleggelse(begrunnelse = "   ")
        }
    }

    @Test
    fun `nyfattet henleggelse er ikke journalført eller distribuert`() {
        val henleggelse = lagHenleggelse()

        assertFalse(henleggelse.erJournalfort)
        assertFalse(henleggelse.erDistribuert)
    }

    @Test
    fun `journalfor setter journalpostId og journalfortTidspunkt`() {
        val henleggelse = lagHenleggelse()
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = henleggelse.journalfor(journalpostId, journalfortTidspunkt)

        assertTrue(journalfort.erJournalfort)
        assertEquals(journalpostId, journalfort.journalpostId)
        assertEquals(journalfortTidspunkt, journalfort.journalfortTidspunkt)
    }

    @Test
    fun `journalfor på allerede journalført henleggelse kaster`() {
        val journalfort = lagHenleggelse().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            journalfort.journalfor(JournalpostId("456"), OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `distribuer på ikke-journalført henleggelse kaster`() {
        val henleggelse = lagHenleggelse()

        assertFailsWith<IllegalStateException> {
            henleggelse.distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))
        }
    }

    @Test
    fun `distribuer setter distribuertTidspunkt for journalført henleggelse`() {
        val journalfort = lagHenleggelse().journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        val distribuertTidspunkt = OffsetDateTime.parse("2026-01-12T08:00:00Z")

        val distribuert = journalfort.distribuer(distribuertTidspunkt)

        assertTrue(distribuert.erDistribuert)
        assertEquals(distribuertTidspunkt, distribuert.distribuertTidspunkt)
    }

    @Test
    fun `distribuer på allerede distribuert henleggelse kaster`() {
        val distribuert =
            lagHenleggelse()
                .journalfor(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
                .distribuer(OffsetDateTime.parse("2026-01-12T08:00:00Z"))

        assertFailsWith<IllegalStateException> {
            distribuert.distribuer(OffsetDateTime.parse("2026-01-13T08:00:00Z"))
        }
    }
}
