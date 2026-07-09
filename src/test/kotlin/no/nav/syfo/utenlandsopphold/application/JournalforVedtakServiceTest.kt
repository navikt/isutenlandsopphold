package no.nav.syfo.utenlandsopphold.application

import kotlinx.coroutines.test.runTest
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.lagSoknad
import no.nav.syfo.utenlandsopphold.domain.veileder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalforVedtakServiceTest {
    private fun soknadMedVedtak(): Soknad =
        lagSoknad().fattVedtak(
            utfall = Utfall.Innvilget,
            fattetAv = veileder,
            now = Instant.parse("2026-01-10T12:00:00Z"),
        )

    @Test
    fun `journalfører og oppdaterer u-journalført vedtak`() =
        runTest {
            val soknad = soknadMedVedtak()
            val repository = FakeSoknadRepository(listOf(soknad))
            val pdfClient = FakePdfClient()
            val journalforingService = FakeJournalforingService(Result.success(JournalpostId("999")))

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = FakePdlClient(),
                    pdfClient = pdfClient,
                    journalforingService = journalforingService,
                    distribusjonService = FakeDistribusjonService(),
                )

            service.journalforVedtak()

            assertEquals(1, pdfClient.callCount)
            assertEquals(1, journalforingService.callCount)
            assertEquals(1, repository.journalforte.size)
            val (vedtakId, journalpostId, _) = repository.journalforte.single()
            assertEquals(soknad.vedtak!!.vedtakId, vedtakId)
            assertEquals(JournalpostId("999"), journalpostId)
        }

    @Test
    fun `feil for ett vedtak stopper ikke journalføring av de andre`() =
        runTest {
            val soknadSomFeiler = soknadMedVedtak()
            val soknadSomLykkes = soknadMedVedtak()
            val repository = FakeSoknadRepository(listOf(soknadSomFeiler, soknadSomLykkes))
            val pdfClient = FakePdfClient()

            var callCount = 0
            val journalforingService =
                FakeJournalforingService { _, _, _ ->
                    callCount++
                    if (callCount == 1) {
                        Result.failure(RuntimeException("dokarkiv er nede"))
                    } else {
                        Result.success(JournalpostId("999"))
                    }
                }

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = FakePdlClient(),
                    pdfClient = pdfClient,
                    journalforingService = journalforingService,
                    distribusjonService = FakeDistribusjonService(),
                )

            service.journalforVedtak()

            assertEquals(1, repository.journalforte.size)
            assertEquals(soknadSomLykkes.vedtak!!.vedtakId, repository.journalforte.single().first)
        }

    @Test
    fun `søknad uten vedtak journalføres ikke, men stopper ikke andre`() =
        runTest {
            val soknadUtenVedtak = lagSoknad()
            val soknadMedVedtak = soknadMedVedtak()
            val repository = FakeSoknadRepository(listOf(soknadUtenVedtak, soknadMedVedtak))

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = FakePdlClient(),
                    pdfClient = FakePdfClient(),
                    journalforingService = FakeJournalforingService(Result.success(JournalpostId("999"))),
                    distribusjonService = FakeDistribusjonService(),
                )

            service.journalforVedtak()

            assertEquals(repository.journalforte.size, 1)
            assertEquals(soknadMedVedtak.vedtak!!.vedtakId, repository.journalforte.single().first)
        }

    @Test
    fun `distribuerer og oppdaterer journalfort, ikke-distribuert vedtak`() =
        runTest {
            val soknad =
                soknadMedVedtak().let {
                    it.journalforVedtak(JournalpostId("999"), Instant.parse("2026-01-11T08:00:00Z"))
                }
            val repository = FakeSoknadRepository(listOf(soknad))
            val distribusjonService = FakeDistribusjonService(Result.success("bestilling-1"))

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = FakePdlClient(),
                    pdfClient = FakePdfClient(),
                    journalforingService = FakeJournalforingService(Result.success(JournalpostId("999"))),
                    distribusjonService = distribusjonService,
                )

            service.distribuerVedtak()

            assertEquals(1, distribusjonService.callCount)
            assertEquals(1, repository.distribuerte.size)
            val (vedtakId, _) = repository.distribuerte.single()
            assertEquals(soknad.vedtak!!.vedtakId, vedtakId)
        }

    @Test
    fun `feil for ett vedtak stopper ikke distribusjon av de andre`() =
        runTest {
            val soknadSomFeiler =
                soknadMedVedtak().journalforVedtak(JournalpostId("111"), Instant.parse("2026-01-11T08:00:00Z"))
            val soknadSomLykkes =
                soknadMedVedtak().journalforVedtak(JournalpostId("222"), Instant.parse("2026-01-11T08:00:00Z"))
            val repository = FakeSoknadRepository(listOf(soknadSomFeiler, soknadSomLykkes))

            var callCount = 0
            val distribusjonService =
                FakeDistribusjonService { _ ->
                    callCount++
                    if (callCount == 1) {
                        Result.failure(RuntimeException("dokdistfordeling er nede"))
                    } else {
                        Result.success("bestilling-1")
                    }
                }

            val service =
                JournalforVedtakService(
                    soknadRepository = repository,
                    personInfoClient = FakePdlClient(),
                    pdfClient = FakePdfClient(),
                    journalforingService = FakeJournalforingService(Result.success(JournalpostId("999"))),
                    distribusjonService = distribusjonService,
                )

            service.distribuerVedtak()

            assertEquals(1, repository.distribuerte.size)
            assertEquals(soknadSomLykkes.vedtak!!.vedtakId, repository.distribuerte.single().first)
        }
}

