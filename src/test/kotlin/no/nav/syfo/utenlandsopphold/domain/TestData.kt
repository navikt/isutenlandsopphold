package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

internal fun lagSoknad(
    soktePerioder: List<Periode> = standardSoktePerioder,
    behandling: Behandling? = null,
): Soknad =
    Soknad(
        id = UUID.randomUUID(),
        eksternId = UUID.randomUUID(),
        personident = Personident("11111111111"),
        soktePerioder = soktePerioder,
        innsendtTidspunkt = OffsetDateTime.parse("2026-01-02T08:00:00Z"),
        behandling = behandling,
    )

/** Standard søkte perioder. Ved full innvilgelse er de også de innvilgede periodene. */
internal val standardSoktePerioder =
    listOf(
        Periode(
            fom = LocalDate.of(2026, 1, 5),
            tom = LocalDate.of(2026, 1, 9),
        ),
    )

/**
 * Lager en behandling med et brev som matcher utfallet. Brevtypen utledes fra utfallet,
 * slik at testene ikke trenger å gjenta koblingen mellom utfall og brev.
 */
internal fun lagBehandling(
    utfall: BehandlingsUtfall = BehandlingsUtfall.Innvilget(standardSoktePerioder),
    behandletAv: Navident = veileder,
    behandletTidspunkt: OffsetDateTime = OffsetDateTime.parse("2026-01-10T08:00:00Z"),
    brev: Brev? = utfall.brevtype()?.let { lagBrev(brevtype = it) },
): Behandling =
    Behandling(
        utfall = utfall,
        behandletAv = behandletAv,
        behandletTidspunkt = behandletTidspunkt,
        brev = brev,
    )

internal fun lagBrev(
    brevtype: Brevtype = Brevtype.VEDTAK_INNVILGET,
    document: List<DocumentComponent> = brevDocument,
): Brev =
    Brev(
        brevtype = brevtype,
        document = document,
    )

internal val veileder = Navident("Z990000")

internal val brevDocument =
    listOf(
        DocumentComponent(
            type = DocumentComponentType.HEADER_H1,
            title = "Vedtak",
            texts = listOf("Søknaden din er innvilget"),
        ),
    )
