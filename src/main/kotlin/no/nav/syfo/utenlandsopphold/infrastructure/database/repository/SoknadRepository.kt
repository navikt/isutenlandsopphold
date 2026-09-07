package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.application.Transaction
import no.nav.syfo.utenlandsopphold.domain.Behandlingsutfall
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.infrastructure.database.DatabaseInterface
import no.nav.syfo.utenlandsopphold.infrastructure.database.jdbcConnection
import no.nav.syfo.utenlandsopphold.infrastructure.database.toList
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
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
            val now = OffsetDateTime.now()
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

    override fun lagreBehandlingsutfall(
        transaction: Transaction,
        soknadMedBehandlingsutfall: Soknad,
    ): Soknad {
        val behandlingsutfall =
            checkNotNull(soknadMedBehandlingsutfall.behandlingsutfall) {
                "Søknad ${soknadMedBehandlingsutfall.id} mangler behandlingsutfall etter registrerBehandlingsutfall"
            }

        transaction.jdbcConnection().lagreBehandlingsutfall(soknadMedBehandlingsutfall.id, behandlingsutfall)

        return soknadMedBehandlingsutfall
    }

    override fun getIkkeJournalforteSoknader(fattetBefore: OffsetDateTime): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getIkkeJournalforteSoknader(fattetBefore)
            connection.toSoknader(pSoknader)
        }

    override fun setBehandlingsutfallJournalfort(
        behandlingsutfallId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: OffsetDateTime,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_BEHANDLINGSUTFALL_JOURNALFORT).use {
                it.setString(1, journalpostId.value)
                it.setObject(2, journalfortTidspunkt)
                it.setObject(3, behandlingsutfallId)
                it.executeUpdate()
            }
        }
    }

    override fun getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore: OffsetDateTime): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore)
            connection.toSoknader(pSoknader)
        }

    override fun setBehandlingsutfallDistribuert(
        behandlingsutfallId: UUID,
        distribuertTidspunkt: OffsetDateTime,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_BEHANDLINGSUTFALL_DISTRIBUERT).use {
                it.setObject(1, distribuertTidspunkt)
                it.setObject(2, behandlingsutfallId)
                it.executeUpdate()
            }
        }
    }

    override fun getUnpublishedSoknader(): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getUnpublishedSoknader()
            connection.toSoknader(pSoknader)
        }

    override fun setSoknadPublished(
        soknadId: UUID,
        publishedAt: OffsetDateTime,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_SOKNAD_PUBLISHED_AT).use {
                it.setObject(1, publishedAt)
                it.setObject(2, soknadId)
                it.executeUpdate()
            }
        }
    }

    override fun getSoknaderMedUnpublishedBehandlingsutfall(): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getSoknaderMedUnpublishedBehandlingsutfall()
            connection.toSoknader(pSoknader)
        }

    override fun setBehandlingsutfallPublished(
        behandlingsutfallId: UUID,
        publishedAt: OffsetDateTime,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_BEHANDLINGSUTFALL_PUBLISHED_AT).use {
                it.setObject(1, publishedAt)
                it.setObject(2, behandlingsutfallId)
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
        val behandlingsutfallPerSoknad = getBehandlingsutfall(soknadIds).associateBy { it.soknadId }
        val perioderPerBehandlingsutfall =
            getBehandlingsutfallPerioder(behandlingsutfallPerSoknad.values.map { it.id })
                .groupBy { it.behandlingsutfallId }

        return pSoknader.map { pSoknad ->
            val pBehandlingsutfall = behandlingsutfallPerSoknad[pSoknad.id]
            pSoknad.toSoknad(
                soktePerioder = perioderPerSoknad[pSoknad.id].orEmpty(),
                behandlingsutfall = pBehandlingsutfall,
                behandlingsutfallPerioder = pBehandlingsutfall?.let { perioderPerBehandlingsutfall[it.id] }.orEmpty(),
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

    private fun Connection.getBehandlingsutfall(soknadIds: List<Int>): List<PBehandlingsutfall> =
        prepareStatement(GET_BEHANDLINGSUTFALL).use {
            it.setArray(1, createArrayOf("integer", soknadIds.toTypedArray()))
            it.executeQuery().toList { toPBehandlingsutfall() }
        }

    private fun Connection.getIkkeJournalforteSoknader(fattetBefore: OffsetDateTime): List<PSoknad> =
        prepareStatement(GET_IKKE_JOURNALFORTE_SOKNADER).use {
            it.setObject(1, fattetBefore)
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore: OffsetDateTime): List<PSoknad> =
        prepareStatement(GET_IKKE_DISTRIBUERTE_SOKNADER).use {
            it.setObject(1, fattetBefore)
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getUnpublishedSoknader(): List<PSoknad> =
        prepareStatement(GET_UNPUBLISHED_SOKNADER).use {
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getSoknaderMedUnpublishedBehandlingsutfall(): List<PSoknad> =
        prepareStatement(GET_SOKNADER_MED_UNPUBLISHED_BEHANDLINGSUTFALL).use {
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getBehandlingsutfallPerioder(behandlingsutfallIds: List<Int>): List<PBehandlingsutfallPeriode> {
        if (behandlingsutfallIds.isEmpty()) return emptyList()
        return prepareStatement(GET_VEDTAK_PERIODER).use {
            it.setArray(1, createArrayOf("integer", behandlingsutfallIds.toTypedArray()))
            it.executeQuery().toList { toPBehandlingsutfallPeriode() }
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

    private fun Connection.lagreBehandlingsutfall(
        soknadId: UUID,
        behandlingsutfall: Behandlingsutfall,
    ) {
        val documentJson =
            PGobject().apply {
                type = "jsonb"
                value = behandlingsutfall.document.serializeToJson()
            }
        val pBehandlingsutfall =
            prepareStatement(CREATE_BEHANDLINGSUTFALL).use {
                it.setObject(1, behandlingsutfall.behandlingsutfallId)
                it.setString(2, behandlingsutfall.utfall.dbValue())
                it.setString(3, behandlingsutfall.fattetAv.value)
                it.setObject(4, behandlingsutfall.fattetTidspunkt)
                it.setObject(5, documentJson)
                it.setString(6, behandlingsutfall.begrunnelse)
                it.setObject(7, soknadId)
                it.executeQuery().toList { toPBehandlingsutfall() }.singleOrNull()
                    ?: throw IllegalArgumentException("Fant ikke søknad med id $soknadId")
            }
        createBehandlingsutfallPerioder(pBehandlingsutfall.id, behandlingsutfall.innvilgedePerioder)
    }

    private fun Connection.createBehandlingsutfallPerioder(
        behandlingsutfallId: Int,
        behandlingsutfallPerioder: List<Periode>,
    ) {
        behandlingsutfallPerioder.forEach { periode ->
            prepareStatement(CREATE_VEDTAK_PERIODE).use {
                it.setInt(1, behandlingsutfallId)
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

        private const val GET_BEHANDLINGSUTFALL =
            """
                SELECT * FROM behandlingsutfall WHERE soknad_id = ANY(?)
            """

        private const val GET_IKKE_JOURNALFORTE_SOKNADER =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN behandlingsutfall b ON b.soknad_id = s.id
                WHERE b.journalpost_id IS NULL AND b.fattet_tidspunkt < ?
            """

        private const val GET_IKKE_DISTRIBUERTE_SOKNADER =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN behandlingsutfall b ON b.soknad_id = s.id
                WHERE b.journalpost_id IS NOT NULL AND b.distribuert_tidspunkt IS NULL
                    AND b.fattet_tidspunkt < ?
            """

        private const val GET_UNPUBLISHED_SOKNADER =
            """
                SELECT * FROM soknad WHERE soknad_published_at IS NULL
            """

        private const val GET_SOKNADER_MED_UNPUBLISHED_BEHANDLINGSUTFALL =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN behandlingsutfall b ON b.soknad_id = s.id
                WHERE b.behandlingsutfall_published_at IS NULL AND s.soknad_published_at IS NOT NULL
            """

        private const val SET_BEHANDLINGSUTFALL_JOURNALFORT =
            """
                UPDATE behandlingsutfall
                SET journalpost_id = ?, journalfort_tidspunkt = ?
                WHERE uuid = ?
            """

        private const val SET_BEHANDLINGSUTFALL_DISTRIBUERT =
            """
                UPDATE behandlingsutfall
                SET distribuert_tidspunkt = ?
                WHERE uuid = ?
            """

        private const val SET_SOKNAD_PUBLISHED_AT =
            """
                UPDATE soknad
                SET soknad_published_at = ?
                WHERE uuid = ?
            """

        private const val SET_BEHANDLINGSUTFALL_PUBLISHED_AT =
            """
                UPDATE behandlingsutfall
                SET behandlingsutfall_published_at = ?
                WHERE uuid = ?
            """

        private const val GET_VEDTAK_PERIODER =
            """
                SELECT * FROM VEDTAK_PERIODE WHERE behandlingsutfall_id = ANY(?) ORDER BY fom ASC
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

        private const val CREATE_BEHANDLINGSUTFALL =
            """
                INSERT INTO behandlingsutfall (
                    uuid,
                    soknad_id,
                    utfall,
                    fattet_av,
                    fattet_tidspunkt,
                    document,
                    begrunnelse
                )
                SELECT ?, s.id, ?, ?, ?, ?, ?
                FROM soknad s
                WHERE s.uuid = ?
                RETURNING *
            """

        private const val CREATE_VEDTAK_PERIODE =
            """
                INSERT INTO VEDTAK_PERIODE (
                    behandlingsutfall_id,
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

internal fun ResultSet.toPBehandlingsutfallPeriode(): PBehandlingsutfallPeriode =
    PBehandlingsutfallPeriode(
        id = getInt("id"),
        behandlingsutfallId = getInt("behandlingsutfall_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
    )

internal fun ResultSet.toPBehandlingsutfall(): PBehandlingsutfall =
    PBehandlingsutfall(
        id = getInt("id"),
        uuid = getObject("uuid", UUID::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        soknadId = getInt("soknad_id"),
        utfall = getString("utfall"),
        fattetAv = getString("fattet_av"),
        fattetTidspunkt = getObject("fattet_tidspunkt", OffsetDateTime::class.java),
        document = getString("document"),
        begrunnelse = getString("begrunnelse"),
        journalpostId = getString("journalpost_id"),
        journalfortTidspunkt = getObject("journalfort_tidspunkt", OffsetDateTime::class.java),
        distribuertTidspunkt = getObject("distribuert_tidspunkt", OffsetDateTime::class.java),
    )
