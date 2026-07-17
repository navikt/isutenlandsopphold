package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.application.Transaction
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.database.jdbcConnection
import no.nav.syfo.utenlandsopphold.infrastructure.database.toList
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SoknadRepository(
    private val database: DatabaseInterface,
) : ISoknadRepository {
    private fun <T> withConnection(
        isolation: Int? = null,
        block: (Connection) -> T,
    ): T =
        database.connection.use { connection ->
            try {
                isolation?.let { connection.transactionIsolation = it }
                val result = block(connection)
                connection.commit()
                result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }

    override fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat =
        withConnection { connection ->
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val pSoknad = connection.createSoknad(soknad, now)

            if (pSoknad != null) {
                connection.createSoknadPerioder(pSoknad.id, soknad.soktePerioder)
                LagreMottattSoknadResultat.LAGRET
            } else {
                LagreMottattSoknadResultat.ALLEREDE_LAGRET
            }
        }

    override fun hentSoknad(soknadId: UUID): Soknad? =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            connection.getSoknad(soknadId)
        }

    override fun hentSoknadForUpdate(
        transaction: Transaction,
        soknadId: UUID,
    ): Soknad? = transaction.jdbcConnection().getSoknadForUpdate(soknadId)

    override fun hentSoknader(personident: Personident): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getSoknader(personident)
            connection.toSoknader(pSoknader)
        }

    override fun lagreVedtak(
        transaction: Transaction,
        soknadMedVedtak: Soknad,
    ): Soknad {
        val vedtak =
            checkNotNull(soknadMedVedtak.vedtak) {
                "Søknad ${soknadMedVedtak.id} mangler vedtak etter fattVedtak"
            }

        transaction.jdbcConnection().lagreVedtak(soknadMedVedtak.id, vedtak)

        return soknadMedVedtak
    }

    override fun getIkkeJournalforteSoknader(fattetBefore: Instant): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getIkkeJournalforteSoknader(fattetBefore)
            connection.toSoknader(pSoknader)
        }

    override fun setVedtakJournalfort(
        vedtakId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: Instant,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_VEDTAK_JOURNALFORT).use {
                it.setString(1, journalpostId.value)
                it.setTimestamp(2, Timestamp.from(journalfortTidspunkt))
                it.setObject(3, vedtakId)
                it.executeUpdate()
            }
        }
    }

    override fun getSoknaderMedIkkeDistribuerteVedtak(fattetBefore: Instant): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getSoknaderMedIkkeDistribuerteVedtak(fattetBefore)
            connection.toSoknader(pSoknader)
        }

    override fun setVedtakDistribuert(
        vedtakId: UUID,
        distribuertTidspunkt: Instant,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_VEDTAK_DISTRIBUERT).use {
                it.setTimestamp(1, Timestamp.from(distribuertTidspunkt))
                it.setObject(2, vedtakId)
                it.executeUpdate()
            }
        }
    }

    private fun Connection.getSoknad(soknadId: UUID): Soknad? =
        getSoknadBySoknadUuid(soknadId)?.let { pSoknad ->
            toSoknad(pSoknad)
        }

    private fun Connection.getSoknadForUpdate(soknadId: UUID): Soknad? =
        getSoknadBySoknadUuidForUpdate(soknadId)?.let { pSoknad ->
            toSoknad(pSoknad)
        }

    private fun Connection.toSoknad(pSoknad: PSoknad): Soknad = toSoknader(listOf(pSoknad)).single()

    private fun Connection.toSoknader(pSoknader: List<PSoknad>): List<Soknad> {
        if (pSoknader.isEmpty()) return emptyList()

        val soknadIds = pSoknader.map { it.id }
        val perioderPerSoknad = getSoknadPerioder(soknadIds).groupBy { it.soknadId }
        val vedtakPerSoknad = getVedtak(soknadIds).associateBy { it.soknadId }
        val vedtakPerioderPerVedtak =
            getVedtakPerioder(vedtakPerSoknad.values.map { it.id })
                .groupBy { it.vedtakId }

        return pSoknader.map { pSoknad ->
            val pVedtak = vedtakPerSoknad[pSoknad.id]
            pSoknad.toSoknad(
                soktePerioder = perioderPerSoknad[pSoknad.id].orEmpty(),
                vedtak = pVedtak,
                vedtakPerioder = pVedtak?.let { vedtakPerioderPerVedtak[it.id] }.orEmpty(),
            )
        }
    }

    private fun Connection.getSoknadBySoknadUuid(soknadId: UUID): PSoknad? =
        prepareStatement(GET_SOKNADER_BY_UUID).use {
            it.setObject(1, soknadId)
            it.executeQuery().toList { toPSoknad() }.singleOrNull()
        }

    private fun Connection.getSoknadBySoknadUuidForUpdate(soknadId: UUID): PSoknad? =
        prepareStatement(GET_SOKNADER_BY_UUID_FOR_UPDATE).use {
            it.setObject(1, soknadId)
            it.executeQuery().toList { toPSoknad() }.singleOrNull()
        }

    private fun Connection.getSoknader(personident: Personident): List<PSoknad> =
        prepareStatement(GET_SOKNADER).use {
            it.setString(1, personident.value)
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getSoknadPerioder(soknadIds: List<Int>): List<PSoknadPeriode> =
        prepareStatement(GET_SOKNAD_PERIODER).use {
            it.setArray(1, createArrayOf("integer", soknadIds.toTypedArray()))
            it.executeQuery().toList { toPSoknadPeriode() }
        }

    private fun Connection.getVedtak(soknadIds: List<Int>): List<PVedtak> =
        prepareStatement(GET_VEDTAK).use {
            it.setArray(1, createArrayOf("integer", soknadIds.toTypedArray()))
            it.executeQuery().toList { toPVedtak() }
        }

    private fun Connection.getIkkeJournalforteSoknader(fattetBefore: Instant): List<PSoknad> =
        prepareStatement(GET_IKKE_JOURNALFORTE_SOKNADER).use {
            it.setTimestamp(1, Timestamp.from(fattetBefore))
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getSoknaderMedIkkeDistribuerteVedtak(fattetBefore: Instant): List<PSoknad> =
        prepareStatement(GET_IKKE_DISTRIBUERTE_SOKNADER).use {
            it.setTimestamp(1, Timestamp.from(fattetBefore))
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getVedtakPerioder(vedtakIds: List<Int>): List<PVedtakPeriode> {
        if (vedtakIds.isEmpty()) return emptyList()
        return prepareStatement(GET_VEDTAK_PERIODER).use {
            it.setArray(1, createArrayOf("integer", vedtakIds.toTypedArray()))
            it.executeQuery().toList { toPVedtakPeriode() }
        }
    }

    private fun Connection.createSoknad(
        soknad: Soknad,
        now: OffsetDateTime,
    ): PSoknad? =
        prepareStatement(CREATE_SOKNAD).use {
            it.setObject(1, soknad.id)
            it.setObject(2, soknad.eksternId)
            it.setString(3, soknad.personident.value)
            it.setObject(4, soknad.innsendtTidspunkt)
            it.setObject(5, now)
            it.executeQuery().toList { toPSoknad() }.singleOrNull()
        }

    private fun Connection.createSoknadPerioder(
        soknadId: Int,
        soknadPerioder: List<Periode>,
    ) {
        soknadPerioder.forEach { periode ->
            prepareStatement(CREATE_SOKNAD_PERIODE).use {
                it.setInt(1, soknadId)
                it.setDate(2, Date.valueOf(periode.fom))
                it.setDate(3, Date.valueOf(periode.tom))
                it.executeUpdate()
            }
        }
    }

    private fun Connection.lagreVedtak(
        soknadId: UUID,
        vedtak: Vedtak,
    ) {
        val documentJson =
            PGobject().apply {
                type = "jsonb"
                value = vedtak.document.serializeToJson()
            }
        val pVedtak =
            prepareStatement(CREATE_VEDTAK).use {
                it.setObject(1, vedtak.vedtakId)
                it.setString(2, vedtak.utfall.dbValue())
                it.setString(3, vedtak.fattetAv.value)
                it.setObject(4, vedtak.fattetTidspunkt.atOffset(ZoneOffset.UTC))
                it.setObject(5, documentJson)
                it.setObject(6, soknadId)
                it.executeQuery().toList { toPVedtak() }.singleOrNull()
                    ?: throw IllegalArgumentException("Fant ikke søknad med id $soknadId")
            }
        createVedtakPerioder(pVedtak.id, vedtak.innvilgetePerioder)
    }

    private fun Connection.createVedtakPerioder(
        vedtakId: Int,
        vedtakPerioder: List<Periode>,
    ) {
        vedtakPerioder.forEach { periode ->
            prepareStatement(CREATE_VEDTAK_PERIODE).use {
                it.setInt(1, vedtakId)
                it.setDate(2, Date.valueOf(periode.fom))
                it.setDate(3, Date.valueOf(periode.tom))
                it.executeUpdate()
            }
        }
    }

    companion object {
        private const val GET_SOKNADER_BY_UUID =
            """
                SELECT * FROM soknad WHERE uuid = ?
            """

        private const val GET_SOKNADER_BY_UUID_FOR_UPDATE =
            """
                SELECT * FROM soknad WHERE uuid = ? FOR UPDATE
            """

        private const val GET_SOKNADER =
            """
                SELECT * FROM soknad WHERE personident = ? ORDER BY innsendt_tidspunkt DESC
            """

        private const val GET_SOKNAD_PERIODER =
            """
                SELECT * FROM soknad_periode WHERE soknad_id = ANY(?) ORDER BY fom ASC
            """

        private const val GET_VEDTAK =
            """
                SELECT * FROM vedtak WHERE soknad_id = ANY(?)
            """

        private const val GET_IKKE_JOURNALFORTE_SOKNADER =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN vedtak v ON v.soknad_id = s.id
                WHERE v.journalpost_id IS NULL AND v.fattet_tidspunkt < ?
            """

        private const val GET_IKKE_DISTRIBUERTE_SOKNADER =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN vedtak v ON v.soknad_id = s.id
                WHERE v.journalpost_id IS NOT NULL AND v.distribuert_tidspunkt IS NULL
                    AND v.fattet_tidspunkt < ?
            """

        private const val SET_VEDTAK_JOURNALFORT =
            """
                UPDATE vedtak
                SET journalpost_id = ?, journalfort_tidspunkt = ?
                WHERE uuid = ?
            """

        private const val SET_VEDTAK_DISTRIBUERT =
            """
                UPDATE vedtak
                SET distribuert_tidspunkt = ?
                WHERE uuid = ?
            """

        private const val GET_VEDTAK_PERIODER =
            """
                SELECT * FROM vedtak_periode WHERE vedtak_id = ANY(?) ORDER BY fom ASC
            """

        private const val CREATE_SOKNAD =
            """
                INSERT INTO soknad (
                    uuid,
                    ekstern_id,
                    personident,
                    innsendt_tidspunkt,
                    created_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (ekstern_id) DO NOTHING
                RETURNING *
            """

        private const val CREATE_SOKNAD_PERIODE =
            """
                INSERT INTO soknad_periode (
                    soknad_id,
                    fom,
                    tom
                ) VALUES (?, ?, ?)
            """

        private const val CREATE_VEDTAK =
            """
                INSERT INTO vedtak (
                    uuid,
                    soknad_id,
                    utfall,
                    fattet_av,
                    fattet_tidspunkt,
                    document
                )
                SELECT ?, s.id, ?, ?, ?, ?
                FROM soknad s
                WHERE s.uuid = ?
                RETURNING *
            """

        private const val CREATE_VEDTAK_PERIODE =
            """
                INSERT INTO vedtak_periode (
                    vedtak_id,
                    fom,
                    tom
                ) VALUES (?, ?, ?)
            """
    }
}

internal fun ResultSet.toPSoknad(): PSoknad =
    PSoknad(
        id = getInt("id"),
        uuid = getObject("uuid", UUID::class.java),
        eksternId = getObject("ekstern_id", UUID::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        personident = Personident(getString("personident")),
        innsendtTidspunkt = getObject("innsendt_tidspunkt", OffsetDateTime::class.java),
    )

internal fun ResultSet.toPSoknadPeriode(): PSoknadPeriode =
    PSoknadPeriode(
        id = getInt("id"),
        soknadId = getInt("soknad_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
    )

internal fun ResultSet.toPVedtakPeriode(): PVedtakPeriode =
    PVedtakPeriode(
        id = getInt("id"),
        vedtakId = getInt("vedtak_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
    )

internal fun ResultSet.toPVedtak(): PVedtak =
    PVedtak(
        id = getInt("id"),
        uuid = getObject("uuid", UUID::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        soknadId = getInt("soknad_id"),
        utfall = getString("utfall"),
        fattetAv = getString("fattet_av"),
        fattetTidspunkt = getObject("fattet_tidspunkt", OffsetDateTime::class.java),
        document = getString("document"),
        journalpostId = getString("journalpost_id"),
        journalfortTidspunkt = getObject("journalfort_tidspunkt", OffsetDateTime::class.java),
        distribuertTidspunkt = getObject("distribuert_tidspunkt", OffsetDateTime::class.java),
    )
