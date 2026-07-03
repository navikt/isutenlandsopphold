package no.nav.syfo.utenlandsopphold.application

data class ApplicationState(
    @Volatile var alive: Boolean = true,
    @Volatile var ready: Boolean = false,
)
