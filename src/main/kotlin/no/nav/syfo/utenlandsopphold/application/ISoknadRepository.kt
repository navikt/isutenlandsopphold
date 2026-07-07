package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad
import java.time.Instant
import java.util.UUID

interface ISoknadRepository {
    fun hentSoknader(personident: Personident): List<Soknad>

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

    fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat
}

enum class LagreMottattSoknadResultat {
    LAGRET,
    ALLEREDE_LAGRET,
}
