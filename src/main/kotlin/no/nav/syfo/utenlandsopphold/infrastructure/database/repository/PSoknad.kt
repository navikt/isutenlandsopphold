package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.domain.Behandling
import no.nav.syfo.utenlandsopphold.domain.Dokument
import no.nav.syfo.utenlandsopphold.domain.Dokumenttype
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
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
        dokument: PDokument?,
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
                    dokument =
                        checkNotNull(dokument) {
                            "Behandling ${behandling.uuid} mangler dokument"
                        },
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
) {
    fun toBehandling(
        innvilgedePerioder: List<Periode>,
        dokument: PDokument,
    ): Behandling =
        Behandling(
            behandlingId = uuid,
            utfall = utfall.toUtfall(innvilgedePerioder),
            behandletAv = Navident(behandletAv),
            behandletTidspunkt = behandletTidspunkt,
            innvilgedePerioder = innvilgedePerioder,
            begrunnelse = begrunnelse,
            dokument = dokument.toDokument(),
        )
}

data class PDokument(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val behandlingId: Int,
    val dokumenttype: String,
    val innhold: String,
    val journalpostId: String?,
    val journalfortTidspunkt: OffsetDateTime?,
    val distribuertTidspunkt: OffsetDateTime?,
) {
    fun toDokument(): Dokument =
        Dokument(
            brevtype = dokumenttype.toDokumenttype(),
            document = innhold.toDocumentComponents(),
            brevId = uuid,
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
        Utfall.Innvilget -> "INNVILGET"
        is Utfall.DelvisInnvilget -> "DELVIS_INNVILGET"
        Utfall.Avslag -> "AVSLAG"
        Utfall.Henlagt -> "HENLAGT"
    }

private fun String.toUtfall(innvilgedePerioder: List<Periode>): Utfall =
    when (this) {
        "INNVILGET" -> Utfall.Innvilget
        "DELVIS_INNVILGET" -> Utfall.DelvisInnvilget(innvilgedePerioder)
        "AVSLAG" -> Utfall.Avslag
        "HENLAGT" -> Utfall.Henlagt
        else -> throw IllegalStateException("Ukjent utfall lagret i database: $this")
    }

fun Dokumenttype.dbValue(): String = name

private fun String.toDokumenttype(): Dokumenttype =
    Dokumenttype.entries.firstOrNull { it.name == this }
        ?: throw IllegalStateException("Ukjent dokumenttype lagret i database: $this")
