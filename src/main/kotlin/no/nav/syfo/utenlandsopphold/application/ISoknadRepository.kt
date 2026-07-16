package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad
import java.time.Instant
import java.util.UUID

interface ISoknadRepository {
    fun hentSoknad(soknadId: UUID): Soknad?

    fun hentSoknadForUpdate(
        transaction: Transaction,
        soknadId: UUID,
    ): Soknad?

    fun hentSoknader(personident: Personident): List<Soknad>

    fun lagreVedtak(
        transaction: Transaction,
        soknadMedVedtak: Soknad,
    ): Soknad

    /**
     * Henter søknader hvor det fattede vedtaket ennå ikke er journalført
     * (`vedtak.journalpost_id IS NULL`). Brukes av journalføringsjobben.
     *
     * @param fattetBefore Kun vedtak fattet før dette tidspunktet inkluderes. Brukes til å gi
     * API-laget (som forsøker journalføring umiddelbart etter at et vedtak er fattet) rom til
     * å journalføre selv, uten at cronjobben forsøker det samme vedtaket samtidig.
     */
    fun getIkkeJournalforteSoknader(fattetBefore: Instant): List<Soknad>

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
    fun getSoknaderMedIkkeDistribuerteVedtak(): List<Soknad>

    /**
     * Markerer at et vedtak er distribuert ved å sette `distribuert_tidspunkt` på raden.
     * Kalles etter vellykket bestilling i dokdistfordeling.
     */
    fun setVedtakDistribuert(
        vedtakId: UUID,
        distribuertTidspunkt: Instant,
    )

    fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat
}

enum class LagreMottattSoknadResultat {
    LAGRET,
    ALLEREDE_LAGRET,
}
