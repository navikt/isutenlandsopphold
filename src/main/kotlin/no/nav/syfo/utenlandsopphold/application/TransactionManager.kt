package no.nav.syfo.utenlandsopphold.application

interface Transaction

interface TransactionManager {
    fun <T> inTransaction(block: (Transaction) -> T): T
}
