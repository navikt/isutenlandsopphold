package no.nav.syfo.utenlandsopphold.infrastructure.database

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.application.IVedtakRepository
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JDBC-implementasjon av [IVedtakRepository]. Dekker kun det journalføringsjobben
 * trenger: å lese søknader med u-journalført vedtak, og å markere et vedtak som
 * journalført. Opprettelse av nye søknader/vedtak er utenfor scope her.
 */
class VedtakRepository(
    private val database: DatabaseInterface,
) : IVedtakRepository {
    private val mapper = configuredJacksonMapper()

    override fun getUjournalforteSoknader(): List<Soknad> =
        database.connection.use { connection ->
            val rader =
                connection.prepareStatement(GET_UJOURNALFORTE_SOKNADER_QUERY).use { preparedStatement ->
                    preparedStatement.executeQuery().toList { toSoknadOgVedtakRad() }
                }
            connection.commit()

            rader.map { rad ->
                val soktePerioder = getSoktePerioder(connection, rad.soknadId)
                val innvilgetePerioder = getVedtakPerioder(connection, rad.vedtakDatabaseId)

                Soknad(
                    id = rad.soknadId,
                    eksternId = rad.eksternId,
                    personident = rad.personident,
                    soktePerioder = soktePerioder,
                    innsendtTidspunkt = rad.innsendtTidspunkt,
                    vedtak = rad.toVedtak(innvilgetePerioder),
                )
            }
        }

    private fun getSoktePerioder(
        connection: Connection,
        soknadId: UUID,
    ): List<Periode> =
        connection.prepareStatement(GET_SOKNAD_PERIODER_QUERY).use { preparedStatement ->
            preparedStatement.setObject(1, soknadId)
            preparedStatement.executeQuery().toList {
                Periode(fom = getDate("fom").toLocalDate(), tom = getDate("tom").toLocalDate())
            }
        }

    private fun getVedtakPerioder(
        connection: Connection,
        vedtakId: Int,
    ): List<Periode> =
        connection.prepareStatement(GET_VEDTAK_PERIODER_QUERY).use { preparedStatement ->
            preparedStatement.setInt(1, vedtakId)
            preparedStatement.executeQuery().toList {
                Periode(fom = getObject("fom", LocalDate::class.java), tom = getObject("tom", LocalDate::class.java))
            }
        }

    override fun setVedtakJournalfort(
        vedtakId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: Instant,
    ) {
        database.connection.use { connection ->
            connection.prepareStatement(SET_VEDTAK_JOURNALFORT_QUERY).use { preparedStatement ->
                preparedStatement.setString(1, journalpostId.value)
                preparedStatement.setTimestamp(2, Timestamp.from(journalfortTidspunkt))
                preparedStatement.setObject(3, vedtakId)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    private fun ResultSet.toSoknadOgVedtakRad(): SoknadOgVedtakRad {
        val document: List<DocumentComponent> = mapper.readValue(getString("document"))

        return SoknadOgVedtakRad(
            soknadId = UUID.fromString(getString("soknad_uuid")),
            eksternId = UUID.fromString(getString("ekstern_id")),
            personident = Personident(getString("personident")),
            innsendtTidspunkt = getTimestamp("innsendt_tidspunkt").toInstant(),
            vedtakDatabaseId = getInt("vedtak_id"),
            vedtakId = UUID.fromString(getString("vedtak_uuid")),
            utfall = toUtfall(getString("utfall")),
            fattetAv = Navident(getString("fattet_av")),
            fattetTidspunkt = getTimestamp("vedtak_fattet_tidspunkt").toInstant(),
            document = document,
            journalpostId = getString("journalpost_id")?.let { JournalpostId(it) },
            journalfortTidspunkt = getTimestamp("journalfort_tidspunkt")?.toInstant(),
        )
    }

    private fun toUtfall(utfall: String): Utfall =
        when (utfall) {
            "INNVILGET" -> Utfall.Innvilget
            else -> throw IllegalStateException("Ukjent utfall $utfall")
        }

    private data class SoknadOgVedtakRad(
        val soknadId: UUID,
        val eksternId: UUID,
        val personident: Personident,
        val innsendtTidspunkt: Instant,
        val vedtakDatabaseId: Int,
        val vedtakId: UUID,
        val utfall: Utfall,
        val fattetAv: Navident,
        val fattetTidspunkt: Instant,
        val document: List<DocumentComponent>,
        val journalpostId: JournalpostId?,
        val journalfortTidspunkt: Instant?,
    ) {
        fun toVedtak(innvilgetePerioder: List<Periode>): Vedtak =
            Vedtak(
                vedtakId = vedtakId,
                utfall = utfall,
                fattetAv = fattetAv,
                fattetTidspunkt = fattetTidspunkt,
                innvilgetePerioder = innvilgetePerioder,
                document = document,
                journalpostId = journalpostId,
                journalfortTidspunkt = journalfortTidspunkt,
            )
    }

    companion object {
        private const val GET_UJOURNALFORTE_SOKNADER_QUERY =
            """
            SELECT s.uuid                  AS soknad_uuid,
                   s.ekstern_id             AS ekstern_id,
                   s.personident            AS personident,
                   s.innsendt_tidspunkt     AS innsendt_tidspunkt,
                   v.id                     AS vedtak_id,
                   v.uuid                   AS vedtak_uuid,
                   v.utfall                 AS utfall,
                   v.fattet_av              AS fattet_av,
                   v.fattet_tidspunkt       AS vedtak_fattet_tidspunkt,
                   v.document               AS document,
                   v.journalpost_id         AS journalpost_id,
                   v.journalfort_tidspunkt  AS journalfort_tidspunkt
            FROM VEDTAK v
                     INNER JOIN SOKNAD s ON s.id = v.soknad_id
            WHERE v.journalpost_id IS NULL
            """

        private const val GET_SOKNAD_PERIODER_QUERY =
            """
            SELECT sp.fom, sp.tom
            FROM SOKNAD_PERIODE sp
                     INNER JOIN SOKNAD s ON s.id = sp.soknad_id
            WHERE s.uuid = ?
            """

        private const val GET_VEDTAK_PERIODER_QUERY =
            """
            SELECT fom, tom
            FROM VEDTAK_PERIODE
            WHERE vedtak_id = ?
            ORDER BY fom ASC
            """

        private const val SET_VEDTAK_JOURNALFORT_QUERY =
            """
            UPDATE VEDTAK
            SET journalpost_id = ?, journalfort_tidspunkt = ?
            WHERE uuid = ?
            """
    }
}
