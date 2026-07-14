package no.nav.syfo.utenlandsopphold.infrastructure.database

import no.nav.syfo.utenlandsopphold.application.Transaction
import no.nav.syfo.utenlandsopphold.application.TransactionIsolation
import no.nav.syfo.utenlandsopphold.application.TransactionManager
import java.sql.Connection

class JdbcTransaction(
    val connection: Connection,
) : Transaction

fun Transaction.jdbcConnection(): Connection =
    (this as? JdbcTransaction)?.connection
        ?: error("Expected JdbcTransaction, but got ${this::class.simpleName}")

class JdbcTransactionManager(
    private val database: DatabaseInterface,
) : TransactionManager {
    override fun <T> inTransaction(
        isolation: TransactionIsolation?,
        block: (Transaction) -> T,
    ): T =
        database.connection.use { connection ->
            try {
                isolation?.let { connection.transactionIsolation = it.jdbcValue }
                val result = block(JdbcTransaction(connection))
                connection.commit()
                result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
}

private val TransactionIsolation.jdbcValue: Int
    get() =
        when (this) {
            TransactionIsolation.READ_COMMITTED -> Connection.TRANSACTION_READ_COMMITTED
            TransactionIsolation.REPEATABLE_READ -> Connection.TRANSACTION_REPEATABLE_READ
        }
