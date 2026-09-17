package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.domain.Behandling
import no.nav.syfo.utenlandsopphold.domain.Brev
import no.nav.syfo.utenlandsopphold.domain.Brevtype
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.IkkeAktuellGrunn
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class PSoknad(
    val id: Int,
    val uuid: UUID,
    val eksternId: UUID,
    val personident: Personident,
    val innsendtTidspunkt: OffsetDateTime,
    val createdAt: OffsetDateTime,
) {
    fun toSoknad(
        soktePerioder: List<PSoknadPeriode>,
        behandling: PBehandling?,
        behandlingPerioder: List<PBehandlingPeriode>,
        brev: PBrev?,
    ): Soknad =
        Soknad(
            id = uuid,
            eksternId = eksternId,
            personident = personident,
            soktePerioder = soktePerioder.map { it.toPeriode() },
            innsendtTidspunkt = innsendtTidspunkt,
            behandling =
                behandling?.toBehandling(
                    innvilgedePerioder = behandlingPerioder.map { it.toPeriode() },
                    brev = brev,
                ),
        )
}

data class PSoknadPeriode(
    val id: Int,
    val soknadId: Int,
    val fom: LocalDate,
    val tom: LocalDate,
) {
    fun toPeriode(): Periode = Periode(fom = fom, tom = tom)
}

data class PBehandlingPeriode(
    val id: Int,
    val behandlingId: Int,
    val fom: LocalDate,
    val tom: LocalDate,
) {
    fun toPeriode(): Periode = Periode(fom = fom, tom = tom)
}

data class PBehandling(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val soknadId: Int,
    val utfall: String,
    val behandletAv: String,
    val behandletTidspunkt: OffsetDateTime,
    val begrunnelse: String?,
    val ikkeAktuellGrunn: String?,
) {
    fun toBehandling(
        innvilgedePerioder: List<Periode>,
        brev: PBrev?,
    ): Behandling =
        Behandling(
            behandlingId = uuid,
            utfall = utfall.toUtfall(innvilgedePerioder, ikkeAktuellGrunn, begrunnelse),
            behandletAv = Navident(behandletAv),
            behandletTidspunkt = behandletTidspunkt,
            brev = brev?.toBrev(),
        )
}

data class PBrev(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val behandlingId: Int,
    val brevtype: String,
    val document: String,
    val journalpostId: String?,
    val journalfortTidspunkt: OffsetDateTime?,
    val distribuertTidspunkt: OffsetDateTime?,
) {
    fun toBrev(): Brev =
        Brev(
            brevId = uuid,
            brevtype = brevtype.toBrevtype(),
            document = document.toDocumentComponents(),
            journalpostId = journalpostId?.let { JournalpostId(it) },
            journalfortTidspunkt = journalfortTidspunkt,
            distribuertTidspunkt = distribuertTidspunkt,
        )
}

private val documentMapper = configuredJacksonMapper()

fun List<DocumentComponent>.serializeToJson(): String = documentMapper.writeValueAsString(this)

private fun String.toDocumentComponents(): List<DocumentComponent> = documentMapper.readValue(this)

fun Utfall.dbValue(): String =
    when (this) {
        is Utfall.Innvilget -> "INNVILGET"
        is Utfall.DelvisInnvilget -> "DELVIS_INNVILGET"
        is Utfall.Avslag -> "AVSLAG"
        is Utfall.Henlagt -> "HENLAGT"
        is Utfall.IkkeAktuell -> "IKKE_AKTUELL"
    }

fun Utfall.ikkeAktuellGrunnDbValue(): String? =
    when (this) {
        is Utfall.IkkeAktuell -> grunn.name
        else -> null
    }

/**
 * Kolonnen er nullbar fordi innvilgelse og ikke aktuell ikke har begrunnelse. Den
 * nullbarheten hører til databasen, ikke til domenet, der begrunnelsen ligger på
 * utfallene som faktisk krever den.
 */
fun Utfall.begrunnelseDbValue(): String? =
    when (this) {
        is Utfall.DelvisInnvilget -> begrunnelse
        is Utfall.Avslag -> begrunnelse
        is Utfall.Henlagt -> begrunnelse
        is Utfall.Innvilget, is Utfall.IkkeAktuell -> null
    }

private fun String.toUtfall(
    innvilgedePerioder: List<Periode>,
    ikkeAktuellGrunn: String?,
    begrunnelse: String?,
): Utfall =
    when (this) {
        "INNVILGET" -> Utfall.Innvilget(innvilgedePerioder = innvilgedePerioder)
        "DELVIS_INNVILGET" ->
            Utfall.DelvisInnvilget(
                innvilgedePerioder = innvilgedePerioder,
                begrunnelse = begrunnelseFraDatabasen(begrunnelse, this),
            )
        "AVSLAG" -> Utfall.Avslag(begrunnelse = begrunnelseFraDatabasen(begrunnelse, this))
        "HENLAGT" -> Utfall.Henlagt(begrunnelse = begrunnelseFraDatabasen(begrunnelse, this))
        "IKKE_AKTUELL" ->
            Utfall.IkkeAktuell(
                grunn =
                    checkNotNull(ikkeAktuellGrunn) {
                        "Behandling med utfall IKKE_AKTUELL mangler ikke_aktuell_grunn i databasen"
                    }.toIkkeAktuellGrunn(),
            )
        else -> throw IllegalStateException("Ukjent utfall lagret i database: $this")
    }

private fun String.toIkkeAktuellGrunn(): IkkeAktuellGrunn =
    IkkeAktuellGrunn.entries.firstOrNull { it.name == this }
        ?: throw IllegalStateException("Ukjent ikke_aktuell_grunn lagret i database: $this")

fun Brevtype.dbValue(): String = name

private fun String.toBrevtype(): Brevtype =
    Brevtype.entries.firstOrNull { it.name == this }
        ?: throw IllegalStateException("Ukjent brevtype lagret i database: $this")

/**
 * Databasen tillater null i begrunnelse fordi innvilgelse og ikke aktuell ikke skal ha
 * noen. Mangler den for et utfall som krever den, er raden inkonsistent.
 */
private fun begrunnelseFraDatabasen(
    begrunnelse: String?,
    utfall: String,
): String =
    checkNotNull(begrunnelse) { "Behandling med utfall $utfall mangler begrunnelse i databasen" }
