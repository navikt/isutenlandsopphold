package no.nav.syfo.utenlandsopphold.infrastructure.database

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import java.sql.Connection

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres =
        try {
            EmbeddedPostgres.start()
        } catch (e: Exception) {
            EmbeddedPostgres.builder().start()
        }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {
        Flyway
            .configure()
            .dataSource(pg.postgresDatabase)
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }

    fun stop() {
        pg.close()
    }
}

fun TestDatabase.dropData() {
    val queryList =
        listOf(
            "DELETE FROM vedtak_periode",
            "DELETE FROM vedtak",
            "DELETE FROM soknad_periode",
            "DELETE FROM soknad",
        )
    connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}
