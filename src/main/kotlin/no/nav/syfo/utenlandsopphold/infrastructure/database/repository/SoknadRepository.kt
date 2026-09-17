package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.application.Transaction
import no.nav.syfo.utenlandsopphold.domain.Behandling
import no.nav.syfo.utenlandsopphold.domain.Brev
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

    override fun lagreBehandling(
        transaction: Transaction,
        behandletSoknad: Soknad,
    ): Soknad {
        val behandling =
            checkNotNull(behandletSoknad.behandling) {
                "Søknad ${behandletSoknad.id} mangler behandling"
            }

        transaction.jdbcConnection().lagreBehandling(behandletSoknad.id, behandling)

        return behandletSoknad
    }

    override fun getIkkeJournalforteSoknader(behandletBefore: OffsetDateTime): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getIkkeJournalforteSoknader(behandletBefore)
            connection.toSoknader(pSoknader)
        }

    override fun setBrevJournalfort(
        brevId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: OffsetDateTime,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_BREV_JOURNALFORT).use {
                it.setString(1, journalpostId.value)
                it.setObject(2, journalfortTidspunkt)
                it.setObject(3, brevId)
                it.executeUpdate()
            }
        }
    }

    override fun getSoknaderMedIkkeDistribuerteBrev(behandletBefore: OffsetDateTime): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getSoknaderMedIkkeDistribuerteBrev(behandletBefore)
            connection.toSoknader(pSoknader)
        }

    override fun setBrevDistribuert(
        brevId: UUID,
        distribuertTidspunkt: OffsetDateTime,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_BREV_DISTRIBUERT).use {
                it.setObject(1, distribuertTidspunkt)
                it.setObject(2, brevId)
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

    override fun getSoknaderMedUnpublishedBehandling(): List<Soknad> =
        withConnection(Connection.TRANSACTION_REPEATABLE_READ) { connection ->
            val pSoknader = connection.getSoknaderMedUnpublishedBehandling()
            connection.toSoknader(pSoknader)
        }

    override fun setBehandlingPublished(
        behandlingId: UUID,
        publishedAt: OffsetDateTime,
    ) {
        withConnection { connection ->
            connection.prepareStatement(SET_BEHANDLING_PUBLISHED_AT).use {
                it.setObject(1, publishedAt)
                it.setObject(2, behandlingId)
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
        val behandlingPerSoknad = getBehandlinger(soknadIds).associateBy { it.soknadId }
        val behandlingIds = behandlingPerSoknad.values.map { it.id }
        val behandlingPerioderPerBehandling =
            getBehandlingPerioder(behandlingIds).groupBy { it.behandlingId }
        val brevPerBehandling = getBrev(behandlingIds).associateBy { it.behandlingId }

        return pSoknader.map { pSoknad ->
            val pBehandling = behandlingPerSoknad[pSoknad.id]
            pSoknad.toSoknad(
                soktePerioder = perioderPerSoknad[pSoknad.id].orEmpty(),
                behandling = pBehandling,
                behandlingPerioder = pBehandling?.let { behandlingPerioderPerBehandling[it.id] }.orEmpty(),
                brev = pBehandling?.let { brevPerBehandling[it.id] },
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

    private fun Connection.getBehandlinger(soknadIds: List<Int>): List<PBehandling> =
        prepareStatement(GET_BEHANDLINGER).use {
            it.setArray(1, createArrayOf("integer", soknadIds.toTypedArray()))
            it.executeQuery().toList { toPBehandling() }
        }

    private fun Connection.getIkkeJournalforteSoknader(behandletBefore: OffsetDateTime): List<PSoknad> =
        prepareStatement(GET_IKKE_JOURNALFORTE_SOKNADER).use {
            it.setObject(1, behandletBefore)
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getSoknaderMedIkkeDistribuerteBrev(behandletBefore: OffsetDateTime): List<PSoknad> =
        prepareStatement(GET_IKKE_DISTRIBUERTE_SOKNADER).use {
            it.setObject(1, behandletBefore)
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getUnpublishedSoknader(): List<PSoknad> =
        prepareStatement(GET_UNPUBLISHED_SOKNADER).use {
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getSoknaderMedUnpublishedBehandling(): List<PSoknad> =
        prepareStatement(GET_SOKNADER_MED_UNPUBLISHED_BEHANDLING).use {
            it.executeQuery().toList { toPSoknad() }
        }

    private fun Connection.getBehandlingPerioder(behandlingIds: List<Int>): List<PBehandlingPeriode> {
        if (behandlingIds.isEmpty()) return emptyList()

        return prepareStatement(GET_BEHANDLING_PERIODER).use {
            it.setArray(1, createArrayOf("integer", behandlingIds.toTypedArray()))
            it.executeQuery().toList { toPBehandlingPeriode() }
        }
    }

    private fun Connection.getBrev(behandlingIds: List<Int>): List<PBrev> {
        if (behandlingIds.isEmpty()) return emptyList()

        return prepareStatement(GET_BREV).use {
            it.setArray(1, createArrayOf("integer", behandlingIds.toTypedArray()))
            it.executeQuery().toList { toPBrev() }
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

    private fun Connection.lagreBehandling(
        soknadId: UUID,
        behandling: Behandling,
    ) {
        val pBehandling =
            prepareStatement(CREATE_BEHANDLING).use {
                it.setObject(1, behandling.behandlingId)
                it.setString(2, behandling.utfall.dbValue())
                it.setString(3, behandling.behandletAv.value)
                it.setObject(4, behandling.behandletTidspunkt)
                it.setString(5, behandling.begrunnelse)
                it.setString(6, behandling.utfall.ikkeAktuellGrunnDbValue())
                it.setObject(7, soknadId)
                it.executeQuery().toList { toPBehandling() }.singleOrNull()
                    ?: throw IllegalArgumentException("Fant ikke søknad med id $soknadId")
            }
        createBehandlingPerioder(pBehandling.id, behandling.innvilgedePerioder)
        behandling.brev?.let { createBrev(pBehandling.id, it) }
    }

    private fun Connection.createBehandlingPerioder(
        behandlingId: Int,
        behandlingPerioder: List<Periode>,
    ) {
        behandlingPerioder.forEach { periode ->
            prepareStatement(CREATE_BEHANDLING_PERIODE).use {
                it.setInt(1, behandlingId)
                it.setDate(2, Date.valueOf(periode.fom))
                it.setDate(3, Date.valueOf(periode.tom))
                it.executeUpdate()
            }
        }
    }

    private fun Connection.createBrev(
        behandlingId: Int,
        brev: Brev,
    ) {
        val documentJson =
            PGobject().apply {
                type = "jsonb"
                value = brev.document.serializeToJson()
            }
        prepareStatement(CREATE_BREV).use {
            it.setObject(1, brev.brevId)
            it.setInt(2, behandlingId)
            it.setString(3, brev.brevtype.dbValue())
            it.setObject(4, documentJson)
            it.setString(5, brev.journalpostId?.value)
            it.setObject(6, brev.journalfortTidspunkt)
            it.setObject(7, brev.distribuertTidspunkt)
            it.executeUpdate()
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

        private const val GET_BEHANDLINGER =
            """
                SELECT * FROM behandling WHERE soknad_id = ANY(?)
            """

        private const val GET_IKKE_JOURNALFORTE_SOKNADER =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN behandling b ON b.soknad_id = s.id
                    INNER JOIN brev br ON br.behandling_id = b.id
                WHERE br.journalpost_id IS NULL AND b.behandlet_tidspunkt < ?
            """

        private const val GET_IKKE_DISTRIBUERTE_SOKNADER =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN behandling b ON b.soknad_id = s.id
                    INNER JOIN brev br ON br.behandling_id = b.id
                WHERE br.journalpost_id IS NOT NULL AND br.distribuert_tidspunkt IS NULL
                    AND b.behandlet_tidspunkt < ?
            """

        private const val GET_UNPUBLISHED_SOKNADER =
            """
                SELECT * FROM soknad WHERE soknad_published_at IS NULL
            """

        private const val GET_SOKNADER_MED_UNPUBLISHED_BEHANDLING =
            """
                SELECT DISTINCT s.* FROM soknad s
                    INNER JOIN behandling b ON b.soknad_id = s.id
                WHERE b.behandling_published_at IS NULL AND s.soknad_published_at IS NOT NULL
            """

        private const val SET_BREV_JOURNALFORT =
            """
                UPDATE brev
                SET journalpost_id = ?, journalfort_tidspunkt = ?
                WHERE uuid = ?
            """

        private const val SET_BREV_DISTRIBUERT =
            """
                UPDATE brev
                SET distribuert_tidspunkt = ?
                WHERE uuid = ?
            """

        private const val SET_SOKNAD_PUBLISHED_AT =
            """
                UPDATE soknad
                SET soknad_published_at = ?
                WHERE uuid = ?
            """

        private const val SET_BEHANDLING_PUBLISHED_AT =
            """
                UPDATE behandling
                SET behandling_published_at = ?
                WHERE uuid = ?
            """

        private const val GET_BEHANDLING_PERIODER =
            """
                SELECT * FROM vedtak_periode WHERE behandling_id = ANY(?) ORDER BY fom ASC
            """

        private const val GET_BREV =
            """
                SELECT * FROM brev WHERE behandling_id = ANY(?)
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

        private const val CREATE_BEHANDLING =
            """
                INSERT INTO behandling (
                    uuid,
                    soknad_id,
                    utfall,
                    behandlet_av,
                    behandlet_tidspunkt,
                    begrunnelse,
                    ikke_aktuell_grunn
                )
                SELECT ?, s.id, ?, ?, ?, ?, ?
                FROM soknad s
                WHERE s.uuid = ?
                RETURNING *
            """

        private const val CREATE_BEHANDLING_PERIODE =
            """
                INSERT INTO vedtak_periode (
                    behandling_id,
                    fom,
                    tom
                ) VALUES (?, ?, ?)
            """

        private const val CREATE_BREV =
            """
                INSERT INTO brev (
                    uuid,
                    behandling_id,
                    brevtype,
                    document,
                    journalpost_id,
                    journalfort_tidspunkt,
                    distribuert_tidspunkt
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
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

internal fun ResultSet.toPBehandlingPeriode(): PBehandlingPeriode =
    PBehandlingPeriode(
        id = getInt("id"),
        behandlingId = getInt("behandling_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
    )

internal fun ResultSet.toPBehandling(): PBehandling =
    PBehandling(
        id = getInt("id"),
        uuid = getObject("uuid", UUID::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        soknadId = getInt("soknad_id"),
        utfall = getString("utfall"),
        behandletAv = getString("behandlet_av"),
        behandletTidspunkt = getObject("behandlet_tidspunkt", OffsetDateTime::class.java),
        begrunnelse = getString("begrunnelse"),
        ikkeAktuellGrunn = getString("ikke_aktuell_grunn"),
    )

internal fun ResultSet.toPBrev(): PBrev =
    PBrev(
        id = getInt("id"),
        uuid = getObject("uuid", UUID::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        behandlingId = getInt("behandling_id"),
        brevtype = getString("brevtype"),
        document = getString("document"),
        journalpostId = getString("journalpost_id"),
        journalfortTidspunkt = getObject("journalfort_tidspunkt", OffsetDateTime::class.java),
        distribuertTidspunkt = getObject("distribuert_tidspunkt", OffsetDateTime::class.java),
    )
