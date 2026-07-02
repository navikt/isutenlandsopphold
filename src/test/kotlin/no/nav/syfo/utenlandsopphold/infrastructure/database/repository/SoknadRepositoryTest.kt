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
import java.time.Instant
import java.time.LocalDate
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

    private fun soknad(
        personident: Personident = this.personident,
        soktePerioder: List<Periode> = listOf(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10))),
    ): Soknad =
        Soknad(
            id = UUID.randomUUID(),
            eksternId = UUID.randomUUID(),
            personident = personident,
            soktePerioder = soktePerioder,
            innsendtTidspunkt = Instant.parse("2026-03-01T09:00:00Z"),
        )
}
