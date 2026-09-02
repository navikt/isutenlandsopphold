package no.nav.syfo.utenlandsopphold.domain

import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import java.time.OffsetDateTime
import java.util.UUID

sealed interface Utfall {
    data object Innvilget : Utfall

    data class DelvisInnvilget(
        val innvilgedePerioder: List<Periode>,
    ) : Utfall

    data object Avslag : Utfall

    companion object {
        fun from(
            utfall: String,
            innvilgedePerioder: List<Periode>,
        ): Utfall =
            when (utfall) {
                "INNVILGET" -> Innvilget
                "DELVIS_INNVILGET" -> DelvisInnvilget(innvilgedePerioder)
                "AVSLAG" -> {
                    require(innvilgedePerioder.isEmpty()) { "innvilgedePerioder skal være tom ved avslag" }
                    Avslag
                }
                else -> throw IllegalArgumentException("Invalid utfall: $utfall")
            }
    }
}

/**
 * Behandlingsutfall er resultatet av behandlingen av en søknad: enten et [Vedtak] (en
 * rettslig bindende avgjørelse med et [Utfall]) eller en [Henleggelse] (søknaden avgjøres
 * ikke, f.eks. fordi den trekkes). En henleggelse er teknisk sett ikke et vedtak, men deler
 * samme livssyklus for dokumentgenerering, journalføring, distribusjon og publisering — se
 * [Utsending].
 */
sealed interface Behandlingsutfall {
    val behandlingsutfallId: UUID
    val fattetAv: Navident
    val fattetTidspunkt: OffsetDateTime
    val begrunnelse: String?
    val utsending: Utsending

    val document: List<DocumentComponent>
        get() = utsending.document

    val journalpostId: JournalpostId?
        get() = utsending.journalpostId

    val journalfortTidspunkt: OffsetDateTime?
        get() = utsending.journalfortTidspunkt

    val distribuertTidspunkt: OffsetDateTime?
        get() = utsending.distribuertTidspunkt

    val erJournalfort: Boolean
        get() = utsending.erJournalfort

    val erDistribuert: Boolean
        get() = utsending.erDistribuert

    fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Behandlingsutfall

    fun distribuer(tidspunkt: OffsetDateTime): Behandlingsutfall
}

data class Vedtak(
    val utfall: Utfall,
    override val fattetAv: Navident,
    override val fattetTidspunkt: OffsetDateTime,
    val innvilgedePerioder: List<Periode>,
    override val behandlingsutfallId: UUID = UUID.randomUUID(),
    override val begrunnelse: String? = null,
    override val utsending: Utsending,
) : Behandlingsutfall {
    init {
        when (utfall) {
            Utfall.Innvilget -> {
                require(innvilgedePerioder.isNotEmpty()) { "Innvilget vedtak må ha innvilgede perioder" }
                require(begrunnelse == null) { "Innvilget vedtak skal ikke ha begrunnelse" }
            }
            is Utfall.DelvisInnvilget -> {
                require(utfall.innvilgedePerioder.isNotEmpty()) { "Delvis innvilget vedtak må ha innvilgede perioder" }
                require(utfall.innvilgedePerioder == innvilgedePerioder) {
                    "Innvilgede perioder på utfall og vedtak må være like"
                }
                require(!begrunnelse.isNullOrBlank()) { "Delvis innvilget vedtak må ha begrunnelse" }
            }
            Utfall.Avslag -> {
                require(innvilgedePerioder.isEmpty()) { "Avslått vedtak skal ikke ha innvilgede perioder" }
                require(!begrunnelse.isNullOrBlank()) { "Avslått vedtak må ha begrunnelse" }
            }
        }
    }

    override fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Vedtak = copy(utsending = utsending.journalfor(journalpostId, tidspunkt))

    override fun distribuer(tidspunkt: OffsetDateTime): Vedtak = copy(utsending = utsending.distribuer(tidspunkt))
}

/**
 * En henleggelse er teknisk sett ikke et vedtak — søknaden avgjøres ikke med et utfall —
 * men krever likevel en begrunnelse og genererer et dokument som journalføres og
 * distribueres på samme måte som et vedtak.
 */
data class Henleggelse(
    override val fattetAv: Navident,
    override val fattetTidspunkt: OffsetDateTime,
    override val behandlingsutfallId: UUID = UUID.randomUUID(),
    override val begrunnelse: String,
    override val utsending: Utsending,
) : Behandlingsutfall {
    init {
        require(begrunnelse.isNotBlank()) { "Henleggelse må ha begrunnelse" }
    }

    override fun journalfor(
        journalpostId: JournalpostId,
        tidspunkt: OffsetDateTime,
    ): Henleggelse = copy(utsending = utsending.journalfor(journalpostId, tidspunkt))

    override fun distribuer(tidspunkt: OffsetDateTime): Henleggelse = copy(utsending = utsending.distribuer(tidspunkt))
}
