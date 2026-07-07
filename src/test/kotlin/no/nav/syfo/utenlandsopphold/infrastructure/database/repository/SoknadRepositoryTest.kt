package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.infrastructure.database.TestDatabase
import no.nav.syfo.utenlandsopphold.infrastructure.database.dropData
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoknadRepositoryTest {
    private val database = TestDatabase()
    private val repository = SoknadRepository(database = database)

    private val personident = Personident("11111111111")

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @AfterAll
    fun stop() {
        database.stop()
    }

    @Test
    fun `lagrer og henter soknad uten vedtak`() {
        val soknad = soknad(soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10))))

        repository.lagreMottattSoknad(soknad)
        val hentet = repository.hentSoknader(personident)

        assertEquals(1, hentet.size)
        val lagret = hentet.single()
        assertEquals(soknad.id, lagret.id)
        assertEquals(soknad.eksternId, lagret.eksternId)
        assertEquals(personident, lagret.personident)
        assertEquals(soknad.soktePerioder, lagret.soktePerioder)
        assertEquals(soknad.innsendtTidspunkt, lagret.innsendtTidspunkt)
        assertEquals(SoknadStatus.MOTTATT, lagret.status)
        assertNull(lagret.vedtak)
    }

    @Test
    fun `henter kun soknader for angitt personident`() {
        repository.lagreMottattSoknad(soknad(personident = Personident("11111111111")))
        repository.lagreMottattSoknad(soknad(personident = Personident("22222222222")))

        val hentet = repository.hentSoknader(Personident("11111111111"))

        assertEquals(1, hentet.size)
        assertEquals(Personident("11111111111"), hentet.single().personident)
    }

    @Test
    fun `henter flere soktePerioder for en soknad`() {
        val perioder =
            listOf(
                Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5)),
                Periode(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3)),
            )

        repository.lagreMottattSoknad(soknad(soktePerioder = perioder))
        val lagret = repository.hentSoknader(personident).single()

        assertEquals(2, lagret.soktePerioder.size)
        assertTrue(lagret.soktePerioder.containsAll(perioder))
    }

    private fun soknad(
        personident: Personident = this.personident,
        soktePerioder: List<Periode> = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10))),
    ): Soknad =
        Soknad(
            id = UUID.randomUUID(),
            eksternId = UUID.randomUUID(),
            personident = personident,
            soktePerioder = soktePerioder,
            innsendtTidspunkt = Instant.parse("2026-03-01T09:00:00Z"),
        )

    @Test
    fun `getUjournalforteSoknader returnerer kun soknader med u-journalfort vedtak`() {
        opprettSoknadMedVedtak(journalpostId = null)
        opprettSoknadMedVedtak(journalpostId = "111")

        val ujournalforte = repository.getUjournalforteSoknader()

        assertEquals(1, ujournalforte.size)
        assertTrue(ujournalforte.single().vedtak?.erJournalfort == false)
    }

    @Test
    fun `getUjournalforteSoknader leser document og perioder`() {
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
                    statement.setString(3, personident.value)
                    statement.setTimestamp(4, Timestamp.from(Instant.parse("2026-01-02T08:00:00Z")))
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
                        statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-01-10T12:00:00Z")))
                        statement.setString(4, DOCUMENT_JSON)
                        statement.setString(5, journalpostId)
                        statement.setTimestamp(6, journalpostId?.let { Timestamp.from(Instant.now()) })
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
        private const val DOCUMENT_JSON =
            """[{"type": "PARAGRAPH", "title": "Tittel", "texts": ["Innhold"]}]"""
    }
}
