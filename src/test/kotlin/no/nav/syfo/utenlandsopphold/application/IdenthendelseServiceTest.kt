package no.nav.syfo.utenlandsopphold.application

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.IdentType
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.Identifikator
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdenthendelseServiceTest {
    private val repository = mockk<ISoknadRepository>(relaxed = true)
    private val pdlClient = mockk<IPdlClient>()
    private val service = IdenthendelseService(soknadRepository = repository, pdlClient = pdlClient)

    private val nyIdent = Personident("22222222222")
    private val gammelIdent = Personident("11111111111")

    private fun hendelse(vararg identer: Identifikator) = KafkaIdenthendelseDTO(identer.toList())

    private fun fnr(
        ident: Personident,
        gjeldende: Boolean,
    ) = Identifikator(ident.value, IdentType.FOLKEREGISTERIDENT, gjeldende)

    private val aktorId = Identifikator("9999999999999", IdentType.AKTORID, true)

    @Test
    fun `oppdaterer personident nar vi har soknader pa gammel ident`() {
        every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true
        every { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) } returns listOf(UUID.randomUUID())

        service.handleIdenthendelse(hendelse(fnr(nyIdent, true), fnr(gammelIdent, false), aktorId))

        verify(exactly = 1) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `gjor ingenting nar hendelsen kun har en folkeregisterident`() {
        service.handleIdenthendelse(hendelse(fnr(nyIdent, true), aktorId))

        verify(exactly = 0) { repository.finnesSoknaderMedPersonident(any()) }
        verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `gjor ingenting nar vi ikke har soknader pa gammel ident`() {
        every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns false

        service.handleIdenthendelse(hendelse(fnr(nyIdent, true), fnr(gammelIdent, false)))

        verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `gjor ingenting nar hendelsen mangler gjeldende ident`() {
        service.handleIdenthendelse(hendelse(fnr(nyIdent, false), fnr(gammelIdent, false)))

        verify(exactly = 0) { repository.finnesSoknaderMedPersonident(any()) }
    }

    @Test
    fun `handleOutdatedIdent flytter soknader fra utdatert ident til aktiv ident`() =
        runTest {
            coEvery { pdlClient.getAktivIdent(gammelIdent) } returns nyIdent
            coEvery { pdlClient.getAktivIdent(nyIdent) } returns nyIdent
            every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true
            val soknadId = UUID.randomUUID()
            every { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) } returns listOf(soknadId)

            val result = service.handleOutdatedIdent(gammelIdent)

            assertEquals(listOf(soknadId), result)
            verify(exactly = 1) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
        }

    @Test
    fun `handleOutdatedIdent gjor ingenting nar ident allerede er aktiv`() =
        runTest {
            coEvery { pdlClient.getAktivIdent(nyIdent) } returns nyIdent

            val result = service.handleOutdatedIdent(nyIdent)

            assertEquals(emptyList(), result)
            verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, any()) }
        }

    @Test
    fun `handleOutdatedIdent gjor ingenting nar det ikke finnes soknader pa utdatert ident`() =
        runTest {
            coEvery { pdlClient.getAktivIdent(gammelIdent) } returns nyIdent
            every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns false

            val result = service.handleOutdatedIdent(gammelIdent)

            assertEquals(emptyList(), result)
            verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, any()) }
        }

    @Test
    fun `handleOutdatedIdent feiler nar ny ident ikke er aktiv ident i PDL`() =
        runTest {
            coEvery { pdlClient.getAktivIdent(gammelIdent) } returns nyIdent
            coEvery { pdlClient.getAktivIdent(nyIdent) } returns Personident("33333333333")
            every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true

            assertFailsWith<IllegalStateException> { service.handleOutdatedIdent(gammelIdent) }
            verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, any()) }
        }

    @Test
    fun `handleOutdatedIdent feiler nar PDL ikke returnerer aktiv ident`() =
        runTest {
            coEvery { pdlClient.getAktivIdent(gammelIdent) } returns null

            assertFailsWith<RuntimeException> { service.handleOutdatedIdent(gammelIdent) }
            verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, any()) }
        }
}
