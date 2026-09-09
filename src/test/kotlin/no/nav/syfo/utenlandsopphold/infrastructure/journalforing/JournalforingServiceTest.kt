package no.nav.syfo.utenlandsopphold.infrastructure.journalforing

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.journalforing.client.DokarkivClient
import no.nav.syfo.common.journalforing.dto.JournalpostRequest
import no.nav.syfo.common.journalforing.dto.JournalpostResponse
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.JournalforingDokumenttype
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalforingServiceTest {
    private val dokarkivClient = mockk<DokarkivClient>()
    private val service =
        JournalforingService(
            dokarkivClient = dokarkivClient,
            isJournalforingRetryEnabled = true,
        )

    @Test
    fun `journalfører vedtak med vedtakskode og vedtakstittel`() =
        runTest {
            val request = journalforOgFangRequest(JournalforingDokumenttype.VEDTAK)

            assertEquals("Vedtak om utenlandsopphold", request.tittel)
            assertEquals("OPPF_VEDTAK_UTENLANDSOPPHOLD", request.dokumenter.single().brevkode)
            assertEquals("Vedtak om utenlandsopphold", request.dokumenter.single().tittel)
        }

    @Test
    fun `journalfører henleggelse med henleggelseskode og henleggelsestittel`() =
        runTest {
            val request = journalforOgFangRequest(JournalforingDokumenttype.HENLEGGELSE)

            assertEquals("Henleggelse av søknad om utenlandsopphold", request.tittel)
            assertEquals("OPPF_HENLEGGELSE_UTENLANDSOPPHOLD", request.dokumenter.single().brevkode)
            assertEquals("Henleggelse av søknad om utenlandsopphold", request.dokumenter.single().tittel)
        }

    private suspend fun journalforOgFangRequest(dokumenttype: JournalforingDokumenttype): JournalpostRequest {
        val requestSlot = slot<JournalpostRequest>()
        coEvery { dokarkivClient.journalfor(capture(requestSlot)) } returns
            JournalpostResponse(
                journalpostId = JournalpostId("999"),
                journalstatus = "FERDIGSTILT",
            )

        service
            .journalfor(
                personident = Personident("11111111111"),
                pdf = byteArrayOf(1, 2, 3),
                eksternReferanseId = "ekstern-referanse",
                dokumenttype = dokumenttype,
            ).getOrThrow()

        coVerify(exactly = 1) { dokarkivClient.journalfor(any()) }
        return requestSlot.captured
    }
}
