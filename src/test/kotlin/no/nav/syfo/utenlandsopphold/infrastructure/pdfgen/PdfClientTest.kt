package no.nav.syfo.utenlandsopphold.infrastructure.pdfgen

import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Henleggelse
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Utsending
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import no.nav.syfo.utenlandsopphold.domain.henleggelseDocument
import no.nav.syfo.utenlandsopphold.domain.vedtakDocument
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_AVSLAG_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_DELVIS_INNVILGET_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_HENLEGGELSE_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.PDF_INNVILGET_BYTES
import no.nav.syfo.utenlandsopphold.infrastructure.mock.mockPdfClient
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfClientTest {
    private val testPersonident = Personident("11111111111")
    private val pdfClient = mockPdfClient()

    @Test
    fun `henter innvilget-pdf ved innvilgelse`() =
        runTest {
            val vedtak =
                Vedtak(
                    utfall = Utfall.Innvilget,
                    fattetAv = Navident("Z999999"),
                    fattetTidspunkt = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
                    innvilgedePerioder = listOf(Periode(fom = LocalDate.of(2026, 1, 1), tom = LocalDate.of(2026, 1, 5))),
                    utsending = Utsending(document = vedtakDocument),
                )

            val pdf =
                pdfClient.createPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    behandlingsutfall = vedtak,
                    datoSendt = LocalDate.of(2026, 1, 6),
                )

            assertTrue(PDF_INNVILGET_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter delvis-innvilget-pdf ved delvis innvilgelse`() =
        runTest {
            val innvilgedePerioder = listOf(Periode(fom = LocalDate.of(2026, 1, 1), tom = LocalDate.of(2026, 1, 5)))
            val delvisInnvilget =
                Vedtak(
                    utfall = Utfall.DelvisInnvilget(innvilgedePerioder = innvilgedePerioder),
                    fattetAv = Navident("Z999999"),
                    fattetTidspunkt = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
                    innvilgedePerioder = innvilgedePerioder,
                    begrunnelse = "Delvis innvilget begrunnelse",
                    utsending = Utsending(document = vedtakDocument),
                )

            val pdf =
                pdfClient.createPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    behandlingsutfall = delvisInnvilget,
                    datoSendt = LocalDate.of(2026, 1, 6),
                )

            assertTrue(PDF_DELVIS_INNVILGET_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter avslag-pdf ved avslag`() =
        runTest {
            val avslag =
                Vedtak(
                    utfall = Utfall.Avslag,
                    fattetAv = Navident("Z999999"),
                    fattetTidspunkt = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
                    innvilgedePerioder = emptyList(),
                    begrunnelse = "Avslag begrunnelse",
                    utsending = Utsending(document = vedtakDocument),
                )

            val pdf =
                pdfClient.createPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    behandlingsutfall = avslag,
                    datoSendt = LocalDate.of(2026, 1, 6),
                )

            assertTrue(PDF_AVSLAG_BYTES.contentEquals(pdf))
        }

    @Test
    fun `henter henleggelse-pdf ved henleggelse`() =
        runTest {
            val henleggelse =
                Henleggelse(
                    fattetAv = Navident("Z999999"),
                    fattetTidspunkt = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
                    begrunnelse = "Trukket av søker",
                    utsending = Utsending(document = henleggelseDocument),
                )

            val pdf =
                pdfClient.createPdf(
                    mottakerFodselsnummer = testPersonident,
                    mottakerNavn = "Ola Nordmann",
                    behandlingsutfall = henleggelse,
                    datoSendt = LocalDate.of(2026, 1, 6),
                )

            assertTrue(PDF_HENLEGGELSE_BYTES.contentEquals(pdf))
        }
}
