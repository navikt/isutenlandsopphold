package no.nav.syfo.utenlandsopphold.application

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.IdentType
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.Identifikator
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IdenthendelseServiceTest {
    private val repository = mockk<ISoknadRepository>(relaxed = true)
    private val pdlClient = mockk<IPdlClient>()
    private val service = IdenthendelseService(soknadRepository = repository, pdlClient = pdlClient)

    private val nyIdent = Personident("22222222222")
    private val gammelIdent = Personident("11111111111")

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    private fun hendelse(vararg identer: Identifikator) = KafkaIdenthendelseDTO(identer.toList())

    private fun fnr(
        ident: Personident,
        gjeldende: Boolean,
    ) = Identifikator(ident.value, IdentType.FOLKEREGISTERIDENT, gjeldende)

    private val aktorId = Identifikator("9999999999999", IdentType.AKTORID, true)

    @Test
    fun `oppdaterer personident nar PDL bekrefter ny ident`() {
        every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true
        coEvery { pdlClient.hentIdenter(nyIdent) } returns
            listOf(PdlIdent(nyIdent.value, historisk = false), PdlIdent(gammelIdent.value, historisk = true))
        every { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) } returns 1

        runBlocking { service.handleIdenthendelse(hendelse(fnr(nyIdent, true), fnr(gammelIdent, false), aktorId)) }

        verify(exactly = 1) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `gjor ingenting nar hendelsen kun har en folkeregisterident`() {
        runBlocking { service.handleIdenthendelse(hendelse(fnr(nyIdent, true), aktorId)) }

        verify(exactly = 0) { repository.finnesSoknaderMedPersonident(any()) }
        verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `gjor ingenting nar vi ikke har soknader pa gammel ident`() {
        every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns false

        runBlocking { service.handleIdenthendelse(hendelse(fnr(nyIdent, true), fnr(gammelIdent, false))) }

        verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `gjor ingenting nar hendelsen mangler gjeldende ident`() {
        runBlocking { service.handleIdenthendelse(hendelse(fnr(nyIdent, false), fnr(gammelIdent, false))) }

        verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `kaster og oppdaterer ikke nar PDL ikke har ny ident som aktiv`() {
        every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true
        coEvery { pdlClient.hentIdenter(nyIdent) } returns listOf(PdlIdent(gammelIdent.value, historisk = false))

        assertFailsWith<IllegalStateException> {
            runBlocking { service.handleIdenthendelse(hendelse(fnr(nyIdent, true), fnr(gammelIdent, false))) }
        }

        verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `hopper over utdatert hendelse nar ny ident er historisk i PDL`() {
        every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true
        coEvery { pdlClient.hentIdenter(nyIdent) } returns listOf(PdlIdent(nyIdent.value, historisk = true))

        runBlocking { service.handleIdenthendelse(hendelse(fnr(nyIdent, true), fnr(gammelIdent, false))) }

        verify(exactly = 0) { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) }
    }

    @Test
    fun `kaster nar PDL ikke finner identer`() {
        every { repository.finnesSoknaderMedPersonident(listOf(gammelIdent)) } returns true
        coEvery { pdlClient.hentIdenter(nyIdent) } returns null

        assertFailsWith<IllegalStateException> {
            runBlocking { service.handleIdenthendelse(hendelse(fnr(nyIdent, true), fnr(gammelIdent, false))) }
        }
    }
}
