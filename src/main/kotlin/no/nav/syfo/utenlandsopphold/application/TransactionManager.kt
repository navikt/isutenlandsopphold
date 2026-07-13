package no.nav.syfo.utenlandsopphold.application

interface TransactionContext

interface TransactionManager {
    fun <T> inTransaction(block: (TransactionContext) -> T): T
}
