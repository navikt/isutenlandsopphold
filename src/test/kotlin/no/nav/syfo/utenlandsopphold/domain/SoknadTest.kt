package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        val vedtak = assertNotNull(resultat.behandlingsutfall as? Vedtak)
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
        val vedtak = assertNotNull(resultat.behandlingsutfall as? Vedtak)
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
        assertEquals(listOf(innvilgetPeriode), (resultat.behandlingsutfall as? Vedtak)?.innvilgedePerioder)
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
        val vedtak = assertNotNull(resultat.behandlingsutfall as? Vedtak)
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
    fun `journalforBehandlingsutfall setter journalpostId på vedtaket`() {
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

        val journalfort = innvilget.journalforBehandlingsutfall(journalpostId, journalfortTidspunkt)

        val vedtak = assertNotNull(journalfort.behandlingsutfall)
        assertEquals(journalpostId, vedtak.journalpostId)
        assertEquals(journalfortTidspunkt, vedtak.journalfortTidspunkt)
    }

    @Test
    fun `journalforBehandlingsutfall på søknad uten behandlingsutfall kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().journalforBehandlingsutfall(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }

    @Test
    fun `henlegg på mottatt søknad gir henleggelse og status HENLAGT`() {
        val now = OffsetDateTime.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().henlegg(
                fattetAv = veileder,
                now = now,
                document = henleggelseDocument,
                begrunnelse = "Trukket av søker",
            )

        assertEquals(SoknadStatus.HENLAGT, resultat.status)
        val henleggelse = assertNotNull(resultat.behandlingsutfall as? Henleggelse)
        assertEquals(veileder, henleggelse.fattetAv)
        assertEquals(now, henleggelse.fattetTidspunkt)
        assertEquals("Trukket av søker", henleggelse.begrunnelse)
    }

    @Test
    fun `henlegg på allerede behandlet søknad kaster`() {
        val alleredeInnvilget =
            lagSoknad().fattVedtak(
                utfall = Utfall.Innvilget,
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = vedtakDocument,
                begrunnelse = null,
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.henlegg(
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = henleggelseDocument,
                begrunnelse = "Trukket av søker",
            )
        }
    }

    @Test
    fun `journalforBehandlingsutfall setter journalpostId på henleggelsen`() {
        val henlagt =
            lagSoknad().henlegg(
                fattetAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = henleggelseDocument,
                begrunnelse = "Trukket av søker",
            )
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = henlagt.journalforBehandlingsutfall(journalpostId, journalfortTidspunkt)

        val henleggelse = assertNotNull(journalfort.behandlingsutfall as? Henleggelse)
        assertEquals(journalpostId, henleggelse.journalpostId)
        assertEquals(journalfortTidspunkt, henleggelse.journalfortTidspunkt)
    }

    @Test
    fun `soknad uten behandlingsutfall har status MOTTATT`() {
        assertNull(lagSoknad().behandlingsutfall)
        assertEquals(SoknadStatus.MOTTATT, lagSoknad().status)
    }
}
