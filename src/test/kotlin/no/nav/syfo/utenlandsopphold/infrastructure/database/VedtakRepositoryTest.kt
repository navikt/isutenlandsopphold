package no.nav.syfo.utenlandsopphold.infrastructure.database

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.common.journalforing.JournalpostId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VedtakRepositoryTest {
    private val repository = VedtakRepository(database)

    @AfterTest
    fun ryddOpp() {
        database.connection.use { connection ->
            connection.prepareStatement("DELETE FROM SOKNAD").executeUpdate()
            connection.commit()
        }
    }

    @Test
    fun `getUjournalforteSoknader returnerer kun søknader med u-journalført vedtak`() {
        opprettSoknadMedVedtak(journalpostId = null)
        opprettSoknadMedVedtak(journalpostId = "111")

        val ujournalforte = repository.getUjournalforteSoknader()

        assertEquals(1, ujournalforte.size)
        assertTrue(ujournalforte.single().vedtak?.erJournalfort == false)
    }

    @Test
    fun `getUjournalforteSoknader leser document og søkte perioder`() {
        val vedtakId = opprettSoknadMedVedtak(journalpostId = null)

        val soknad = repository.getUjournalforteSoknader().single()

        assertEquals(vedtakId, soknad.vedtak?.vedtakId)
        assertEquals(1, soknad.vedtak?.document?.size)
        assertEquals(
            "Tittel",
            soknad.vedtak
                ?.document
                ?.first()
                ?.title,
        )
        assertEquals(1, soknad.soktePerioder.size)
        assertEquals(soknad.soktePerioder, soknad.vedtak?.innvilgetePerioder)
    }

    @Test
    fun `setVedtakJournalfort oppdaterer journalpost_id og journalfort_tidspunkt`() {
        val vedtakId = opprettSoknadMedVedtak(journalpostId = null)
        val journalpostId = JournalpostId("999")
        val journalfortTidspunkt = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        repository.setVedtakJournalfort(vedtakId, journalpostId, journalfortTidspunkt)

        assertTrue(repository.getUjournalforteSoknader().isEmpty())
    }

    private fun opprettSoknadMedVedtak(journalpostId: String?): UUID {
        val soknadUuid = UUID.randomUUID()
        val vedtakUuid = UUID.randomUUID()

        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO SOKNAD (uuid, ekstern_id, personident, innsendt_tidspunkt)
                    VALUES (?, ?, ?, ?)
                    """,
                ).use { statement ->
                    statement.setObject(1, soknadUuid)
                    statement.setObject(2, UUID.randomUUID())
                    statement.setString(3, "11111111111")
                    statement.setTimestamp(4, java.sql.Timestamp.from(Instant.parse("2026-01-02T08:00:00Z")))
                    statement.executeUpdate()
                }

            val soknadId =
                connection.prepareStatement("SELECT id FROM SOKNAD WHERE uuid = ?").use { statement ->
                    statement.setObject(1, soknadUuid)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getInt("id")
                    }
                }

            connection
                .prepareStatement(
                    "INSERT INTO SOKNAD_PERIODE (soknad_id, fom, tom) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setInt(1, soknadId)
                    statement.setObject(2, LocalDate.of(2026, 1, 5))
                    statement.setObject(3, LocalDate.of(2026, 1, 9))
                    statement.executeUpdate()
                }

            val vedtakId =
                connection
                    .prepareStatement(
                        """
                        INSERT INTO VEDTAK (
                            uuid,
                            soknad_id,
                            utfall,
                            fattet_av,
                            fattet_tidspunkt,
                            document,
                            journalpost_id,
                            journalfort_tidspunkt
                        )
                        VALUES (?, ?, 'INNVILGET', 'Z990000', ?, ?::jsonb, ?, ?)
                        RETURNING id
                        """,
                    ).use { statement ->
                        statement.setObject(1, vedtakUuid)
                        statement.setInt(2, soknadId)
                        statement.setTimestamp(3, java.sql.Timestamp.from(Instant.parse("2026-01-10T12:00:00Z")))
                        statement.setString(4, DOCUMENT_JSON)
                        statement.setString(5, journalpostId)
                        statement.setTimestamp(6, journalpostId?.let { java.sql.Timestamp.from(Instant.now()) })
                        statement.executeQuery().use { rs ->
                            rs.next()
                            rs.getInt("id")
                        }
                    }

            connection
                .prepareStatement(
                    "INSERT INTO VEDTAK_PERIODE (vedtak_id, fom, tom) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setInt(1, vedtakId)
                    statement.setObject(2, LocalDate.of(2026, 1, 5))
                    statement.setObject(3, LocalDate.of(2026, 1, 9))
                    statement.executeUpdate()
                }

            connection.commit()
        }

        return vedtakUuid
    }

    companion object {
        private lateinit var embeddedPostgres: EmbeddedPostgres
        private lateinit var database: Database

        private const val DOCUMENT_JSON =
            """[{"type": "PARAGRAPH", "title": "Tittel", "texts": ["Innhold"]}]"""

        @BeforeAll
        @JvmStatic
        fun setup() {
            embeddedPostgres = EmbeddedPostgres.start()
            database =
                Database(
                    config =
                        DatabaseConfig(
                            jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres"),
                            username = "postgres",
                            password = "",
                        ),
                )
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            embeddedPostgres.close()
        }
    }
}
