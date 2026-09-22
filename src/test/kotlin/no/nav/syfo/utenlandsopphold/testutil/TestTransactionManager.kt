package no.nav.syfo.utenlandsopphold.testutil

import no.nav.syfo.utenlandsopphold.application.Transaction
import no.nav.syfo.utenlandsopphold.application.TransactionManager

/**
 * Kjører blokka rett ut uten noen ekte transaksjon. Brukes av tester som mocker
 * repositoryet, der det ikke finnes en database å committe mot.
 */
internal object TestTransactionManager : TransactionManager {
    override fun <T> inTransaction(block: (Transaction) -> T): T = block(TestTransaction)
}

private object TestTransaction : Transaction
