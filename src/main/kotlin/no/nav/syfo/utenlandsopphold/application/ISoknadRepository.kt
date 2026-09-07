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

    fun lagreBehandlingsutfall(
        transaction: Transaction,
        soknadMedBehandlingsutfall: Soknad,
    ): Soknad

    /**
     * Henter søknader hvor behandlingsutfallet ennå ikke er journalført
     * (`behandlingsutfall.journalpost_id IS NULL`). Brukes av journalføringsjobben.
     *
     * @param fattetBefore Kun behandlingsutfall registrert før dette tidspunktet inkluderes. Brukes
     * til å gi API-laget (som forsøker journalføring umiddelbart etter at et behandlingsutfall er
     * registrert) rom til å journalføre selv, uten at cronjobben forsøker det samme samtidig.
     */
    fun getIkkeJournalforteSoknader(fattetBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at et behandlingsutfall er journalført ved å sette `journalpost_id` og
     * `journalfort_tidspunkt` på raden. Kalles etter vellykket arkivering i dokarkiv.
     */
    fun setBehandlingsutfallJournalfort(
        behandlingsutfallId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: OffsetDateTime,
    )

    /**
     * Henter søknader hvor behandlingsutfallet er journalført, men ennå ikke distribuert
     * (`behandlingsutfall.journalpost_id IS NOT NULL AND behandlingsutfall.distribuert_tidspunkt IS NULL`).
     * Brukes av distribusjonsjobben.
     *
     * @param fattetBefore Kun behandlingsutfall registrert før dette tidspunktet inkluderes. Brukes
     * til å gi API-laget (som forsøker distribusjon umiddelbart etter at et behandlingsutfall er
     * journalført) rom til å distribuere selv, uten at cronjobben forsøker det samme samtidig.
     */
    fun getSoknaderMedIkkeDistribuerteBehandlingsutfall(fattetBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at et behandlingsutfall er distribuert ved å sette `distribuert_tidspunkt` på raden.
     * Kalles etter vellykket bestilling i dokdistfordeling.
     */
    fun setBehandlingsutfallDistribuert(
        behandlingsutfallId: UUID,
        distribuertTidspunkt: OffsetDateTime,
    )

    fun getUnpublishedSoknader(): List<Soknad>

    fun setSoknadPublished(
        soknadId: UUID,
        publishedAt: OffsetDateTime,
    )

    fun getSoknaderMedUnpublishedBehandlingsutfall(): List<Soknad>

    fun setBehandlingsutfallPublished(
        behandlingsutfallId: UUID,
        publishedAt: OffsetDateTime,
    )

    fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat
}

enum class LagreMottattSoknadResultat {
    LAGRET,
    ALLEREDE_LAGRET,
}
