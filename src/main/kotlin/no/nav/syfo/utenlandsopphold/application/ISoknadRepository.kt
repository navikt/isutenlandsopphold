package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad
import java.time.Instant
import java.util.UUID

interface ISoknadRepository {
    fun hentSoknader(personident: Personident): List<Soknad>

    fun lagreMottattSoknad(soknad: Soknad): Soknad

    /**
     * Henter søknader hvor det fattede vedtaket ennå ikke er journalført
     * (`vedtak.journalpost_id IS NULL`). Brukes av journalføringsjobben.
     */
    fun getIkkeJournalforteSoknader(): List<Soknad>

    /**
     * Markerer at et vedtak er journalført ved å sette `journalpost_id` og
     * `journalfort_tidspunkt` på raden. Kalles etter vellykket arkivering i dokarkiv.
     */
    fun setVedtakJournalfort(
        vedtakId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: Instant,
    )

    /**
     * Henter søknader hvor det fattede vedtaket er journalført, men ennå ikke distribuert
     * (`vedtak.journalpost_id IS NOT NULL AND vedtak.distribuert_tidspunkt IS NULL`).
     * Brukes av distribusjonsjobben.
     */
    fun getIkkeDistribuerteSoknader(): List<Soknad>

    /**
     * Markerer at et vedtak er distribuert ved å sette `distribuert_tidspunkt` på raden.
     * Kalles etter vellykket bestilling i dokdistfordeling.
     */
    fun setVedtakDistribuert(
        vedtakId: UUID,
        distribuertTidspunkt: Instant,
    )
}
