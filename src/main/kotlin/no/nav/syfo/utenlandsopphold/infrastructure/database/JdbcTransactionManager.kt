package no.nav.syfo.utenlandsopphold.infrastructure.database

import no.nav.syfo.utenlandsopphold.application.TransactionContext
import no.nav.syfo.utenlandsopphold.application.TransactionManager
import java.sql.Connection

class JdbcTransactionContext(
    val connection: Connection,
) : TransactionContext

class JdbcTransactionManager(
    private val database: DatabaseInterface,
) : TransactionManager {
    override fun <T> inTransaction(block: (TransactionContext) -> T): T =
        database.connection.use { connection ->
            try {
                connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                val result = block(JdbcTransactionContext(connection))
                connection.commit()
                result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
}
