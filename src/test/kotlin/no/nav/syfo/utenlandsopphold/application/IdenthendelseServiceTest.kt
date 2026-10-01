package no.nav.syfo.utenlandsopphold.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.IdentType
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.Identifikator
import no.nav.syfo.utenlandsopphold.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import kotlin.test.Test

class IdenthendelseServiceTest {
    private val repository = mockk<ISoknadRepository>(relaxed = true)
    private val service = IdenthendelseService(soknadRepository = repository)

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
        every { repository.oppdaterPersonident(nyIdent, listOf(gammelIdent)) } returns 1

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
}
