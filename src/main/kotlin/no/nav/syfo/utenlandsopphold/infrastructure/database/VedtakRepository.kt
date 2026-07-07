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
                Soknad(
                    id = rad.soknadId,
                    eksternId = rad.eksternId,
                    personident = rad.personident,
                    soktePerioder = getSoktePerioder(connection, rad.soknadId),
                    innsendtTidspunkt = rad.innsendtTidspunkt,
                    vedtak = rad.vedtak,
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

        val vedtak =
            Vedtak(
                vedtakId = UUID.fromString(getString("vedtak_uuid")),
                utfall = toUtfall(getString("utfall")),
                fattetAv = Navident(getString("fattet_av")),
                fattetTidspunkt = getTimestamp("vedtak_created_at").toInstant(),
                document = document,
                journalpostId = getString("journalpost_id")?.let { JournalpostId(it) },
                journalfortTidspunkt = getTimestamp("journalfort_tidspunkt")?.toInstant(),
            )

        return SoknadOgVedtakRad(
            soknadId = UUID.fromString(getString("soknad_uuid")),
            eksternId = UUID.fromString(getString("ekstern_id")),
            personident = Personident(getString("personident")),
            innsendtTidspunkt = getTimestamp("innsendt_tidspunkt").toInstant(),
            vedtak = vedtak,
        )
    }

    private fun toUtfall(utfall: String): Utfall =
        when (utfall) {
            "INNVILGET" -> Utfall.FullInnvilgelse
            else -> throw IllegalStateException("Ukjent utfall $utfall")
        }

    private data class SoknadOgVedtakRad(
        val soknadId: UUID,
        val eksternId: UUID,
        val personident: Personident,
        val innsendtTidspunkt: Instant,
        val vedtak: Vedtak,
    )

    companion object {
        private const val GET_UJOURNALFORTE_SOKNADER_QUERY =
            """
            SELECT s.uuid                  AS soknad_uuid,
                   s.ekstern_id             AS ekstern_id,
                   s.personident            AS personident,
                   s.innsendt_tidspunkt     AS innsendt_tidspunkt,
                   v.uuid                   AS vedtak_uuid,
                   v.utfall                 AS utfall,
                   v.fattet_av              AS fattet_av,
                   v.created_at             AS vedtak_created_at,
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

        private const val SET_VEDTAK_JOURNALFORT_QUERY =
            """
            UPDATE VEDTAK
            SET journalpost_id = ?, journalfort_tidspunkt = ?
            WHERE uuid = ?
            """
    }
}

private fun <T> ResultSet.toList(rowMapper: ResultSet.() -> T): List<T> =
    use {
        val result = mutableListOf<T>()
        while (next()) {
            result.add(rowMapper())
        }
        result
    }
