package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import java.time.LocalDate
import java.time.OffsetDateTime
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
        eksternId = UUID.randomUUID(),
        personident = Personident("11111111111"),
        soktePerioder = soktePerioder,
        innsendtTidspunkt = OffsetDateTime.parse("2026-01-02T08:00:00Z"),
        vedtak = vedtak,
    )

internal val veileder = Navident("Z990000")

internal val vedtakDocument =
    listOf(
        DocumentComponent(
            type = DocumentComponentType.HEADER_H1,
            title = "Vedtak",
            texts = listOf("Søknaden din er innvilget"),
        ),
    )
