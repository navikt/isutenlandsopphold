package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal fun lagSoknad(
    soktePerioder: List<Periode> =
        listOf(
            Periode(
                fom = LocalDate.of(2026, 1, 5),
                tom = LocalDate.of(2026, 1, 9),
            ),
        ),
    vedtak: Vedtak? = null,
): Soknad =
    Soknad(
        id = UUID.randomUUID(),
        personident = Personident("11111111111"),
        soktePerioder = soktePerioder,
        mottattTidspunkt = Instant.parse("2026-01-02T08:00:00Z"),
        vedtak = vedtak,
    )

internal val veileder = Navident("Z990000")
