package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OpptellingTest {
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

        val opptellinger = listOf(soknad).opptellingerFor(soknad)

        assertEquals(2, opptellinger.size)
        assertEquals(LocalDate.parse("2026-01-12"), opptellinger[0].tom)
        assertEquals(LocalDate.parse("2025-01-13"), opptellinger[0].fom)
        assertEquals(LocalDate.parse("2026-03-05"), opptellinger[1].tom)
        assertEquals(LocalDate.parse("2025-03-06"), opptellinger[1].fom)
    }

    @Test
    fun `soeknadens egne dager telles med i begge tallene`() {
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(soknad).opptellingerFor(soknad).single()

        assertEquals(5, opptelling.antallDagerBruktHvisInnvilget)
        assertEquals(5, opptelling.antallDagerPotensieltBruktHvisInnvilget)
        assertEquals(23, opptelling.antallDagerIgjenHvisInnvilget)
        assertEquals(23, opptelling.antallDagerPotensieltIgjenHvisInnvilget)
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

        val opptellinger = listOf(soknad).opptellingerFor(soknad)

        assertEquals(3, opptellinger[0].antallDagerBruktHvisInnvilget)
        assertEquals(8, opptellinger[1].antallDagerBruktHvisInnvilget)
    }

    @Test
    fun `tidligere innvilgede dager telles med i begge tallene`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2025-12-01", "2025-12-10")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(innvilget, soknad).opptellingerFor(soknad).single()

        assertEquals(15, opptelling.antallDagerBruktHvisInnvilget)
        assertEquals(15, opptelling.antallDagerPotensieltBruktHvisInnvilget)
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
            listOf(delvisInnvilget, soknad).opptellingerFor(soknad).single()

        assertEquals(8, opptelling.antallDagerBruktHvisInnvilget)
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

        val opptelling = listOf(avslatt, soknad).opptellingerFor(soknad).single()

        assertEquals(5, opptelling.antallDagerBruktHvisInnvilget)
        assertEquals(5, opptelling.antallDagerPotensieltBruktHvisInnvilget)
    }

    @Test
    fun `andre ubehandlede soeknader telles kun med i inkl-ubehandlede-tallet`() {
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-02-01", "2026-02-04")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(annenUbehandlet, soknad).opptellingerFor(soknad).single()

        assertEquals(5, opptelling.antallDagerBruktHvisInnvilget)
        assertEquals(9, opptelling.antallDagerPotensieltBruktHvisInnvilget)
    }

    @Test
    fun `overlappende dager telles kun en gang`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-03-03", "2026-03-07")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-04", "2026-03-08")))

        val opptelling =
            listOf(innvilget, annenUbehandlet, soknad)
                .opptellingerFor(soknad)
                .single()

        assertEquals(8, opptelling.antallDagerBruktHvisInnvilget)
        assertEquals(8, opptelling.antallDagerPotensieltBruktHvisInnvilget)
    }

    @Test
    fun `dager utenfor vinduet telles ikke med`() {
        val forsteDagIVinduet = innvilgetSoknad(soktePerioder = listOf(periode("2025-03-06", "2025-03-06")))
        val sisteDagUtenforVinduet = innvilgetSoknad(soktePerioder = listOf(periode("2025-03-05", "2025-03-05")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(forsteDagIVinduet, sisteDagUtenforVinduet, soknad)
                .opptellingerFor(soknad)
                .single()

        assertEquals(6, opptelling.antallDagerBruktHvisInnvilget)
    }

    @Test
    fun `dager etter vindusslutt i samme periode telles ikke med`() {
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-03-04", "2026-03-20")))

        val opptelling =
            listOf(annenUbehandlet, soknad).opptellingerFor(soknad).single()

        assertEquals(5, opptelling.antallDagerPotensieltBruktHvisInnvilget)
    }

    @Test
    fun `perioder deles i innvilgede og ubehandlede, og soeknadens egne dager er ikke med`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2025-12-01", "2025-12-03")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-02-01", "2026-02-04")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(innvilget, annenUbehandlet, soknad)
                .opptellingerFor(soknad)
                .single()

        assertEquals(listOf(periode("2025-12-01", "2025-12-03")), opptelling.tidligereInnvilgedePerioder)
        assertEquals(listOf(periode("2026-02-01", "2026-02-04")), opptelling.tidligereUbehandledePerioder)
    }

    @Test
    fun `en dag som er baade innvilget og ubehandlet telles kun som innvilget`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-02-01", "2026-02-04")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-02-03", "2026-02-06")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(innvilget, annenUbehandlet, soknad)
                .opptellingerFor(soknad)
                .single()

        assertEquals(listOf(periode("2026-02-01", "2026-02-04")), opptelling.tidligereInnvilgedePerioder)
        assertEquals(listOf(periode("2026-02-05", "2026-02-06")), opptelling.tidligereUbehandledePerioder)
    }

    @Test
    fun `avslaatte dager regnes som ubehandlede kun hvis de ogsaa er soekt om i en ubehandlet soeknad`() {
        val avslatt =
            lagSoknad(
                soktePerioder = listOf(periode("2026-02-01", "2026-02-04")),
                vedtak =
                    lagVedtak(
                        utfall = Utfall.Avslag,
                        innvilgedePerioder = emptyList(),
                        begrunnelse = "Ikke innvilget",
                    ),
            )
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(avslatt, soknad).opptellingerFor(soknad).single()

        assertEquals(emptyList(), opptelling.tidligereInnvilgedePerioder)
        assertEquals(emptyList(), opptelling.tidligereUbehandledePerioder)
    }

    @Test
    fun `perioder klippes til vinduet og hull gir separate perioder`() {
        val utenforVinduet = innvilgetSoknad(soktePerioder = listOf(periode("2025-03-03", "2025-03-08")))
        val medHull = innvilgetSoknad(soktePerioder = listOf(periode("2025-06-01", "2025-06-02")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(utenforVinduet, medHull, soknad)
                .opptellingerFor(soknad)
                .single()

        assertEquals(
            listOf(periode("2025-03-06", "2025-03-08"), periode("2025-06-01", "2025-06-02")),
            opptelling.tidligereInnvilgedePerioder,
        )
    }

    @Test
    fun `de tre listene er disjunkte og summerer seg til totalen`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-02-01", "2026-02-04")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-02-03", "2026-02-10")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))
        val soknader = listOf(innvilget, annenUbehandlet, soknad)

        val opptelling = soknader.opptellingerFor(soknad).single()

        val innvilgedeDager = opptelling.tidligereInnvilgedePerioder.dager()
        val ubehandledeDager = opptelling.tidligereUbehandledePerioder.dager()
        val soktedager = opptelling.soktePerioderIVinduet.dager()

        assertEquals(innvilgedeDager.size, opptelling.tidligereInnvilgedePerioder.dager().size)
        assertEquals(emptySet(), innvilgedeDager intersect ubehandledeDager)
        assertEquals(emptySet(), innvilgedeDager intersect soktedager)
        assertEquals(emptySet(), ubehandledeDager intersect soktedager)
        assertEquals(
            opptelling.antallDagerPotensieltBruktHvisInnvilget,
            innvilgedeDager.size + ubehandledeDager.size + soktedager.size,
        )
        assertEquals(
            opptelling.antallDagerBruktHvisInnvilget,
            innvilgedeDager.size + soktedager.size,
        )
    }

    @Test
    fun `dager som overlapper med soeknadens egne soekte dager tas ut av de andre listene`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-02-25", "2026-03-02")))
        val annenUbehandlet = lagSoknad(soktePerioder = listOf(periode("2026-03-04", "2026-03-10")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling =
            listOf(innvilget, annenUbehandlet, soknad)
                .opptellingerFor(soknad)
                .single()

        assertEquals(listOf(periode("2026-03-01", "2026-03-05")), opptelling.soktePerioderIVinduet)
        assertEquals(listOf(periode("2026-02-25", "2026-02-28")), opptelling.tidligereInnvilgedePerioder)
        assertEquals(emptyList(), opptelling.tidligereUbehandledePerioder)
        assertEquals(9, opptelling.antallDagerPotensieltBruktHvisInnvilget)
    }

    @Test
    fun `dager mellom soeknadens perioder telles med i opptellingen for den senere perioden`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-02-01", "2026-02-03")))
        val soknad =
            lagSoknad(
                soktePerioder =
                    listOf(
                        periode("2026-01-10", "2026-01-12"),
                        periode("2026-03-01", "2026-03-05"),
                    ),
            )

        val opptellinger = listOf(innvilget, soknad).opptellingerFor(soknad)

        assertEquals(emptyList(), opptellinger[0].tidligereInnvilgedePerioder)
        assertEquals(listOf(periode("2026-01-10", "2026-01-12")), opptellinger[0].soktePerioderIVinduet)

        assertEquals(listOf(periode("2026-02-01", "2026-02-03")), opptellinger[1].tidligereInnvilgedePerioder)
        assertEquals(
            listOf(periode("2026-01-10", "2026-01-12"), periode("2026-03-01", "2026-03-05")),
            opptellinger[1].soktePerioderIVinduet,
        )
        assertEquals(11, opptellinger[1].antallDagerBruktHvisInnvilget)
    }

    @Test
    fun `dager igjen blir negativt naar grensen overskrides`() {
        val innvilget = innvilgetSoknad(soktePerioder = listOf(periode("2026-01-01", "2026-01-30")))
        val soknad = lagSoknad(soktePerioder = listOf(periode("2026-03-01", "2026-03-05")))

        val opptelling = listOf(innvilget, soknad).opptellingerFor(soknad).single()

        assertEquals(35, opptelling.antallDagerBruktHvisInnvilget)
        assertEquals(-7, opptelling.antallDagerIgjenHvisInnvilget)
    }
}
