package no.nav.syfo.utenlandsopphold.domain

class ManglerSoktePerioderException(
    message: String = "Søknad må ha minst en søkt periode",
) : IllegalArgumentException(message)
