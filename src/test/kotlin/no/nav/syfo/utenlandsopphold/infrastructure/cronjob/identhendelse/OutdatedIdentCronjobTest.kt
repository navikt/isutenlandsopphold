package no.nav.syfo.utenlandsopphold.infrastructure.cronjob.identhendelse

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.application.IPdlClient
import no.nav.syfo.utenlandsopphold.application.ISoknadRepository
import no.nav.syfo.utenlandsopphold.application.IdenthendelseService
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutdatedIdentCronjobTest {
    private val repository = mockk<ISoknadRepository>(relaxed = true)
    private val pdlClient = mockk<IPdlClient>()
    private val identhendelseService = IdenthendelseService(soknadRepository = repository, pdlClient = pdlClient)

    private val gammelIdent = Personident("11111111111")
    private val nyIdent = Personident("22222222222")

    private fun cronjob(soknadIds: List<UUID>) =
        OutdatedIdentCronjob(
            soknadRepository = repository,
            identhendelseService = identhendelseService,
            soknadIds = soknadIds,
        )

    @Test
    fun `flytter soknader pa utdatert ident til aktiv ident`() =
        runTest {
            val soknad = lagSoknad().copy(personident = gammelIdent)
            val annenSoknad = lagSoknad().copy(personident = gammelIdent)
            every { repository.hentSoknad(soknad.id) } returns soknad
            every { repository.hentSoknad(annenSoknad.id) } returns annenSoknad
            coEvery { pdlClient.getAktivIdent(gammelIdent) } returns nyIdent
            coEvery { pdlClient.getAktivIdent(nyIdent) } returns nyIdent
            every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true
            every { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) } returns listOf(soknad.id, annenSoknad.id)

            val result = cronjob(listOf(soknad.id, annenSoknad.id)).run()

            assertEquals(1, result.size)
            assertTrue(result.single().isSuccess)
            verify(exactly = 1) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
        }

    @Test
    fun `returnerer feilet resultat nar PDL ikke har aktiv ident`() =
        runTest {
            val soknad = lagSoknad().copy(personident = gammelIdent)
            every { repository.hentSoknad(soknad.id) } returns soknad
            coEvery { pdlClient.getAktivIdent(gammelIdent) } returns null

            val result = cronjob(listOf(soknad.id)).run()

            assertEquals(1, result.size)
            assertTrue(result.single().isFailure)
            verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, any()) }
        }

    @Test
    fun `ignorerer ukjent soknad-uuid`() =
        runTest {
            val soknadId = UUID.randomUUID()
            every { repository.hentSoknad(soknadId) } returns null

            val result = cronjob(listOf(soknadId)).run()

            assertTrue(result.isEmpty())
            verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, any()) }
        }
}
