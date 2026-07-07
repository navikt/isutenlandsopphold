package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.SoknadStatus
import no.nav.syfo.utenlandsopphold.infrastructure.database.TestDatabase
import no.nav.syfo.utenlandsopphold.infrastructure.database.dropData
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoknadRepositoryTest {
    private val database = TestDatabase()
    private val repository = SoknadRepository(database = database)

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

        repository.upsertSoknad(soknad)
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
    fun `oppdaterer eksisterende soknad med samme eksternId`() {
        val eksternId = UUID.randomUUID()
        val opprinneligPersonident = Personident("11111111111")
        val oppdatertPersonident = Personident("22222222222")
        val oppdatertePerioder = listOf(Periode(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)))
        val oppdatertInnsendtTidspunkt = OffsetDateTime.parse("2026-04-01T09:00:00Z")
        repository.upsertSoknad(
            soknad(
                eksternId = eksternId,
                personident = opprinneligPersonident,
                soktePerioder = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10))),
            ),
        )
        val oppdatertSoknad =
            soknad(
                eksternId = eksternId,
                personident = oppdatertPersonident,
                soktePerioder = oppdatertePerioder,
                innsendtTidspunkt = oppdatertInnsendtTidspunkt,
            )

        repository.upsertSoknad(oppdatertSoknad)
        val hentet = repository.hentSoknader(oppdatertPersonident)

        assertTrue(repository.hentSoknader(opprinneligPersonident).isEmpty())
        assertEquals(1, hentet.size)
        val lagret = hentet.single()
        assertEquals(oppdatertSoknad.id, lagret.id)
        assertEquals(eksternId, lagret.eksternId)
        assertEquals(oppdatertPersonident, lagret.personident)
        assertEquals(oppdatertePerioder, lagret.soktePerioder)
        assertEquals(oppdatertInnsendtTidspunkt, lagret.innsendtTidspunkt)
    }

    @Test
    fun `henter kun soknader for angitt personident`() {
        repository.upsertSoknad(soknad(personident = Personident("11111111111")))
        repository.upsertSoknad(soknad(personident = Personident("22222222222")))

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

        repository.upsertSoknad(soknad(soktePerioder = perioder))
        val lagret = repository.hentSoknader(personident).single()

        assertEquals(2, lagret.soktePerioder.size)
        assertTrue(lagret.soktePerioder.containsAll(perioder))
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
}
