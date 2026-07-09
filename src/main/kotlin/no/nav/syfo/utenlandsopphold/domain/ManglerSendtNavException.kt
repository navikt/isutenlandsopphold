package no.nav.syfo.utenlandsopphold.domain

class ManglerSendtNavException(
    id: String,
) : IllegalStateException("Søknad med id $id og status SENDT mangler sendtNav")
