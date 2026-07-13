package no.nav.syfo.utenlandsopphold.infrastructure.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.utenlandsopphold.DatabaseEnvironment
import no.nav.syfo.utenlandsopphold.infrastructure.metric.METRICS_REGISTRY
import org.flywaydb.core.Flyway
import java.sql.Connection

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val poolSize: Int = 4,
)

interface DatabaseInterface {
    val connection: Connection
}

class Database(
    private val config: DatabaseConfig,
) : DatabaseInterface {
    private val dataSource: HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.poolSize
                minimumIdle = 1
                isAutoCommit = false
                metricRegistry = METRICS_REGISTRY
                validate()
            },
        )

    override val connection: Connection
        get() = dataSource.connection

    init {
        runFlywayMigrations()
    }

    private fun runFlywayMigrations() =
        Flyway
            .configure()
            .run {
                dataSource(config.jdbcUrl, config.username, config.password)
                validateMigrationNaming(true)
                load()
                    .migrate()
                    .migrationsExecuted
            }
}

fun DatabaseInterface.isOk(): Boolean =
    try {
        connection.use { it.isValid(1) }
    } catch (ex: Exception) {
        false
    }

fun databaseConfig(databaseEnvironment: DatabaseEnvironment) =
    DatabaseConfig(
        jdbcUrl = databaseEnvironment.jdbcUrl,
        username = databaseEnvironment.username,
        password = databaseEnvironment.password,
    )
