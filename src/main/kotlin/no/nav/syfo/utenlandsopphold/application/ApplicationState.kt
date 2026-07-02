package no.nav.syfo.utenlandsopphold.application

import kotlin.concurrent.Volatile

data class ApplicationState(
    @Volatile var alive: Boolean = true,
    @Volatile var ready: Boolean = false,
)
