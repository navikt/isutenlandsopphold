package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.Henleggelse
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Utsending
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
        assertNull(lagret.behandlingsutfall)
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
    fun `lagreBehandlingsutfall lagrer vedtak og oppdaterer status til INNVILGET`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)

        val vedtak = generateVedtak(innvilgedePerioder = soktePerioder)
        val oppdatertSoknad =
            transactionManager.inTransaction { transaction ->
                val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
                repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = vedtak))
            }

        assertEquals(SoknadStatus.INNVILGET, oppdatertSoknad.status)
        val lagretVedtak = oppdatertSoknad.behandlingsutfall as? Vedtak
        assertEquals(Utfall.Innvilget, lagretVedtak?.utfall)
        assertEquals(vedtak.fattetAv, lagretVedtak?.fattetAv)
        assertEquals(vedtak.fattetTidspunkt, lagretVedtak?.fattetTidspunkt)
        assertEquals(soktePerioder, lagretVedtak?.innvilgedePerioder)
        assertEquals(vedtak.document, lagretVedtak?.document)
    }

    @Test
    fun `lagreBehandlingsutfall persisteres og hentes på nytt via hentSoknader`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val vedtak = generateVedtak(innvilgedePerioder = soktePerioder)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.INNVILGET, hentetPaNytt.status)
        val lagretVedtak = hentetPaNytt.behandlingsutfall as? Vedtak
        assertEquals(soktePerioder, lagretVedtak?.innvilgedePerioder)
        assertEquals(vedtak.behandlingsutfallId, lagretVedtak?.behandlingsutfallId)
    }

    @Test
    fun `lagreBehandlingsutfall kan kun lagre ett behandlingsutfall per soknad`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = generateVedtak()))
        }

        assertFailsWith<PSQLException> {
            transactionManager.inTransaction { transaction ->
                val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
                repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = generateVedtak()))
            }
        }
    }

    @Test
    fun `lagreBehandlingsutfall persisterer og henter delvis innvilget vedtak`() {
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
            repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.DELVIS_INNVILGET, hentetPaNytt.status)
        val lagretVedtak = hentetPaNytt.behandlingsutfall as? Vedtak
        assertEquals(Utfall.DelvisInnvilget(innvilgedePerioder), lagretVedtak?.utfall)
        assertEquals(innvilgedePerioder, lagretVedtak?.innvilgedePerioder)
        assertEquals("Delvis innvilget begrunnelse", lagretVedtak?.begrunnelse)
    }

    @Test
    fun `lagreBehandlingsutfall persisterer og henter avslag uten innvilgede perioder`() {
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
            repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.AVSLAG, hentetPaNytt.status)
        val lagretVedtak = hentetPaNytt.behandlingsutfall as? Vedtak
        assertEquals(Utfall.Avslag, lagretVedtak?.utfall)
        assertEquals(emptyList(), lagretVedtak?.innvilgedePerioder)
        assertEquals("Avslag begrunnelse", lagretVedtak?.begrunnelse)
    }

    @Test
    fun `lagreBehandlingsutfall persisterer null begrunnelse for innvilget vedtak`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val vedtak = generateVedtak(innvilgedePerioder = soktePerioder)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = vedtak))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.INNVILGET, hentetPaNytt.status)
        assertNull(hentetPaNytt.behandlingsutfall?.begrunnelse)
    }

    @Test
    fun `lagreBehandlingsutfall for ukjent soknadId kaster IllegalArgumentException`() {
        val ukjentSoknadId = UUID.randomUUID()
        assertFailsWith<IllegalArgumentException> {
            transactionManager.inTransaction { transaction ->
                repository.lagreBehandlingsutfall(
                    transaction,
                    soknad().copy(id = ukjentSoknadId, behandlingsutfall = generateVedtak()),
                )
            }
        }
    }

    @Test
    fun `lagreBehandlingsutfall persisterer og henter henleggelse`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        val henleggelse = generateHenleggelse()
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandlingsutfall(transaction, lagretSoknad.copy(behandlingsutfall = henleggelse))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.HENLAGT, hentetPaNytt.status)
        val lagretHenleggelse = hentetPaNytt.behandlingsutfall as? Henleggelse
        assertEquals(henleggelse.begrunnelse, lagretHenleggelse?.begrunnelse)
        assertEquals(henleggelse.fattetAv, lagretHenleggelse?.fattetAv)
        assertEquals(henleggelse.document, lagretHenleggelse?.document)
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
        assertTrue(ikkeJournalforte.single().behandlingsutfall?.erJournalfort == false)
    }

    @Test
    fun `getIkkeJournalforteSoknader ekskluderer vedtak fattet etter fattetBefore`() {
        opprettSoknadMedVedtak(journalpostId = null, fattetTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"))
        opprettSoknadMedVedtak(journalpostId = null, fattetTidspunkt = OffsetDateTime.parse("2026-01-12T12:00:00Z"))

        val ikkeJournalforte = repository.getIkkeJournalforteSoknader(fattetBefore = OffsetDateTime.parse("2026-01-11T00:00:00Z"))

        assertEquals(1, ikkeJournalforte.size)
        assertEquals(OffsetDateTime.parse("2026-01-10T12:00:00Z"), ikkeJournalforte.single().behandlingsutfall?.fattetTidspunkt)
    }

    @Test
    fun `getIkkeJournalforteSoknader leser document og perioder`() {
        val behandlingsutfallId = opprettSoknadMedVedtak(journalpostId = null)

        val soknad = repository.getIkkeJournalforteSoknader(fattetBefore = etterAlleTestVedtak).single()

        assertEquals(behandlingsutfallId, soknad.behandlingsutfall?.behandlingsutfallId)
        assertEquals(1, soknad.behandlingsutfall?.document?.size)
        assertEquals(
            "Tittel",
            soknad.behandlingsutfall
                ?.document
                ?.first()
                ?.title,
        )
        assertEquals(1, soknad.soktePerioder.size)
        assertEquals(soknad.soktePerioder, (soknad.behandlingsutfall as? Vedtak)?.innvilgedePerioder)
    }

    @Test
    fun `setBehandlingsutfallJournalfort oppdaterer journalpost_id og journalfort_tidspunkt`() {
        val behandlingsutfallId = opprettSoknadMedVedtak(journalpostId = null)
        val journalpostId = JournalpostId("999")
        val journalfortTidspunkt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        repository.setBehandlingsutfallJournalfort(behandlingsutfallId, journalpostId, journalfortTidspunkt)

        assertTrue(repository.getIkkeJournalforteSoknader(fattetBefore = etterAlleTestVedtak).isEmpty())
    }

    @Test
    fun `getSoknaderMedIkkeDistribuerteBehandlingsutfall returnerer kun journalforte, ikke-distribuerte vedtak`() {
        opprettSoknadMedVedtak(journalpostId = null)
        opprettSoknadMedVedtak(journalpostId = "111", distribuertTidspunkt = null)
        opprettSoknadMedVedtak(journalpostId = "222", distribuertTidspunkt = OffsetDateTime.now())

        val ikkeDistribuerte = repository.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore = etterAlleTestVedtak)

        assertEquals(1, ikkeDistribuerte.size)
        val behandlingsutfall = ikkeDistribuerte.single().behandlingsutfall
        assertEquals(true, behandlingsutfall?.erJournalfort)
        assertEquals(false, behandlingsutfall?.erDistribuert)
    }

    @Test
    fun `getSoknaderMedIkkeDistribuerteBehandlingsutfall ekskluderer vedtak fattet etter fattetBefore (grace)`() {
        opprettSoknadMedVedtak(
            journalpostId = "111",
            distribuertTidspunkt = null,
            fattetTidspunkt = OffsetDateTime.parse("2026-05-01T12:00:00Z"),
        )

        val foerFattetTidspunkt = OffsetDateTime.parse("2026-04-01T00:00:00Z")

        assertTrue(repository.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore = foerFattetTidspunkt).isEmpty())
        assertEquals(1, repository.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore = etterAlleTestVedtak).size)
    }

    @Test
    fun `setBehandlingsutfallDistribuert oppdaterer distribuert_tidspunkt`() {
        val behandlingsutfallId = opprettSoknadMedVedtak(journalpostId = "111", distribuertTidspunkt = null)
        val distribuertTidspunkt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        repository.setBehandlingsutfallDistribuert(behandlingsutfallId, distribuertTidspunkt)

        assertTrue(repository.getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore = etterAlleTestVedtak).isEmpty())
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
    fun `getSoknaderMedUnpublishedBehandlingsutfall returnerer kun soknader med publisert soknad og upublisert vedtak`() {
        opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = null, vedtakPublishedAt = null)
        opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = OffsetDateTime.now(), vedtakPublishedAt = null)
        opprettSoknadMedVedtak(
            journalpostId = null,
            soknadPublishedAt = OffsetDateTime.now(),
            vedtakPublishedAt = OffsetDateTime.now(),
        )

        val soknaderMedUnpublishedBehandlingsutfall = repository.getSoknaderMedUnpublishedBehandlingsutfall()

        assertEquals(1, soknaderMedUnpublishedBehandlingsutfall.size)
    }

    @Test
    fun `setBehandlingsutfallPublished oppdaterer vedtak_published_at`() {
        val behandlingsutfallId =
            opprettSoknadMedVedtak(journalpostId = null, soknadPublishedAt = OffsetDateTime.now(), vedtakPublishedAt = null)
        assertEquals(1, repository.getSoknaderMedUnpublishedBehandlingsutfall().size)

        repository.setBehandlingsutfallPublished(behandlingsutfallId, OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))

        assertTrue(repository.getSoknaderMedUnpublishedBehandlingsutfall().isEmpty())
    }

    private fun opprettSoknadMedVedtak(
        journalpostId: String?,
        distribuertTidspunkt: OffsetDateTime? = null,
        fattetTidspunkt: OffsetDateTime = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
        soknadPublishedAt: OffsetDateTime? = null,
        vedtakPublishedAt: OffsetDateTime? = null,
    ): UUID {
        val soknadUuid = UUID.randomUUID()
        val behandlingsutfallUuid = UUID.randomUUID()

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

            val behandlingsutfallId =
                connection
                    .prepareStatement(
                        """
                        INSERT INTO BEHANDLINGSUTFALL (
                            uuid,
                            soknad_id,
                            type,
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
                        statement.setObject(1, behandlingsutfallUuid)
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
                    statement.setInt(1, behandlingsutfallId)
                    statement.setObject(2, LocalDate.of(2026, 1, 5))
                    statement.setObject(3, LocalDate.of(2026, 1, 9))
                    statement.executeUpdate()
                }

            connection.commit()
        }

        return behandlingsutfallUuid
    }

    companion object {
        private const val DOCUMENT_JSON =
            """[{"type": "PARAGRAPH", "title": "Tittel", "texts": ["Innhold"]}]"""

        // Cutoff langt etter alle fattet_tidspunkt brukt i disse testene, slik at
        // getIkkeJournalforteSoknader/getSoknaderMedIkkeDistribuerteBehandlingsutfall sine
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
            utsending =
                Utsending(
                    document =
                        listOf(
                            DocumentComponent(
                                type = DocumentComponentType.HEADER_H1,
                                title = "Vedtak",
                                texts = listOf("Søknaden din er innvilget"),
                            ),
                        ),
                ),
        )

    private fun generateHenleggelse(): Henleggelse =
        Henleggelse(
            fattetAv = Navident("Z999999"),
            fattetTidspunkt = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
            begrunnelse = "Trukket av søker",
            utsending =
                Utsending(
                    document =
                        listOf(
                            DocumentComponent(
                                type = DocumentComponentType.HEADER_H1,
                                title = "Henleggelse",
                                texts = listOf("Søknaden din er henlagt"),
                            ),
                        ),
                ),
        )
}
