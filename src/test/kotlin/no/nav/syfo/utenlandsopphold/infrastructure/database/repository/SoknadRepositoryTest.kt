package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.LagreMottattSoknadResultat
import no.nav.syfo.utenlandsopphold.domain.Behandling
import no.nav.syfo.utenlandsopphold.domain.BehandlingsUtfall
import no.nav.syfo.utenlandsopphold.domain.Brev
import no.nav.syfo.utenlandsopphold.domain.Brevtype
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.DocumentComponentType
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellGrunn
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.domain.brevtype
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
    fun `lagrer og henter ubehandlet soknad`() {
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
        assertNull(lagret.behandling)
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
    fun `lagreBehandling lagrer behandling og oppdaterer status til INNVILGET`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)

        val behandling = generateBehandling(utfall = BehandlingsUtfall.Innvilget(soktePerioder))
        val oppdatertSoknad =
            transactionManager.inTransaction { transaction ->
                val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
                repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = behandling))
            }

        assertEquals(SoknadStatus.INNVILGET, oppdatertSoknad.status)
        assertEquals(BehandlingsUtfall.Innvilget(soktePerioder), oppdatertSoknad.behandling?.utfall)
        assertEquals(behandling.behandletAv, oppdatertSoknad.behandling?.behandletAv)
        assertEquals(behandling.behandletTidspunkt, oppdatertSoknad.behandling?.behandletTidspunkt)
        assertEquals(behandling.brev!!.document, oppdatertSoknad.behandling?.brev?.document)
    }

    @Test
    fun `lagreBehandling persisteres og hentes på nytt via hentSoknader`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val behandling = generateBehandling(utfall = BehandlingsUtfall.Innvilget(soktePerioder))
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = behandling))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.INNVILGET, hentetPaNytt.status)
        assertEquals(BehandlingsUtfall.Innvilget(soktePerioder), hentetPaNytt.behandling?.utfall)
        assertEquals(behandling.behandlingId, hentetPaNytt.behandling?.behandlingId)
    }

    @Test
    fun `lagreBehandling kan kun lagre én behandling per soknad`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        val forsteBehandling = generateBehandling()
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = forsteBehandling))
        }

        assertFailsWith<PSQLException> {
            transactionManager.inTransaction { transaction ->
                val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
                repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = generateBehandling()))
            }
        }

        // Behandling, perioder og brev lagres i samme transaksjon, så det mislykkede forsøket
        // skal ikke ha lagt igjen et foreldreløst brev.
        val hentetPaNytt = repository.hentSoknader(personident).single()
        assertEquals(forsteBehandling.behandlingId, hentetPaNytt.behandling?.behandlingId)
        assertEquals(forsteBehandling.brev!!.brevId, hentetPaNytt.behandling?.brev?.brevId)
        assertEquals(1, countRows("BREV"))
        assertEquals(1, countRows("BEHANDLING"))
    }

    private fun countRows(table: String): Int =
        database.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM $table").use { statement ->
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    /**
     * Leses direkte fra kolonnen, ikke gjennom domenet. Utfall uten begrunnelse har
     * ingen plass å bære en verdi, så en round-trip kan ikke avsløre at skrivveien
     * likevel la noe i kolonnen.
     */
    private fun lagretBegrunnelse(): String? =
        database.connection.use { connection ->
            connection.prepareStatement("SELECT begrunnelse FROM behandling").use { statement ->
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getString(1)
                }
            }
        }

    @Test
    fun `lagreBehandling persisterer og henter delvis innvilget behandling`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val innvilgedePerioder = listOf(Periode(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 5)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val behandling =
            generateBehandling(
                utfall = BehandlingsUtfall.DelvisInnvilget(innvilgedePerioder, "Delvis innvilget begrunnelse"),
            )
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = behandling))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.DELVIS_INNVILGET, hentetPaNytt.status)
        assertEquals(BehandlingsUtfall.DelvisInnvilget(innvilgedePerioder, "Delvis innvilget begrunnelse"), hentetPaNytt.behandling?.utfall)
    }

    @Test
    fun `lagreBehandling persisterer og henter avslag uten innvilgede perioder`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        val behandling =
            generateBehandling(
                utfall = BehandlingsUtfall.Avslag("Avslag begrunnelse"),
            )
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = behandling))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.AVSLAG, hentetPaNytt.status)
        assertEquals(BehandlingsUtfall.Avslag("Avslag begrunnelse"), hentetPaNytt.behandling?.utfall)
    }

    @Test
    fun `lagreBehandling persisterer og henter henleggelse uten innvilgede perioder`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        val behandling =
            generateBehandling(
                utfall = BehandlingsUtfall.Henlagt("Søker har trukket søknaden"),
            )
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = behandling))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.HENLAGT, hentetPaNytt.status)
        assertEquals(BehandlingsUtfall.Henlagt("Søker har trukket søknaden"), hentetPaNytt.behandling?.utfall)
    }

    @Test
    fun `lagreBehandling persisterer null begrunnelse for innvilget behandling`() {
        val soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
        val soknad = soknad(soktePerioder = soktePerioder)
        repository.lagreMottattSoknad(soknad)
        val behandling = generateBehandling(utfall = BehandlingsUtfall.Innvilget(soktePerioder))
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(transaction, lagretSoknad.copy(behandling = behandling))
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.INNVILGET, hentetPaNytt.status)
        assertNull(lagretBegrunnelse())
    }

    @Test
    fun `lagreBehandling persisterer og henter behandling merket ikke aktuell, uten brev`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(
                transaction,
                lagretSoknad.merkIkkeAktuell(
                    grunn = IkkeAktuellGrunn.BEHANDLET_I_INFOTRYGD,
                    behandletAv = Navident("Z999999"),
                    now = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
                ),
            )
        }

        val hentetPaNytt = repository.hentSoknader(personident).single()

        assertEquals(SoknadStatus.IKKE_AKTUELL, hentetPaNytt.status)
        assertEquals(
            BehandlingsUtfall.IkkeAktuell(IkkeAktuellGrunn.BEHANDLET_I_INFOTRYGD),
            hentetPaNytt.behandling?.utfall,
        )
        assertNull(lagretBegrunnelse())
        assertNull(hentetPaNytt.behandling?.brev)
        assertEquals(0, countRows("brev"))
    }

    /**
     * Databasen skal håndheve koblingen mellom utfall og grunn begge veier, ikke bare
     * stole på at domenet gjør det.
     */
    @Test
    fun `databasen avviser ikke_aktuell_grunn på andre utfall, og ikke aktuell uten grunn`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)

        assertFailsWith<PSQLException> {
            insertBehandlingDirekte(soknadEksternId = soknad.eksternId, utfall = "HENLAGT", ikkeAktuellGrunn = "DUPLIKAT")
        }
        assertFailsWith<PSQLException> {
            insertBehandlingDirekte(soknadEksternId = soknad.eksternId, utfall = "IKKE_AKTUELL", ikkeAktuellGrunn = null)
        }
    }

    private fun insertBehandlingDirekte(
        soknadEksternId: UUID,
        utfall: String,
        ikkeAktuellGrunn: String?,
    ) = database.connection.use { connection ->
        connection
            .prepareStatement(
                """
                INSERT INTO behandling (uuid, soknad_id, utfall, behandlet_av, behandlet_tidspunkt, ikke_aktuell_grunn)
                SELECT ?, s.id, ?, 'Z999999', now(), ?
                FROM soknad s WHERE s.ekstern_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setString(2, utfall)
                statement.setString(3, ikkeAktuellGrunn)
                statement.setObject(4, soknadEksternId)
                statement.executeUpdate()
            }
        connection.commit()
    }

    @Test
    fun `getIkkeJournalforteSoknader utelater behandling merket ikke aktuell`() {
        val soknad = soknad()
        repository.lagreMottattSoknad(soknad)
        transactionManager.inTransaction { transaction ->
            val lagretSoknad = repository.hentSoknadForUpdate(transaction, soknad.id)!!
            repository.lagreBehandling(
                transaction,
                lagretSoknad.merkIkkeAktuell(
                    grunn = IkkeAktuellGrunn.ANNET,
                    behandletAv = Navident("Z999999"),
                    now = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
                ),
            )
        }

        assertEquals(emptyList(), repository.getIkkeJournalforteSoknader(behandletBefore = etterAlleTestBehandlinger))
        assertEquals(
            emptyList(),
            repository.getSoknaderMedIkkeDistribuerteBrev(behandletBefore = etterAlleTestBehandlinger),
        )
    }

    @Test
    fun `lagreBehandling for ukjent soknadId kaster IllegalArgumentException`() {
        val ukjentSoknadId = UUID.randomUUID()
        assertFailsWith<IllegalArgumentException> {
            transactionManager.inTransaction { transaction ->
                repository.lagreBehandling(transaction, soknad().copy(id = ukjentSoknadId, behandling = generateBehandling()))
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
    fun `getIkkeJournalforteSoknader returnerer kun soknader med ujournalført brev`() {
        opprettBehandletSoknad(journalpostId = null)
        opprettBehandletSoknad(journalpostId = "111")

        val ikkeJournalforte = repository.getIkkeJournalforteSoknader(behandletBefore = etterAlleTestBehandlinger)

        assertEquals(1, ikkeJournalforte.size)
        assertEquals(
            false,
            ikkeJournalforte
                .single()
                .behandling
                ?.brev
                ?.erJournalfort,
        )
    }

    @Test
    fun `getIkkeJournalforteSoknader ekskluderer behandlinger utført etter behandletBefore`() {
        opprettBehandletSoknad(journalpostId = null, behandletTidspunkt = OffsetDateTime.parse("2026-01-10T12:00:00Z"))
        opprettBehandletSoknad(journalpostId = null, behandletTidspunkt = OffsetDateTime.parse("2026-01-12T12:00:00Z"))

        val ikkeJournalforte =
            repository.getIkkeJournalforteSoknader(behandletBefore = OffsetDateTime.parse("2026-01-11T00:00:00Z"))

        assertEquals(1, ikkeJournalforte.size)
        assertEquals(
            OffsetDateTime.parse("2026-01-10T12:00:00Z"),
            ikkeJournalforte.single().behandling?.behandletTidspunkt,
        )
    }

    @Test
    fun `getIkkeJournalforteSoknader leser brev, document og perioder`() {
        val opprettet = opprettBehandletSoknad(journalpostId = null)

        val soknad = repository.getIkkeJournalforteSoknader(behandletBefore = etterAlleTestBehandlinger).single()

        val behandling = soknad.behandling
        assertEquals(opprettet.behandlingUuid, behandling?.behandlingId)
        assertEquals(opprettet.brevUuid, behandling?.brev?.brevId)
        assertEquals(Brevtype.VEDTAK_INNVILGET, behandling?.brev?.brevtype)
        assertEquals(1, behandling?.brev?.document?.size)
        assertEquals(
            "Tittel",
            behandling
                ?.brev
                ?.document
                ?.first()
                ?.title,
        )
        assertEquals(1, soknad.soktePerioder.size)
        assertEquals(BehandlingsUtfall.Innvilget(soknad.soktePerioder), behandling?.utfall)
    }

    @Test
    fun `setBrevJournalfort oppdaterer journalpost_id og journalfort_tidspunkt`() {
        val opprettet = opprettBehandletSoknad(journalpostId = null)
        val journalpostId = JournalpostId("999")
        val journalfortTidspunkt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        repository.setBrevJournalfort(opprettet.brevUuid, journalpostId, journalfortTidspunkt)

        assertTrue(repository.getIkkeJournalforteSoknader(behandletBefore = etterAlleTestBehandlinger).isEmpty())
        val brev =
            repository
                .getSoknaderMedIkkeDistribuerteBrev(behandletBefore = etterAlleTestBehandlinger)
                .single()
                .behandling
                ?.brev
        assertEquals(journalpostId, brev?.journalpostId)
        // Databasen returnerer tidspunktet i UTC, så sammenlign øyeblikk og ikke offset.
        assertEquals(journalfortTidspunkt.toInstant(), brev?.journalfortTidspunkt?.toInstant())
    }

    @Test
    fun `getSoknaderMedIkkeDistribuerteBrev returnerer kun journalførte, ikke-distribuerte brev`() {
        opprettBehandletSoknad(journalpostId = null)
        opprettBehandletSoknad(journalpostId = "111", distribuertTidspunkt = null)
        opprettBehandletSoknad(journalpostId = "222", distribuertTidspunkt = OffsetDateTime.now())

        val ikkeDistribuerte = repository.getSoknaderMedIkkeDistribuerteBrev(behandletBefore = etterAlleTestBehandlinger)

        assertEquals(1, ikkeDistribuerte.size)
        val brev = ikkeDistribuerte.single().behandling?.brev
        assertEquals(true, brev?.erJournalfort)
        assertEquals(false, brev?.erDistribuert)
    }

    @Test
    fun `getSoknaderMedIkkeDistribuerteBrev ekskluderer behandlinger utført etter behandletBefore (grace)`() {
        opprettBehandletSoknad(
            journalpostId = "111",
            distribuertTidspunkt = null,
            behandletTidspunkt = OffsetDateTime.parse("2026-05-01T12:00:00Z"),
        )

        val foerBehandletTidspunkt = OffsetDateTime.parse("2026-04-01T00:00:00Z")

        assertTrue(repository.getSoknaderMedIkkeDistribuerteBrev(behandletBefore = foerBehandletTidspunkt).isEmpty())
        assertEquals(1, repository.getSoknaderMedIkkeDistribuerteBrev(behandletBefore = etterAlleTestBehandlinger).size)
    }

    @Test
    fun `setBrevDistribuert oppdaterer distribuert_tidspunkt`() {
        val opprettet = opprettBehandletSoknad(journalpostId = "111", distribuertTidspunkt = null)
        val distribuertTidspunkt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        repository.setBrevDistribuert(opprettet.brevUuid, distribuertTidspunkt)

        assertTrue(repository.getSoknaderMedIkkeDistribuerteBrev(behandletBefore = etterAlleTestBehandlinger).isEmpty())
    }

    @Test
    fun `getUnpublishedSoknader returnerer kun soknader uten soknad_publisert_at`() {
        opprettBehandletSoknad(journalpostId = null, soknadPublishedAt = null)
        opprettBehandletSoknad(journalpostId = null, soknadPublishedAt = OffsetDateTime.now())

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
    fun `getSoknaderMedUnpublishedBehandling returnerer kun soknader med publisert soknad og upublisert behandling`() {
        opprettBehandletSoknad(journalpostId = null, soknadPublishedAt = null, behandlingPublishedAt = null)
        opprettBehandletSoknad(journalpostId = null, soknadPublishedAt = OffsetDateTime.now(), behandlingPublishedAt = null)
        opprettBehandletSoknad(
            journalpostId = null,
            soknadPublishedAt = OffsetDateTime.now(),
            behandlingPublishedAt = OffsetDateTime.now(),
        )

        val soknaderMedUpublisertBehandling = repository.getSoknaderMedUnpublishedBehandling()

        assertEquals(1, soknaderMedUpublisertBehandling.size)
    }

    @Test
    fun `setBehandlingPublished oppdaterer behandling_published_at`() {
        val opprettet =
            opprettBehandletSoknad(
                journalpostId = null,
                soknadPublishedAt = OffsetDateTime.now(),
                behandlingPublishedAt = null,
            )
        assertEquals(1, repository.getSoknaderMedUnpublishedBehandling().size)

        repository.setBehandlingPublished(opprettet.behandlingUuid, OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))

        assertTrue(repository.getSoknaderMedUnpublishedBehandling().isEmpty())
    }

    private data class OpprettetBehandling(
        val behandlingUuid: UUID,
        val brevUuid: UUID,
    )

    /**
     * Setter opp en behandlet søknad direkte i databasen, uten å gå via repository-et, slik at
     * testene kan konstruere tilstander (journalført, distribuert, publisert) som repository-et
     * ikke tilbyr i ett kall.
     */
    private fun opprettBehandletSoknad(
        journalpostId: String?,
        distribuertTidspunkt: OffsetDateTime? = null,
        behandletTidspunkt: OffsetDateTime = OffsetDateTime.parse("2026-01-10T12:00:00Z"),
        soknadPublishedAt: OffsetDateTime? = null,
        behandlingPublishedAt: OffsetDateTime? = null,
    ): OpprettetBehandling {
        val soknadUuid = UUID.randomUUID()
        val behandlingUuid = UUID.randomUUID()
        val brevUuid = UUID.randomUUID()

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

            val behandlingId =
                connection
                    .prepareStatement(
                        """
                        INSERT INTO BEHANDLING (
                            uuid,
                            soknad_id,
                            utfall,
                            behandlet_av,
                            behandlet_tidspunkt,
                            behandling_published_at
                        )
                        VALUES (?, ?, 'INNVILGET', 'Z990000', ?, ?)
                        RETURNING id
                        """,
                    ).use { statement ->
                        statement.setObject(1, behandlingUuid)
                        statement.setInt(2, soknadId)
                        statement.setObject(3, behandletTidspunkt)
                        statement.setObject(4, behandlingPublishedAt)
                        statement.executeQuery().use { rs ->
                            rs.next()
                            rs.getInt("id")
                        }
                    }

            connection
                .prepareStatement(
                    "INSERT INTO VEDTAK_PERIODE (behandling_id, fom, tom) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setInt(1, behandlingId)
                    statement.setObject(2, LocalDate.of(2026, 1, 5))
                    statement.setObject(3, LocalDate.of(2026, 1, 9))
                    statement.executeUpdate()
                }

            connection
                .prepareStatement(
                    """
                    INSERT INTO BREV (
                        uuid,
                        behandling_id,
                        brevtype,
                        document,
                        journalpost_id,
                        journalfort_tidspunkt,
                        distribuert_tidspunkt
                    )
                    VALUES (?, ?, 'VEDTAK_INNVILGET', ?::jsonb, ?, ?, ?)
                    """,
                ).use { statement ->
                    statement.setObject(1, brevUuid)
                    statement.setInt(2, behandlingId)
                    statement.setString(3, DOCUMENT_JSON)
                    statement.setString(4, journalpostId)
                    statement.setObject(5, journalpostId?.let { OffsetDateTime.now() })
                    statement.setObject(6, distribuertTidspunkt)
                    statement.executeUpdate()
                }

            connection.commit()
        }

        return OpprettetBehandling(behandlingUuid = behandlingUuid, brevUuid = brevUuid)
    }

    companion object {
        private const val DOCUMENT_JSON =
            """[{"type": "PARAGRAPH", "title": "Tittel", "texts": ["Innhold"]}]"""

        // Cutoff langt etter alle behandlet_tidspunkt brukt i disse testene, slik at
        // getIkkeJournalforteSoknader/getSoknaderMedIkkeDistribuerteBrev sine
        // eksisterende asserts ikke påvirkes av grace-vinduet.
        private val etterAlleTestBehandlinger = OffsetDateTime.parse("2026-06-01T00:00:00Z")

        private val behandlingsperioder =
            listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)))
    }

    private fun generateBehandling(utfall: BehandlingsUtfall = BehandlingsUtfall.Innvilget(behandlingsperioder)): Behandling =
        Behandling(
            utfall = utfall,
            behandletAv = Navident("Z999999"),
            behandletTidspunkt = OffsetDateTime.parse("2026-03-05T10:00:00Z"),
            brev =
                utfall.brevtype()?.let { brevtype ->
                    Brev(
                        brevtype = brevtype,
                        document =
                            listOf(
                                DocumentComponent(
                                    type = DocumentComponentType.HEADER_H1,
                                    title = "Vedtak",
                                    texts = listOf("Søknaden din er innvilget"),
                                ),
                            ),
                    )
                },
        )
}