private class FakeSoknadRepository(
    private val soknader: List<Soknad>,
) : ISoknadRepository {
    val journalforte = mutableListOf<Triple<UUID, JournalpostId, Instant>>()
    val distribuerte = mutableListOf<Pair<UUID, Instant>>()

    override fun hentSoknader(personident: Personident): List<Soknad> = soknader.filter { it.personident == personident }

    override fun lagreMottattSoknad(soknad: Soknad): Soknad = soknad

    override fun getIkkeJournalforteSoknader(): List<Soknad> = soknader

    override fun setVedtakJournalfort(
        vedtakId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: Instant,
    ) {
        journalforte.add(Triple(vedtakId, journalpostId, journalfortTidspunkt))
    }

    override fun getIkkeDistribuerteSoknader(): List<Soknad> =
        soknader.filter { it.vedtak?.let { vedtak -> vedtak.erJournalfort && !vedtak.erDistribuert } == true }

    override fun setVedtakDistribuert(
        vedtakId: UUID,
        distribuertTidspunkt: Instant,
    ) {
        distribuerte.add(Pair(vedtakId, distribuertTidspunkt))
    }
}

private class FakePdlClient(
    private val navn: String = "Ola Nordmann",
) : IPdlClient {
    override suspend fun getNavn(personident: Personident): String = navn
}

private class FakePdfClient : IPdfClient {
    var callCount = 0
        private set

    override suspend fun createVedtakPdf(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate,
    ): ByteArray {
        callCount++
        return byteArrayOf(1, 2, 3)
    }
}

private class FakeJournalforingService(
    private val onJournalfor: (Personident, ByteArray, String) -> Result<JournalpostId>,
) : IJournalforingService {
    var callCount = 0
        private set

    constructor(result: Result<JournalpostId>) : this({ _, _, _ -> result })

    override suspend fun journalfor(
        personident: Personident,
        pdf: ByteArray,
        eksternReferanseId: String,
    ): Result<JournalpostId> {
        callCount++
        return onJournalfor(personident, pdf, eksternReferanseId)
    }
}

private class FakeDistribusjonService(
    private val onDistribuer: (JournalpostId) -> Result<String> = { Result.success("bestilling-1") },
) : IDistribusjonService {
    var callCount = 0
        private set

    constructor(result: Result<String>) : this({ _ -> result })

    override suspend fun distribuer(journalpostId: JournalpostId): Result<String> {
        callCount++
        return onDistribuer(journalpostId)
    }
}
