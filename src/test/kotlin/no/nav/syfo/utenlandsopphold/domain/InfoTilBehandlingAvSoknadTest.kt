package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class InfoTilBehandlingAvSoknadTest {
    private fun periode(
        fom: String,
        tom: String,
    ) = Periode(fom = LocalDate.parse(fom), tom = LocalDate.parse(tom))

    private fun innvilgetSoknad(
        soktePerioder: List<Periode>,
        innvilgedePerioder: List<Periode> = soktePerioder,
    ): Soknad =
        lagSoknad(
            soktePerioder = soktePerioder,
            vedtak =
                lagVedtak(
                    utfall =
                        if (innvilgedePerioder == soktePerioder) {
                            Utfall.Innvilget
                        } else {
                            Utfall.DelvisInnvilget(innvilgedePerioder)
                        },
                    innvilgedePerioder = innvilgedePerioder,
                ),
        )

    @Test
    fun `en opptelling per soekt periode, med vindu paa noeyaktig ett aar`() {
        val soknad =
            lagSoknad(
                soktePerioder =
                    listOf(
                        periode("2026-03-01", "2026-03-05"),
                        periode("2026-01-10", "2026-01-12"),
                    ),
            )

        val opptellinger = listOf(soknad).infoTilBehandlingAv(soknad).opptellinger

        assertEquals(2, opptellinger.size)
        assertEquals(LocalDate.parse("2026-01-12"), opptellinger[0].tom)
        assertEquals(LocalDate.parse("2025-01-13"), opptellinger[0].fom)
        assertEquals(LocalDate.parse("2026-03-05"), opptellinger[1].tom)
        assertEquals(LocalDate.parse("2025-03-06"), opptellinger[1].fom)
    }

    @Test
    fun `soeknadens egne dager telles med i begge tallene`() {
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(soknad).infoTilBehandlingAv(soknad).opptellinger.single()

        assertEquals(5, opptelling.antallDagerHvisInnvilget)
        assertEquals(5, opptelling.antallDagerHvisInnvilgetInklUbehandlede)
        assertEquals(23, opptelling.antallDagerIgjenHvisInnvilget)
        assertEquals(23, opptelling.antallDagerIgjenHvisInnvilgetInklUbehandlede)
    }

    @Test
    fun `tidligere perioder i samme soeknad telles med, senere perioder faller utenfor vinduet`() {
        val soknad =
            lagSoknad(
                soktePerioder =
                    listOf(
                        periode("2026-01-10", "2026-01-12"),
                        periode("2026-03-01", "2026-03-05"),
                    ),
            )

        val opptellinger = listOf(soknad).infoTilBehandlingAv(soknad).opptellinger

        assertEquals(3, opptellinger[0].antallDagerHvisInnvilget)
        assertEquals(8, opptellinger[1].antallDagerHvisInnvilget)
    }

    @Test
    fun `tidligere innvilgede dager telles med i begge tallene`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2025-12-01", "2025-12-10")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(innvilget, soknad).infoTilBehandlingAv(soknad).opptellinger.single()

        assertEquals(15, opptelling.antallDagerHvisInnvilget)
        assertEquals(15, opptelling.antallDagerHvisInnvilgetInklUbehandlede)
    }

    @Test
    fun `ved delvis innvilgelse telles kun de innvilgede dagene`() {
        val delvisInnvilget =
            innvilgetSoknad(
                soktePerioder = listOf(periode("2025-12-01", "2025-12-10")),
                innvilgedePerioder = listOf(periode("2025-12-01", "2025-12-03")),
            )
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(delvisInnvilget, soknad).infoTilBehandlingAv(soknad).opptellinger.single()

        assertEquals(8, opptelling.antallDagerHvisInnvilget)
    }

    @Test
    fun `avslaatt soeknad bidrar ikke med dager`() {
        val avslatt =
            lagSoknad(
                soktePerioder = listOf(periode("2025-12-01", "2025-12-10")),
                vedtak =
                    lagVedtak(
                        utfall = Utfall.Avslag,
                        innvilgedePerioder = emptyList(),
                        begrunnelse = "Ikke innvilget",
                    ),
            )
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(avslatt, soknad).infoTilBehandlingAv(soknad).opptellinger.single()

        assertEquals(5, opptelling.antallDagerHvisInnvilget)
        assertEquals(5, opptelling.antallDagerHvisInnvilgetInklUbehandlede)
    }

    @Test
    fun `andre ubehandlede soeknader telles kun med i inkl-ubehandlede-tallet`() {
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-02-01", "2026-02-04")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(annenUbehandlet, soknad).infoTilBehandlingAv(soknad).opptellinger.single()

        assertEquals(5, opptelling.antallDagerHvisInnvilget)
        assertEquals(9, opptelling.antallDagerHvisInnvilgetInklUbehandlede)
    }

    @Test
    fun `overlappende dager telles kun en gang`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-03-03", "2026-03-07")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-04", "2026-03-08")))

        val opptelling =
            listOf(innvilget, annenUbehandlet, soknad)
                .infoTilBehandlingAv(soknad)
                .opptellinger
                .single()

        assertEquals(8, opptelling.antallDagerHvisInnvilget)
        assertEquals(8, opptelling.antallDagerHvisInnvilgetInklUbehandlede)
    }

    @Test
    fun `dager utenfor vinduet telles ikke med`() {
        val forsteDagIVinduet = innvilgetSoknad(soktePerioder = listOf(periode("2025-03-06", "2025-03-06")))
        val sisteDagUtenforVinduet = innvilgetSoknad(soktePerioder = listOf(periode("2025-03-05", "2025-03-05")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(forsteDagIVinduet, sisteDagUtenforVinduet, soknad)
                .infoTilBehandlingAv(soknad)
                .opptellinger
                .single()

        assertEquals(6, opptelling.antallDagerHvisInnvilget)
    }

    @Test
    fun `dager etter vindusslutt i samme periode telles ikke med`() {
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-03-04", "2026-03-20")))

        val opptelling =
            listOf(annenUbehandlet, soknad).infoTilBehandlingAv(soknad).opptellinger.single()

        assertEquals(5, opptelling.antallDagerHvisInnvilgetInklUbehandlede)
    }

    @Test
    fun `dager igjen blir negativt naar grensen overskrides`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-01-01", "2026-01-30")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(innvilget, soknad).infoTilBehandlingAv(soknad).opptellinger.single()

        assertEquals(35, opptelling.antallDagerHvisInnvilget)
        assertEquals(-7, opptelling.antallDagerIgjenHvisInnvilget)
    }
}
