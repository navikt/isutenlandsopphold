package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
import no.nav.syfo.utenlandsopphold.infrastructure.database.JdbcTransactionManager
import no.nav.syfo.utenlandsopphold.infrastructure.database.TestDatabase
import no.nav.syfo.utenlandsopphold.infrastructure.database.dropData
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import org.postgresql.util.PSQLException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoknadRepositoryTest {
    private val database = TestDatabase()
    private val repository = SoknadRepository(database = database)
    private val transactionManager = JdbcTransactionManager(database = database)

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
    fun `hopper over soknad som allerede er lagret med samme eksternId`() {
        val eksternId = UUID.randomUUID()
        val opprinneligePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val opprinneligInnsendtTidspunkt = OffsetDateTime.parse("2026-03-01T09:00:00Z")
        val forsteResultat =
            repository.lagreMottattSoknad(
                soknad(
                    eksternId = eksternId,
                    personident = personident,
                    soktePerioder = opprinneligePerioder,
                    innsendtTidspunkt = opprinneligInnsendtTidspunkt,
                ),
            )
        val duplikat =
            soknad(
                eksternId = eksternId,
                personident = Personident("22222222222"),
                soktePerioder = listOf(Periode(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10))),
                innsendtTidspunkt = OffsetDateTime.parse("2026-04-01T09:00:00Z"),
            )

        val duplikatResultat = repository.lagreMottattSoknad(duplikat)

        assertEquals(LagreMottattSoknadResultat.LAGRET, forsteResultat)
        assertEquals(LagreMottattSoknadResultat.ALLEREDE_LAGRET, duplikatResultat)
        assertTrue(repository.hentSoknader(Personident("22222222222")).isEmpty())
        val hentet = repository.hentSoknader(personident)
        assertEquals(1, hentet.size)
        val lagret = hentet.single()
        assertEquals(eksternId, lagret.eksternId)
        assertEquals(personident, lagret.personident)
        assertEquals(opprinneligePerioder, lagret.soktePerioder)
        assertEquals(opprinneligInnsendtTidspunkt, lagret.innsendtTidspunkt)
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

    @Test
    fun `lagreVedtak lagrer vedtak og oppdaterer status til INNVILGET`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)

        val vedtak = generateVedtak(innvilgedePerioder = soktePerioder)
        val oppdatertSoknad =
            transactionManager.inTransaction { transaction ->
                val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
                repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = vedtak))
            }

        assertEquals(SoknadStatus.INNVILGET, oppdatertSoknad.status)
        assertEquals(Utfall.Innvilget, oppdatertSoknad.vedtak?.utfall)
        assertEquals(vedtak.fattetAv, oppdatertSoknad.vedtak?.fattetAv)
        assertEquals(vedtak.fattetTidspunkt, oppdatertSoknad.vedtak?.fattetTidspunkt)
        assertEquals(soktePerioder, oppdatertSoknad.vedtak?.innvilgedePerioder)
        assertEquals(vedtak.document, oppdatertSoknad.vedtak?.document)
    }

    @Test
    fun `lagreVedtak persisteres og hentes på nytt via hentSoknader`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val vedtak = generateVedtak(innvilgedePerioder = soktePerioder)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.INNVILGET, hentetPaNytt.status)
        assertEquals(soktePerioder, hentetPaNytt.vedtak?.innvilgedePerioder)
        assertEquals(vedtak.vedtakId, hentetPaNytt.vedtak?.vedtakId)
    }

    @Test
    fun `lagreVedtak kan kun lagre ett vedtak per soknad`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = generateVedtak()))
        }

        assertFailsWith<PSQLException> {
            transactionManager.inTransaction { transaction ->
                val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
                repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = generateVedtak()))
            }
        }
    }

    @Test
    fun `lagreVedtak persisterer og henter delvis innvilget vedtak`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 5)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val vedtak =
            generateVedtak(
                utfall = Utfall.DelvisInnvilget(innvilgedePerioder),
                innvilgedePerioder = innvilgedePerioder,
                begrunnelse = "Delvis innvilget begrunnelse",
            )
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.DELVIS_INNVILGET, hentetPaNytt.status)
        assertEquals(Utfall.DelvisInnvilget(innvilgedePerioder), hentetPaNytt.vedtak?.utfall)
        assertEquals(innvilgedePerioder, hentetPaNytt.vedtak?.innvilgedePerioder)
        assertEquals("Delvis innvilget begrunnelse", hentetPaNytt.vedtak?.begrunnelse)
    }

    @Test
    fun `lagreVedtak persisterer og henter avslag uten innvilgede perioder`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        val vedtak =
            generateVedtak(
                utfall = Utfall.Avslag,
                innvilgedePerioder = emptyList(),
                begrunnelse = "Avslag begrunnelse",
            )
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.AVSLAG, hentetPaNytt.status)
        assertEquals(Utfall.Avslag, hentetPaNytt.vedtak?.utfall)
        assertEquals(emptyList(), hentetPaNytt.vedtak?.innvilgedePerioder)
        assertEquals("Avslag begrunnelse", hentetPaNytt.vedtak?.begrunnelse)
    }

    @Test
    fun `lagreVedtak persisterer null begrunnelse for innvilget vedtak`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val vedtak = generateVedtak(innvilgedePerioder = soktePerioder)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreVedtak(transaction, lagretSoknad.copy(vedtak = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.INNVILGET, hentetPaNytt.status)
        assertNull(hentetPaNytt.vedtak?.begrunnelse)
    }

    @Test
    fun `lagreVedtak for ukjent soknadId kaster IllegalArgumentException`() {
        val ukjentSoknadId = UUID.randomUUID()
        assertFailsWith<IllegalArgumentException> {
            transactionManager.inTransaction { transaction ->
                repository.lagreVedtak(transaction, soknad().copy(id = ukjentSoknadId, vedtak = generateVedtak()))
            }
        }
    }

    private fun soknad(
        eksternId: UUID = UUID.randomUUID(),
        personident: Personident = this.personident,
        soktePerioder: List<Periode> = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10))),
        innsendtTidspunkt: OffsetDateTime = OffsetDateTime.parse("2026-03-01T09:00:00Z"),
    ): Soknad =
        Soknad(
            id = UUID.randomUUID(),
            eksternId = eksternId,
            personident = personident,
            soktePerioder = soktePerioder,
            innsendtTidspunkt = innsendtTidspunkt,
        )

    @Test
    fun `getIkkeJournalforteSoknader returnerer kun soknader med u-journalfort vedtak`() {
        opprettSoknadMedVedtak(journalpostId = null)
        opprettSoknadMedVedtak(journalpostId = "111")

        val ikkeJournalforte = repository.getIkkeJournalforteSoknader(fattetBefore = etterAlleTestVedtak)

        assertEquals(1, ikkeJournalforte.size)
        assertTrue(ikkeJournalforte.single().vedtak?.erJournalfort == false)
    }

    @Test
    fun `getIkkeJournalforteSoknader ekskluderer vedtak fattet etter fattetBefore`() {
        opprettSoknadMedVedtak(journalpostId = null, fattetTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"))
        opprettSoknadMedVedtak(journalpostId = null, fattetTidspunkt = OffsetDateTime.parse("2026-01-12T12:00:00Z"))

        val ikkeJournalforte = repository.getIkkeJournalforteSoknader(fattetBefore = OffsetDateTime.parse("2026-01-11T00:00:00Z"))

        assertEquals(1, ikkeJournalforte.size)
        assertEquals(OffsetDateTime.parse("2026-01-10T12:00:00Z"), ikkeJournalforte.single().vedtak?.fattetTidspunkt)
    }

    @Test
    fun `getIkkeJournalforteSoknader leser document og perioder`() {
        val vedtakId = opprettSoknadMedVedtak(journalpostId = null)

        val soknad = repository.getIkkeJournalforteSoknader(fattetBefore = etterAlleTestVedtak).single()

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
        assertEquals(soknad.soktePerioder, soknad.vedtak?.innvilgedePerioder)
    }

    @Test
    fun `setVedtakJournalfort oppdaterer journalpost_id og journalfort_tidspunkt`() {
        val vedtakId = opprettSoknadMedVedtak(journalpostId = null)
        val journalpostId = JournalpostId("999")
        val journalfortTidspunkt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        repository.setVedtakJournalfort(vedtakId, journalpostId, journalfortTidspunkt)

        assertTrue(repository.getIkkeJournalforteSoknader(fattetBefore = etterAlleTestVedtak).isEmpty())
    }

    @Test
    fun `getSoknaderMedIkkeDistribuerteVedtak returnerer kun journalforte, ikke-distribuerte vedtak`() {
        opprettSoknadMedVedtak(journalpostId = null)
        opprettSoknadMedVedtak(journalpostId = "111", distribuertTidspunkt = null)
        opprettSoknadMedVedtak(journalpostId = "222", distribuertTidspunkt = OffsetDateTime.now())

        val ikkeDistribuerte = repository.getSoknaderMedIkkeDistribuerteVedtak(fattetBefore = etterAlleTestVedtak)

        assertEquals(1, ikkeDistribuerte.size)
        val vedtak = ikkeDistribuerte.single().vedtak
        assertEquals(true, vedtak?.erJournalfort)
        assertEquals(false, vedtak?.erDistribuert)
    }

    @Test
    fun `getSoknaderMedIkkeDistribuerteVedtak ekskluderer vedtak fattet etter fattetBefore (grace)`() {
        opprettSoknadMedVedtak(
            journalpostId = "111",
            distribuertTidspunkt = null,
            fattetTidspunkt = OffsetDateTime.parse("2026-05-01T12:00:00Z"),
        )

        val foerFattetTidspunkt = OffsetDateTime.parse("2026-04-01T00:00:00Z")

        assertTrue(repository.getSoknaderMedIkkeDistribuerteVedtak(fattetBefore = foerFattetTidspunkt).isEmpty())
        assertEquals(1, repository.getSoknaderMedIkkeDistribuerteVedtak(fattetBefore = etterAlleTestVedtak).size)
    }

    @Test
    fun `setVedtakDistribuert oppdaterer distribuert_tidspunkt`() {
        val vedtakId = opprettSoknadMedVedtak(journalpostId = "111", distribuertTidspunkt = null)
        val distribuertTidspunkt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        repository.setVedtakDistribuert(vedtakId, distribuertTidspunkt)

        assertTrue(repository.getSoknaderMedIkkeDistribuerteVedtak(fattetBefore = etterAlleTestVedtak).isEmpty())
    }

    @Test
    fun `getUnpublishedSoknader returnerer kun soknader uten soknad_publisert_at`() {
        opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = null)
        opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = OffsetDateTime.now())

        val upubliserte = repository.getUnpublishedSoknader()

        assertEquals(1, upubliserte.size)
    }

    @Test
    fun `setSoknadPublished oppdaterer soknad_publisert_at`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        assertEquals(1, repository.getUnpublishedSoknader().size)

        repository.setSoknadPublished(soknad.id, OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))

        assertTrue(repository.getUnpublishedSoknader().isEmpty())
    }

    @Test
    fun `getSoknaderMedUnpublishedVedtak returnerer kun soknader med publisert soknad og upublisert vedtak`() {
        opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = null, vedtakPublishedAt = null)
        opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = OffsetDateTime.now(), vedtakPublishedAt = null)
        opprettSoknadMedVedtak(
            journalpostId = null,
            soknadPublishedAt = OffsetDateTime.now(),
            vedtakPublishedAt = OffsetDateTime.now(),
        )

        val soknaderMedUnpublishedVedtak = repository.getSoknaderMedUnpublishedVedtak()

        assertEquals(1, soknaderMedUnpublishedVedtak.size)
    }

    @Test
    fun `setVedtakPublished oppdaterer vedtak_published_at`() {
        val vedtakId =
            opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = OffsetDateTime.now(), vedtakPublishedAt = null)
        assertEquals(1, repository.getSoknaderMedUnpublishedVedtak().size)

        repository.setVedtakPublished(vedtakId, OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))

        assertTrue(repository.getSoknaderMedUnpublishedVedtak().isEmpty())
    }

    private fun opprettSoknadMedVedtak(
        journalpostId: String?,
        distribuertTidspunkt: OffsetDateTime? = null,
        fattetTidspunkt: OffsetDateTime = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
        soknadPublishedAt: OffsetDateTime? = null,
        vedtakPublishedAt: OffsetDateTime? = null,
    ): UUID {
        val soknadUuid = UUID.randomUUID()
        val vedtakUuid = UUID.randomUUID()

        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO SOKNAD (uuid, ekstern_id, personident, innsendt_tidspunkt, soknad_published_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                ).use { statement ->
                    statement.setObject(1, soknadUuid)
                    statement.setObject(2, UUID.randomUUID())
                    statement.setString(3, personident.value)
                    statement.setObject(4, OffsetDateTime.parse("2026-01-02T08:00:00Z"))
                    statement.setObject(5, soknadPublishedAt)
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
                            journalfort_tidspunkt,
                            distribuert_tidspunkt,
                            vedtak_published_at
                        )
                        VALUES (?, ?, 'INNVILGET', 'Z990000', ?, ?::jsonb, ?, ?, ?, ?)
                        RETURNING id
                        """,
                    ).use { statement ->
                        statement.setObject(1, vedtakUuid)
                        statement.setInt(2, soknadId)
                        statement.setObject(3, fattetTidspunkt)
                        statement.setString(4, DOCUMENT_JSON)
                        statement.setString(5, journalpostId)
                        statement.setObject(6, journalpostId?.let { OffsetDateTime.now() })
                        statement.setObject(7, distribuertTidspunkt)
                        statement.setObject(8, vedtakPublishedAt)
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

        // Cutoff langt etter alle fattet_tidspunkt brukt i disse testene, slik at
        // getIkkeJournalforteSoknader/getSoknaderMedIkkeDistribuerteVedtak sine
        // eksisterende asserts ikke påvirkes av grace-vinduet.
        private val etterAlleTestVedtak = OffsetDateTime.parse("2026-06-01T00:00:00Z")
    }

    private fun generateVedtak(
        utfall: Utfall = Utfall.Innvilget,
        innvilgedePerioder: List<Periode> =
            listOf(
                Periode(
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 4, 10),
                ),
            ),
        begrunnelse: String? = null,
    ): Vedtak =
        Vedtak(
            utfall = utfall,
            fattetAv = Navident("Z999999"),
            fattetTidspunkt = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
            innvilgedePerioder = innvilgedePerioder,
            begrunnelse = begrunnelse,
            document =
                listOf(
                    DocumentComponent(
                        type = DocumentComponentType.HEADER_H1,
                        title = "Vedtak",
                        texts = listOf("Søknaden din er innvilget"),
                    ),
                ),
        )
}
