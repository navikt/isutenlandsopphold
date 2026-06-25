package no.nav.syfo.utenlandsopphold.application

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = false,
)
