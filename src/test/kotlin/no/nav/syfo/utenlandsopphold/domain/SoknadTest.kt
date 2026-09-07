package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SoknadTest {
    @Test
    fun `fattVedtak om innvilgelse på mottatt søknad gir vedtak om innvilgelse`() {
        val now = OffsetDateTime.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = now,
                document = vedtakDocument,
                begrunnelse = null,
            )

        assertEquals(SoknadStatus.INNVILGET, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.Innvilget, vedtak.utfall)
        assertEquals(veileder, vedtak.fattetAv)
        assertEquals(now, vedtak.fattetTidspunkt)
        assertEquals(resultat.soktePerioder, vedtak.innvilgedePerioder)
        assertEquals(null, vedtak.begrunnelse)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse setter innvilgede perioder`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7))

        val resultat =
            lagSoknad().fattVedtak(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.DelvisInnvilget(listOf(innvilgetPeriode)), vedtak.utfall)
        assertEquals(listOf(innvilgetPeriode), vedtak.innvilgedePerioder)
        assertEquals("Delvis innvilget begrunnelse", vedtak.begrunnelse)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse kan gå på tvers av sammenhengende søkte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14)),
            )
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12))

        val resultat =
            lagSoknad(soktePerioder = soktePerioder).fattVedtak(
                utfall = Utfall.DelvisInnvilget(listOf(innvilgetPeriode)),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        assertEquals(listOf(innvilgetPeriode), resultat.vedtak?.innvilgedePerioder)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse kan ikke gå gjennom hull mellom søkte perioder`() {
        val soktePerioder =
            listOf(
                Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)),
                Periode(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 14)),
            )

        assertFailsWith<IllegalArgumentException> {
            lagSoknad(soktePerioder = soktePerioder).fattVedtak(
                utfall = Utfall.DelvisInnvilget(listOf(Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12)))),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med overlappende innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(
                            Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7)),
                            Periode(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9)),
                        ),
                    ),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse uten innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = Utfall.DelvisInnvilget(emptyList()),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med periode utenfor søkte perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall =
                    Utfall.DelvisInnvilget(
                        listOf(Periode(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 7))),
                    ),
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        }
    }

    @Test
    fun `fattVedtak om avslag gir avslag uten innvilgede perioder`() {
        val resultat =
            lagSoknad().fattVedtak(
                utfall = Utfall.Avslag,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Avslag begrunnelse",
            )

        assertEquals(SoknadStatus.AVSLAG, resultat.status)
        val vedtak = assertNotNull(resultat.vedtak)
        assertEquals(Utfall.Avslag, vedtak.utfall)
        assertEquals(emptyList(), vedtak.innvilgedePerioder)
        assertEquals("Avslag begrunnelse", vedtak.begrunnelse)
    }

    @Test
    fun `fattVedtak om avslag uten begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = Utfall.Avslag,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `fattVedtak om innvilgelse med begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = "Skal ikke være satt",
            )
        }
    }

    @Test
    fun `fattVedtak på allerede innvilget søknad kaster`() {
        val alleredeInnvilget =
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = null,
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = null,
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
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = null,
            )
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = innvilget.journalforVedtak(journalpostId, journalfortTidspunkt)

        val vedtak = assertNotNull(journalfort.vedtak)
        assertEquals(journalpostId, vedtak.journalpostId)
        assertEquals(journalfortTidspunkt, vedtak.journalfortTidspunkt)
    }

    @Test
    fun `journalforVedtak på søknad uten vedtak kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().journalforVedtak(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }

    @Test
    fun `henlegg på mottatt søknad gir status HENLAGT`() {
        val now = OffsetDateTime.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().henlegg(
                begrunnelse = "Søker har trukket søknaden",
                henlagtAv = veileder,
                now = now,
                document = henleggelseDocument,
            )

        assertEquals(SoknadStatus.HENLAGT, resultat.status)
        val henleggelse = assertNotNull(resultat.henleggelse)
        assertEquals("Søker har trukket søknaden", henleggelse.begrunnelse)
        assertEquals(veileder, henleggelse.henlagtAv)
        assertEquals(now, henleggelse.henlagtTidspunkt)
    }

    @Test
    fun `henlegg på søknad med vedtak kaster`() {
        val soknadMedVedtak =
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = null,
            )

        assertFailsWith<IllegalStateException> {
            soknadMedVedtak.henlegg(
                begrunnelse = "Søker har trukket søknaden",
                henlagtAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = henleggelseDocument,
            )
        }
    }

    @Test
    fun `henlegg på allerede henlagt søknad kaster`() {
        val alleredeHenlagt =
            lagSoknad().henlegg(
                begrunnelse = "Søker har trukket søknaden",
                henlagtAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = henleggelseDocument,
            )

        assertFailsWith<IllegalStateException> {
            alleredeHenlagt.henlegg(
                begrunnelse = "Ny begrunnelse",
                henlagtAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = henleggelseDocument,
            )
        }
    }

    @Test
    fun `fattVedtak på henlagt søknad kaster`() {
        val henlagt =
            lagSoknad().henlegg(
                begrunnelse = "Søker har trukket søknaden",
                henlagtAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = henleggelseDocument,
            )

        assertFailsWith<IllegalStateException> {
            henlagt.fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = null,
            )
        }
    }

    @Test
    fun `soknad med både vedtak og henleggelse kaster`() {
        val vedtak =
            Vedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                fattetTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9))),
                document = vedtakDocument,
            )
        val henleggelse =
            Henleggelse(
                begrunnelse = "Søker har trukket søknaden",
                henlagtAv = veileder,
                henlagtTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = henleggelseDocument,
            )

        assertFailsWith<IllegalStateException> {
            lagSoknad(vedtak = vedtak, henleggelse = henleggelse)
        }
    }

    @Test
    fun `journalforHenleggelse setter journalpostId på henleggelsen`() {
        val henlagt =
            lagSoknad().henlegg(
                begrunnelse = "Søker har trukket søknaden",
                henlagtAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = henleggelseDocument,
            )
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = henlagt.journalforHenleggelse(journalpostId, journalfortTidspunkt)

        val henleggelse = assertNotNull(journalfort.henleggelse)
        assertEquals(journalpostId, henleggelse.journalpostId)
        assertEquals(journalfortTidspunkt, henleggelse.journalfortTidspunkt)
    }

    @Test
    fun `journalforHenleggelse på søknad uten henleggelse kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().journalforHenleggelse(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }
}
