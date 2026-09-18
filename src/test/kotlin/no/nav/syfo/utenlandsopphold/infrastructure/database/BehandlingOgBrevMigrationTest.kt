package no.nav.syfo.utenlandsopphold.infrastructure.database

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifiserer at V6_06 flytter eksisterende vedtak over i BEHANDLING og BREV uten å miste data,
 * og at brevet arver vedtakets uuid. Sistnevnte er kritisk: uuid-en brukes som
 * `eksternReferanseId` mot dokarkiv, og en ny verdi ville gjort at allerede journalførte brev
 * kunne bli journalført på nytt.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BehandlingOgBrevMigrationTest {
    private val pg: EmbeddedPostgres = EmbeddedPostgres.builder().start()

    @AfterAll
    fun stop() {
        pg.close()
    }

    private fun migrateTo(target: String) {
        Flyway
            .configure()
            .dataSource(pg.postgresDatabase)
            .validateMigrationNaming(true)
            .target(target)
            .load()
            .migrate()
    }

    @Test
    fun `migrerer vedtak i alle leveringstilstander til behandling og brev`() {
        migrateTo("6.05")

        val innvilgetUuid = insertVedtakForNySoknad(utfall = "INNVILGET", journalpostId = null)
        val delvisUuid =
            insertVedtakForNySoknad(
                utfall = "DELVIS_INNVILGET",
                journalpostId = "111",
                publishedAt = OffsetDateTime.parse("2026-01-12T09:00:00Z"),
            )
        val avslagUuid =
            insertVedtakForNySoknad(
                utfall = "AVSLAG",
                journalpostId = "222",
                distribuertTidspunkt = OffsetDateTime.parse("2026-01-15T10:00:00Z"),
            )
        val henlagtUuid = insertVedtakForNySoknad(utfall = "HENLAGT", journalpostId = null)

        migrateTo("6.06")

        assertEquals(4, count("BEHANDLING"))
        assertEquals(4, count("BREV"))
        assertEquals(4, count("VEDTAK_PERIODE"))

        assertBrev(behandlingUuid = innvilgetUuid, brevtype = "VEDTAK_INNVILGET", journalpostId = null, distribuert = false)
        assertBrev(behandlingUuid = delvisUuid, brevtype = "VEDTAK_DELVIS_INNVILGET", journalpostId = "111", distribuert = false)
        assertBrev(behandlingUuid = avslagUuid, brevtype = "VEDTAK_AVSLAG", journalpostId = "222", distribuert = true)
        assertBrev(behandlingUuid = henlagtUuid, brevtype = "HENLEGGELSE", journalpostId = null, distribuert = false)

        assertBehandling(behandlingUuid = innvilgetUuid, begrunnelse = null, publishedAt = null)
        assertBehandling(
            behandlingUuid = delvisUuid,
            begrunnelse = "Begrunnelse",
            publishedAt = OffsetDateTime.parse("2026-01-12T09:00:00Z"),
        )
    }

    /**
     * Behandlingen beholder feltene sine gjennom omdøpingen, inkludert publiseringstidspunktet
     * som styrer om den skal publiseres på Kafka på nytt. Verifiserer også at periodene
     * fortsatt peker på riktig behandling etter at fremmednøkkelen ble døpt om.
     */
    private fun assertBehandling(
        behandlingUuid: UUID,
        begrunnelse: String?,
        publishedAt: OffsetDateTime?,
    ) {
        pg.postgresDatabase.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT behandling.behandlet_av,
                           behandling.behandlet_tidspunkt,
                           behandling.begrunnelse,
                           behandling.behandling_published_at,
                           (SELECT COUNT(*) FROM VEDTAK_PERIODE p WHERE p.behandling_id = behandling.id) AS perioder
                    FROM BEHANDLING behandling
                    WHERE behandling.uuid = ?
                    """,
                ).use { statement ->
                    statement.setObject(1, behandlingUuid)
                    statement.executeQuery().use { rs ->
                        assertEquals(true, rs.next(), "Fant ingen behandling $behandlingUuid")
                        assertEquals("Z990000", rs.getString("behandlet_av"))
                        assertEquals(
                            OffsetDateTime.parse("2026-01-10T12:00:00Z").toInstant(),
                            rs.getObject("behandlet_tidspunkt", OffsetDateTime::class.java).toInstant(),
                        )
                        assertEquals(begrunnelse, rs.getString("begrunnelse"))
                        assertEquals(
                            publishedAt?.toInstant(),
                            rs.getObject("behandling_published_at", OffsetDateTime::class.java)?.toInstant(),
                        )
                        assertEquals(1, rs.getInt("perioder"))
                    }
                }
        }
    }

    /**
     * Brevet må arve vedtakets uuid, ellers mister vi dedupliseringsnøkkelen mot dokarkiv.
     */
    private fun assertBrev(
        behandlingUuid: UUID,
        brevtype: String,
        journalpostId: String?,
        distribuert: Boolean,
    ) {
        pg.postgresDatabase.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT brev.uuid, brev.brevtype, brev.journalpost_id, brev.journalfort_tidspunkt, brev.distribuert_tidspunkt,
                           brev.document = ?::jsonb AS dokument_bevart
                    FROM BREV brev
                             JOIN BEHANDLING behandling ON behandling.id = brev.behandling_id
                    WHERE behandling.uuid = ?
                    """,
                ).use { statement ->
                    statement.setString(1, DOCUMENT_JSON)
                    statement.setObject(2, behandlingUuid)
                    statement.executeQuery().use { rs ->
                        assertEquals(true, rs.next(), "Fant ingen brev for behandling $behandlingUuid")
                        assertEquals(behandlingUuid, rs.getObject("uuid", UUID::class.java))
                        assertEquals(brevtype, rs.getString("brevtype"))
                        assertEquals(true, rs.getBoolean("dokument_bevart"), "Dokumentet ble ikke bevart")
                        assertEquals(journalpostId, rs.getString("journalpost_id"))
                        if (journalpostId == null) {
                            assertNull(rs.getObject("journalfort_tidspunkt"))
                        } else {
                            assertNotNull(rs.getObject("journalfort_tidspunkt"))
                        }
                        assertEquals(distribuert, rs.getObject("distribuert_tidspunkt") != null)
                    }
                }
        }
    }

    private fun count(table: String): Int =
        pg.postgresDatabase.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM $table").use { statement ->
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun insertVedtakForNySoknad(
        utfall: String,
        journalpostId: String?,
        distribuertTidspunkt: OffsetDateTime? = null,
        publishedAt: OffsetDateTime? = null,
    ): UUID {
        val vedtakUuid = UUID.randomUUID()

        pg.postgresDatabase.connection.use { connection ->
            val soknadId =
                connection
                    .prepareStatement(
                        """
                        INSERT INTO SOKNAD (uuid, ekstern_id, personident, innsendt_tidspunkt)
                        VALUES (?, ?, '11111111111', ?)
                        RETURNING id
                        """,
                    ).use { statement ->
                        statement.setObject(1, UUID.randomUUID())
                        statement.setObject(2, UUID.randomUUID())
                        statement.setObject(3, OffsetDateTime.parse("2026-01-02T08:00:00Z"))
                        statement.executeQuery().use { rs ->
                            rs.next()
                            rs.getInt(1)
                        }
                    }

            connection
                .prepareStatement("INSERT INTO SOKNAD_PERIODE (soknad_id, fom, tom) VALUES (?, ?, ?)")
                .use { statement ->
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
                            uuid, soknad_id, utfall, fattet_av, fattet_tidspunkt, begrunnelse, document,
                            journalpost_id, journalfort_tidspunkt, distribuert_tidspunkt, vedtak_published_at
                        )
                        VALUES (?, ?, ?, 'Z990000', ?, ?, ?::jsonb, ?, ?, ?, ?)
                        RETURNING id
                        """,
                    ).use { statement ->
                        statement.setObject(1, vedtakUuid)
                        statement.setInt(2, soknadId)
                        statement.setString(3, utfall)
                        statement.setObject(4, OffsetDateTime.parse("2026-01-10T12:00:00Z"))
                        statement.setString(5, if (utfall == "INNVILGET") null else "Begrunnelse")
                        statement.setString(6, DOCUMENT_JSON)
                        statement.setString(7, journalpostId)
                        statement.setObject(8, journalpostId?.let { OffsetDateTime.parse("2026-01-11T12:00:00Z") })
                        statement.setObject(9, distribuertTidspunkt)
                        statement.setObject(10, publishedAt)
                        statement.executeQuery().use { rs ->
                            rs.next()
                            rs.getInt(1)
                        }
                    }

            connection
                .prepareStatement("INSERT INTO VEDTAK_PERIODE (vedtak_id, fom, tom) VALUES (?, ?, ?)")
                .use { statement ->
                    statement.setInt(1, vedtakId)
                    statement.setObject(2, LocalDate.of(2026, 1, 5))
                    statement.setObject(3, LocalDate.of(2026, 1, 9))
                    statement.executeUpdate()
                }
        }

        return vedtakUuid
    }

    companion object {
        private const val DOCUMENT_JSON =
            """[{"type": "PARAGRAPH", "title": "Tittel", "texts": ["Innhold"]}]"""
    }
}
