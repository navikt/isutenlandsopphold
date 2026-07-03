package no.nav.syfo.utenlandsopphold.infrastructure.journalforing

import no.nav.syfo.common.journalforing.Brevkode

/**
 * Brevkoder for documents journalført by this app.
 */
enum class UtenlandsoppholdBrevkode(
    override val value: String,
) : Brevkode {
    VEDTAK("OPPF_VEDTAK_UTENLANDSOPPHOLD"),
}
