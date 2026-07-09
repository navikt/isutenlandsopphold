package no.nav.syfo.utenlandsopphold

import no.nav.syfo.common.types.ident.Personident

object UserConstants {
    const val VEILEDER_IDENT_MED_LESETILGANG = "Z999999"

    val PERSON_VEILEDERE_HAR_TILGANG_TIL = Personident("11111111111")
    val PERSON_VEILEDERE_IKKE_HAR_TILGANG_TIL = Personident("22222222222")
}
