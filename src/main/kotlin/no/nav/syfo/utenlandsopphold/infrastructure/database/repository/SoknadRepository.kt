package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.database.toList
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
    override fun hentSoknader(personident: Personident): List<Soknad> =
        database.connection.use { connection ->
            connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ

            val pSoknader = connection.getSoknader(personident)
            if (pSoknader.isEmpty()) {
                connection.commit()
                return@use emptyList()
            }

            val soknadIds = pSoknader.map { it.id }
            val perioderPerSoknad = connection.getPerioder(soknadIds).groupBy { it.soknadId }
            val vedtakPerSoknad = connection.getVedtak(soknadIds).associateBy { it.soknadId }
            val vedtakPerioderPerVedtak =
                connection
                    .getVedtakPerioder(vedtakPerSoknad.values.map { it.id })
                    .groupBy { it.vedtakId }
            connection.commit()

            pSoknader.map { pSoknad ->
                val pVedtak = vedtakPerSoknad[pSoknad.id]
                pSoknad.toSoknad(
                    soktePerioder = perioderPerSoknad[pSoknad.id].orEmpty(),
                    vedtak = pVedtak,
                    vedtakPerioder = pVedtak?.let { vedtakPerioderPerVedtak[it.id] }.orEmpty(),
                )
            }
        }

    override fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat =
        database.connection.use { connection ->
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val pSoknad = connection.createSoknad(soknad, now)
            val resultat =
                if (pSoknad != null) {
                    connection.createPerioder(pSoknad.id, soknad)
                    LagreMottattSoknadResultat.LAGRET
                } else {
                    LagreMottattSoknadResultat.ALLEREDE_LAGRET
                }

            connection.commit()
            resultat
        }

    override fun getIkkeJournalforteSoknader(): List<Soknad> =
        database.connection.use { connection ->
            connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ

            val pSoknader = connection.getIkkeJournalforteSoknader()
            if (pSoknader.isEmpty()) {
                connection.commit()
                return@use emptyList()
            }

            val soknadIds = pSoknader.map { it.id }
            val perioderPerSoknad = connection.getPerioder(soknadIds).groupBy { it.soknadId }
            val vedtakPerSoknad = connection.getVedtak(soknadIds).associateBy { it.soknadId }
            val vedtakPerioderPerVedtak =
                connection
                    .getVedtakPerioder(vedtakPerSoknad.values.map { it.id })
                    .groupBy { it.vedtakId }
            connection.commit()

            pSoknader.map { pSoknad ->
                val pVedtak = vedtakPerSoknad[pSoknad.id]
                pSoknad.toSoknad(
                    soktePerioder = perioderPerSoknad[pSoknad.id].orEmpty(),
                    vedtak = pVedtak,
                    vedtakPerioder = pVedtak?.let { vedtakPerioderPerVedtak[it.id] }.orEmpty(),
                )
            }
        }

    override fun setVedtakJournalfort(
        vedtakId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: Instant,
    ) {
        database.connection.use { connection ->
            connection.prepareStatement(SET_VEDTAK_JOURNALFORT).use {
                it.setString(1, journalpostId.value)
                it.setTimestamp(2, Timestamp.from(journalfortTidspunkt))
                it.setObject(3, vedtakId)
                it.executeUpdate()
            }
            connection.commit()
        }
    }

    private fun Connection.getSoknader(personident: Personident): List<PSoknad> =
        prepareStatement(GET_SOKNADER).use {
            it.setString(1, personident.value)
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getPerioder(soknadIds: List<Int>): List<PSoknadPeriode> =
        prepareStatement(GET_PERIODER).use {
            it.setArray(1, createArrayOf("integer", soknadIds.toTypedArray()))
            it.executeQuery().toList { toPSoknadPeriode() }
        }

    private fun Connection.getVedtak(soknadIds: List<Int>): List<PVedtak> =
        prepareStatement(GET_VEDTAK).use {
            it.setArray(1, createArrayOf("integer", soknadIds.toTypedArray()))
            it.executeQuery().toList { toPVedtak() }
        }

    private fun Connection.getIkkeJournalforteSoknader(): List<PSoknad> =
        prepareStatement(GET_IKKE_JOURNALFORTE_SOKNADER).use {
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

    private fun Connection.createPerioder(
        soknadId: Int,
        soknad: Soknad,
    ) {
        val fomDates = soknad.soktePerioder.map { Date.valueOf(it.fom) }.toTypedArray()
        val tomDates = soknad.soktePerioder.map { Date.valueOf(it.tom) }.toTypedArray()

        prepareStatement(CREATE_PERIODER).use {
            it.setInt(1, soknadId)
            it.setArray(2, createArrayOf("date", fomDates))
            it.setArray(3, createArrayOf("date", tomDates))
            it.executeUpdate()
        }
    }

    private fun Connection.createVedtak(
        soknadId: Int,
        vedtak: Vedtak,
    ) {
        val pVedtak =
            prepareStatement(CREATE_VEDTAK).use {
                it.setObject(1, UUID.randomUUID())
                it.setInt(2, soknadId)
                it.setString(3, vedtak.utfall.dbValue())
                it.setString(4, vedtak.fattetAv.value)
                it.setObject(5, vedtak.fattetTidspunkt.atOffset(ZoneOffset.UTC))
                it.executeQuery().toList { toPVedtak() }.single()
            }
        vedtak.innvilgetePerioder.forEach { periode ->
            createVedtakPeriode(pVedtak.id, periode.fom, periode.tom)
        }
    }

    private fun Connection.createVedtakPeriode(
        vedtakId: Int,
        fom: LocalDate,
        tom: LocalDate,
    ) {
        prepareStatement(CREATE_VEDTAK_PERIODE).use {
            it.setInt(1, vedtakId)
            it.setDate(2, Date.valueOf(fom))
            it.setDate(3, Date.valueOf(tom))
            it.executeUpdate()
        }
    }

    companion object {
        private const val GET_SOKNADER =
            """
                SELECT * FROM soknad WHERE personident = ? ORDER BY innsendt_tidspunkt DESC
            """

        private const val GET_PERIODER =
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
                WHERE v.journalpost_id IS NULL
            """

        private const val SET_VEDTAK_JOURNALFORT =
            """
                UPDATE vedtak
                SET journalpost_id = ?, journalfort_tidspunkt = ?
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

        private const val CREATE_PERIODER =
            """
                INSERT INTO soknad_periode (
                    soknad_id,
                    fom,
                    tom
                )
                SELECT ?, periode.fom, periode.tom
                FROM unnest(?::date[], ?::date[]) AS periode(fom, tom)
            """

        private const val CREATE_VEDTAK =
            """
                INSERT INTO vedtak (
                    uuid,
                    soknad_id,
                    utfall,
                    fattet_av,
                    fattet_tidspunkt
                ) VALUES (?, ?, ?, ?, ?)
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
    )
