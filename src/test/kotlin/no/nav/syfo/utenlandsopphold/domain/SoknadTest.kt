package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SoknadTest {
    @Test
    fun `fattVedtak om innvilgelse på mottatt søknad gir behandling med utfall innvilget`() {
        val now = OffsetDateTime.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.INNVILGET,
                innvilgedePerioder = emptyList(),
                begrunnelse = "Oppholdet hindrer ikke planlagt behandling",
                behandletAv = veileder,
                now = now,
                document = brevDocument,
            )

        assertEquals(SoknadStatus.INNVILGET, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(
            BehandlingsUtfall.Innvilget(standardSoktePerioder, "Oppholdet hindrer ikke planlagt behandling"),
            behandling.utfall,
        )
        assertEquals(veileder, behandling.behandletAv)
        assertEquals(now, behandling.behandletTidspunkt)
    }

    /**
     * Begrunnelse er valgfri ved innvilgelse. Et tomt tekstfelt i frontend skal gi null,
     * ikke en blank streng lagret i databasen.
     */
    @Test
    fun `fattVedtak om innvilgelse gjør blank begrunnelse om til null`() {
        val resultat =
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.INNVILGET,
                innvilgedePerioder = emptyList(),
                begrunnelse = "   ",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )

        val utfall = assertIs<BehandlingsUtfall.Innvilget>(assertNotNull(resultat.behandling).utfall)
        assertNull(utfall.begrunnelse)
    }

    @Test
    fun `fattVedtak om delvis innvilgelse setter innvilgede perioder`() {
        val innvilgetPeriode = Periode(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7))

        val resultat =
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.DELVIS_INNVILGET,
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = "Delvis innvilget begrunnelse",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(BehandlingsUtfall.DelvisInnvilget(listOf(innvilgetPeriode), "Delvis innvilget begrunnelse"), behandling.utfall)
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
                utfall = VedtaksUtfall.DELVIS_INNVILGET,
                innvilgedePerioder = listOf(innvilgetPeriode),
                begrunnelse = "Delvis innvilget begrunnelse",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )

        assertEquals(SoknadStatus.DELVIS_INNVILGET, resultat.status)
        assertEquals(
            BehandlingsUtfall.DelvisInnvilget(listOf(innvilgetPeriode), "Delvis innvilget begrunnelse"),
            resultat.behandling?.utfall,
        )
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
                utfall = VedtaksUtfall.DELVIS_INNVILGET,
                innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12))),
                begrunnelse = "Delvis innvilget begrunnelse",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med overlappende innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.DELVIS_INNVILGET,
                innvilgedePerioder =
                    listOf(
                        Periode(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7)),
                        Periode(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9)),
                    ),
                begrunnelse = "Delvis innvilget begrunnelse",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse uten innvilgede perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.DELVIS_INNVILGET,
                innvilgedePerioder = emptyList(),
                begrunnelse = "Delvis innvilget begrunnelse",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om delvis innvilgelse med periode utenfor søkte perioder kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.DELVIS_INNVILGET,
                innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 7))),
                begrunnelse = "Delvis innvilget begrunnelse",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )
        }
    }

    @Test
    fun `fattVedtak om avslag gir avslag uten innvilgede perioder`() {
        val resultat =
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.AVSLAG,
                innvilgedePerioder = emptyList(),
                begrunnelse = "Avslag begrunnelse",
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )

        assertEquals(SoknadStatus.AVSLAG, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(BehandlingsUtfall.Avslag("Avslag begrunnelse"), behandling.utfall)
    }

    @Test
    fun `fattVedtak om avslag uten begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.AVSLAG,
                innvilgedePerioder = emptyList(),
                begrunnelse = null,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )
        }
    }

    @Test
    fun `henlegg gir henlagt status med begrunnelse`() {
        val resultat =
            lagSoknad().henlegg(
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Søker har trukket søknaden",
            )

        assertEquals(SoknadStatus.HENLAGT, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(BehandlingsUtfall.Henlagt("Søker har trukket søknaden"), behandling.utfall)
    }

    @Test
    fun `henlegg med blank begrunnelse kaster feil`() {
        assertFailsWith<IllegalArgumentException> {
            lagSoknad().henlegg(
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "   ",
            )
        }
    }

    @Test
    fun `fattVedtak på allerede innvilget søknad kaster`() {
        val alleredeInnvilget =
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.INNVILGET,
                innvilgedePerioder = emptyList(),
                begrunnelse = null,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )

        assertFailsWith<IllegalStateException> {
            alleredeInnvilget.fattVedtak(
                utfall = VedtaksUtfall.INNVILGET,
                innvilgedePerioder = emptyList(),
                begrunnelse = null,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T12:00:00Z"),
                document = brevDocument,
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
    fun `journalforBrev setter journalpostId på brevet`() {
        val innvilget =
            lagSoknad().fattVedtak(
                utfall = VedtaksUtfall.INNVILGET,
                innvilgedePerioder = emptyList(),
                begrunnelse = null,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
            )
        val journalpostId = JournalpostId("123")
        val journalfortTidspunkt = OffsetDateTime.parse("2026-01-11T08:00:00Z")

        val journalfort = innvilget.journalforBrev(journalpostId, journalfortTidspunkt)

        val brev = assertNotNull(assertNotNull(journalfort.behandling).brev)
        assertEquals(journalpostId, brev.journalpostId)
        assertEquals(journalfortTidspunkt, brev.journalfortTidspunkt)
    }

    @Test
    fun `journalforBrev på ubehandlet søknad kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().journalforBrev(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }

    @Test
    fun `behandling lager brev med brevtype som matcher utfallet`() {
        val henlagt =
            lagSoknad().henlegg(
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
                document = brevDocument,
                begrunnelse = "Søker har trukket søknaden",
            )

        val brev = assertNotNull(assertNotNull(henlagt.behandling).brev)
        assertEquals(Brevtype.HENLEGGELSE, brev.brevtype)
        assertEquals(brevDocument, brev.document)
    }

    @Test
    fun `distribuerBrev på ubehandlet søknad kaster feil`() {
        assertFailsWith<IllegalStateException> {
            lagSoknad().distribuerBrev(OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }

    @Test
    fun `merkIkkeAktuell gir status ikke aktuell med årsak, uten brev`() {
        val now = OffsetDateTime.parse("2026-01-10T12:00:00Z")

        val resultat =
            lagSoknad().merkIkkeAktuell(
                arsak = IkkeAktuellArsak.BEHANDLET_I_INFOTRYGD,
                behandletAv = veileder,
                now = now,
            )

        assertEquals(SoknadStatus.IKKE_AKTUELL, resultat.status)
        val behandling = assertNotNull(resultat.behandling)
        assertEquals(BehandlingsUtfall.IkkeAktuell(IkkeAktuellArsak.BEHANDLET_I_INFOTRYGD), behandling.utfall)
        assertEquals(veileder, behandling.behandletAv)
        assertEquals(now, behandling.behandletTidspunkt)
        assertEquals(null, behandling.brev)
    }

    @Test
    fun `merkIkkeAktuell på allerede behandlet søknad kaster feil`() {
        val behandletSoknad = lagSoknad(behandling = lagBehandling(utfall = BehandlingsUtfall.Innvilget(standardSoktePerioder)))

        assertFailsWith<IllegalStateException> {
            behandletSoknad.merkIkkeAktuell(
                arsak = IkkeAktuellArsak.DUPLIKAT,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-11T08:00:00Z"),
            )
        }
    }

    @Test
    fun `søknad merket ikke aktuell kan ikke journalføres eller distribueres`() {
        val ikkeAktuell =
            lagSoknad().merkIkkeAktuell(
                arsak = IkkeAktuellArsak.ANNET,
                behandletAv = veileder,
                now = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            )

        assertFailsWith<IllegalStateException> {
            ikkeAktuell.journalforBrev(JournalpostId("123"), OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
        assertFailsWith<IllegalStateException> {
            ikkeAktuell.distribuerBrev(OffsetDateTime.parse("2026-01-11T08:00:00Z"))
        }
    }
}
