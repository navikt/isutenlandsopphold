package no.nav.syfo.utenlandsopphold.application

import java.util.UUID

/**
 * Kastes når en søknad som skal behandles ikke finnes. API-laget slår normalt opp
 * søknaden og svarer 404 før dette skjer, så i praksis treffer den bare når søknaden
 * forsvinner mellom oppslaget og låsingen i transaksjonen.
 */
class SoknadFinnesIkkeException(
    soknadId: UUID,
) : RuntimeException("Søknad med id $soknadId finnes ikke")
