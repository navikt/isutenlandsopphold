package no.nav.syfo.utenlandsopphold.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellArsak
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.VedtaksUtfall
import no.nav.syfo.utenlandsopphold.domain.brevDocument
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.veileder
import no.nav.syfo.utenlandsopphold.testutil.TestTransactionManager
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Dekker koblingen mellom behandling og brevutsending. At [BrevService] gjør jobben er
 * dekket av BrevServiceTest, og at domenet håndhever invariantene av SoknadTest. Her
 * testes bare at [SoknadService] faktisk kaller brevtjenesten for de utfallene som
 * skal ha brev.
 */
class SoknadServiceTest {
    private val repository = mockk<ISoknadRepository>()
    private val brevService = mockk<BrevService>(relaxed = true)

    private val service =
        SoknadService(
            transactionManager = TestTransactionManager,
            soknadRepository = repository,
            brevService = brevService,
        )

    private val soknad = lagSoknad()

    private fun stubHentOgLagre() {
        every { repository.hentSoknadForUpdate(any(), any()) } returns soknad
        every { repository.lagreBehandling(any(), any()) } answers { secondArg() }
        coEvery { brevService.journalforBrev(any<Soknad>()) } answers { firstArg() }
    }

    /**
     * Journalføring og distribusjon skjer i en fire-and-forget bakgrunnsoppgave som
     * svelger alle exceptions, derfor timeout i verifiseringen.
     */
    @Test
    fun `fattVedtak journalfører og distribuerer brevet`() {
        stubHentOgLagre()

        service.fattVedtak(
            soknadId = soknad.id,
            behandletAv = veileder,
            utfall = VedtaksUtfall.INNVILGET,
            innvilgedePerioder = emptyList(),
            begrunnelse = null,
            document = brevDocument,
        )

        coVerify(timeout = 2000) { brevService.journalforBrev(any<Soknad>()) }
        coVerify(timeout = 2000) { brevService.distribuerBrev(any<Soknad>()) }
    }

    @Test
    fun `henlegg journalfører og distribuerer brevet`() {
        stubHentOgLagre()

        service.henlegg(
            soknadId = soknad.id,
            behandletAv = veileder,
            document = brevDocument,
            begrunnelse = "Søker har trukket søknaden",
        )

        coVerify(timeout = 2000) { brevService.journalforBrev(any<Soknad>()) }
        coVerify(timeout = 2000) { brevService.distribuerBrev(any<Soknad>()) }
    }

    @Test
    fun `merkIkkeAktuell sender ikke brev`() {
        stubHentOgLagre()

        service.merkIkkeAktuell(
            soknadId = soknad.id,
            behandletAv = veileder,
            arsak = IkkeAktuellArsak.DUPLIKAT,
        )

        coVerify(exactly = 0) { brevService.journalforBrev(any<Soknad>()) }
        coVerify(exactly = 0) { brevService.distribuerBrev(any<Soknad>()) }
    }

    @Test
    fun `behandling av søknad som ikke finnes kaster SoknadFinnesIkkeException`() {
        every { repository.hentSoknadForUpdate(any(), any()) } returns null

        assertFailsWith<SoknadFinnesIkkeException> {
            service.merkIkkeAktuell(
                soknadId = soknad.id,
                behandletAv = veileder,
                arsak = IkkeAktuellArsak.DUPLIKAT,
            )
        }
    }
}
