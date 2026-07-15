package no.nav.syfo.utenlandsopphold.infrastructure.database

import no.nav.syfo.utenlandsopphold.application.Transaction
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
    override fun <T> inTransaction(block: (Transaction) -> T): T =
        database.connection.use { connection ->
            try {
                val result = block(JdbcTransaction(connection))
                connection.commit()
                result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
}
