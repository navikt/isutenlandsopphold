package no.nav.syfo.utenlandsopphold.infrastructure.mock

import no.nav.syfo.common.mock.tilgangskontroll.MockUserSyfoTilgangLevel
import no.nav.syfo.common.mock.tilgangskontroll.MockUserTilgangDetails
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.utenlandsopphold.UserConstants

/**
 * Tilgangsmatrise for mock-istilgangskontroll: hvilken veileder som har hvilket nivå av Modia
 * SYFO-tilgang, og til hvilke personer. Brukes av [mockTilgangskontrollRequestHandler] via
 * [mockHttpClient]. Personer utenfor [MockUserTilgangDetails.personsUserHasAccessTo] gir avslag (403).
 */
val mockTilgangDetailsPerNavident =
    mapOf(
        Navident(UserConstants.VEILEDER_IDENT_MED_LESETILGANG) to
            MockUserTilgangDetails(
                syfoTilgangLevel = MockUserSyfoTilgangLevel.READ,
                personsUserHasAccessTo = setOf(UserConstants.PERSON_VEILEDERE_HAR_TILGANG_TIL),
            ),
    )
