package no.nav.syfo.utenlandsopphold.application

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.utenlandsopphold.domain.Soknad
import java.time.OffsetDateTime
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
     * Lagrer en henleggelse for en søknad. Krever at søknaden er MOTTATT
     * (håndheves av [Soknad.henlegg]).
     */
    fun lagreHenleggelse(
        transaction: Transaction,
        soknadMedHenleggelse: Soknad,
    ): Soknad

    /**
     * Henter søknader hvor det fattede vedtaket ennå ikke er journalført
     * (`vedtak.journalpost_id IS NULL`). Brukes av journalføringsjobben.
     *
     * @param fattetBefore Kun vedtak fattet før dette tidspunktet inkluderes. Brukes til å gi
     * API-laget (som forsøker journalføring umiddelbart etter at et vedtak er fattet) rom til
     * å journalføre selv, uten at cronjobben forsøker det samme vedtaket samtidig.
     */
    fun getIkkeJournalforteSoknader(fattetBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at et vedtak er journalført ved å sette `journalpost_id` og
     * `journalfort_tidspunkt` på raden. Kalles etter vellykket arkivering i dokarkiv.
     */
    fun setVedtakJournalfort(
        vedtakId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: OffsetDateTime,
    )

    /**
     * Henter søknader hvor det fattede vedtaket er journalført, men ennå ikke distribuert
     * (`vedtak.journalpost_id IS NOT NULL AND vedtak.distribuert_tidspunkt IS NULL`).
     * Brukes av distribusjonsjobben.
     *
     * @param fattetBefore Kun vedtak fattet før dette tidspunktet inkluderes. Brukes til å gi
     * API-laget (som forsøker distribusjon umiddelbart etter at et vedtak er journalført) rom til
     * å distribuere selv, uten at cronjobben forsøker det samme vedtaket samtidig.
     */
    fun getSoknaderMedIkkeDistribuerteVedtak(fattetBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at et vedtak er distribuert ved å sette `distribuert_tidspunkt` på raden.
     * Kalles etter vellykket bestilling i dokdistfordeling.
     */
    fun setVedtakDistribuert(
        vedtakId: UUID,
        distribuertTidspunkt: OffsetDateTime,
    )

    /**
     * Henter søknader hvor henleggelsen ennå ikke er journalført
     * (`henleggelse.journalpost_id IS NULL`). Brukes av journalføringsjobben.
     *
     * @param henlagtBefore Kun henleggelser gjort før dette tidspunktet inkluderes. Brukes til
     * å gi API-laget (som forsøker journalføring umiddelbart etter at en søknad er henlagt) rom
     * til å journalføre selv, uten at cronjobben forsøker det samme samtidig.
     */
    fun getIkkeJournalforteHenleggelser(henlagtBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at en henleggelse er journalført ved å sette `journalpost_id` og
     * `journalfort_tidspunkt` på raden. Kalles etter vellykket arkivering i dokarkiv.
     */
    fun setHenleggelseJournalfort(
        henleggelseId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: OffsetDateTime,
    )

    /**
     * Henter søknader hvor henleggelsen er journalført, men ennå ikke distribuert.
     * Brukes av distribusjonsjobben.
     *
     * @param henlagtBefore Kun henleggelser gjort før dette tidspunktet inkluderes.
     */
    fun getHenleggelserMedIkkeDistribuert(henlagtBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at en henleggelse er distribuert ved å sette `distribuert_tidspunkt` på raden.
     * Kalles etter vellykket bestilling i dokdistfordeling.
     */
    fun setHenleggelseDistribuert(
        henleggelseId: UUID,
        distribuertTidspunkt: OffsetDateTime,
    )

    fun getUnpublishedSoknader(): List<Soknad>

    fun setSoknadPublished(
        soknadId: UUID,
        publishedAt: OffsetDateTime,
    )

    fun getSoknaderMedUnpublishedVedtak(): List<Soknad>

    fun setVedtakPublished(
        vedtakId: UUID,
        publishedAt: OffsetDateTime,
    )

    fun getSoknaderMedUnpublishedHenleggelse(): List<Soknad>

    fun setHenleggelsePublished(
        henleggelseId: UUID,
        publishedAt: OffsetDateTime,
    )

    fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat
}

enum class LagreMottattSoknadResultat {
    LAGRET,
    ALLEREDE_LAGRET,
}
