package no.nav.syfo.utenlandsopphold.application

interface Transaction

enum class TransactionIsolation {
    READ_COMMITTED,
    REPEATABLE_READ,
}

interface TransactionManager {
    fun <T> inTransaction(
        isolation: TransactionIsolation? = null,
        block: (Transaction) -> T,
    ): T
}
