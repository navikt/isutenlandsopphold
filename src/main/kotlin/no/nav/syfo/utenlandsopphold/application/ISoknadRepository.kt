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

    /**
     * Lagrer behandlingen, dens innvilgede perioder og brevet i samme transaksjon.
     * Enten lagres alt, eller ingenting.
     */
    fun lagreBehandling(
        transaction: Transaction,
        behandletSoknad: Soknad,
    ): Soknad

    /**
     * Henter søknader hvor brevet ennå ikke er journalført (`brev.journalpost_id IS NULL`).
     * Brukes av journalføringsjobben.
     *
     * @param behandletBefore Kun behandlinger utført før dette tidspunktet inkluderes. Brukes til å gi
     * API-laget (som forsøker journalføring umiddelbart etter at en søknad er behandlet) rom til
     * å journalføre selv, uten at cronjobben forsøker det samme brevet samtidig.
     */
    fun getIkkeJournalforteSoknader(behandletBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at et brev er journalført ved å sette `journalpost_id` og
     * `journalfort_tidspunkt` på raden. Kalles etter vellykket arkivering i dokarkiv.
     */
    fun setBrevJournalfort(
        brevId: UUID,
        journalpostId: JournalpostId,
        journalfortTidspunkt: OffsetDateTime,
    )

    /**
     * Henter søknader hvor brevet er journalført, men ennå ikke distribuert
     * (`brev.journalpost_id IS NOT NULL AND brev.distribuert_tidspunkt IS NULL`).
     * Brukes av distribusjonsjobben.
     *
     * @param behandletBefore Kun behandlinger utført før dette tidspunktet inkluderes. Brukes til å gi
     * API-laget (som forsøker distribusjon umiddelbart etter journalføring) rom til å distribuere
     * selv, uten at cronjobben forsøker det samme brevet samtidig.
     */
    fun getSoknaderMedIkkeDistribuerteBrev(behandletBefore: OffsetDateTime): List<Soknad>

    /**
     * Markerer at et brev er distribuert ved å sette `distribuert_tidspunkt` på raden.
     * Kalles etter vellykket bestilling i dokdistfordeling.
     */
    fun setBrevDistribuert(
        brevId: UUID,
        distribuertTidspunkt: OffsetDateTime,
    )

    fun getUnpublishedSoknader(): List<Soknad>

    fun setSoknadPublished(
        soknadId: UUID,
        publishedAt: OffsetDateTime,
    )

    fun getSoknaderMedUnpublishedBehandling(): List<Soknad>

    fun setBehandlingPublished(
        behandlingId: UUID,
        publishedAt: OffsetDateTime,
    )

    fun lagreMottattSoknad(soknad: Soknad): LagreMottattSoknadResultat
}

enum class LagreMottattSoknadResultat {
    LAGRET,
    ALLEREDE_LAGRET,
}
