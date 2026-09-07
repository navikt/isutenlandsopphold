package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.domain.Behandlingsutfall
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
        behandlingsutfall: PBehandlingsutfall?,
        behandlingsutfallPerioder: List<PBehandlingsutfallPeriode>,
    ): Soknad =
        Soknad(
            id = uuid,
            eksternId = eksternId,
            personident = personident,
            soktePerioder = soktePerioder.map { it.toPeriode() },
            innsendtTidspunkt = innsendtTidspunkt,
            behandlingsutfall =
                behandlingsutfall?.toBehandlingsutfall(
                    innvilgedePerioder = behandlingsutfallPerioder.map { it.toPeriode() },
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

data class PBehandlingsutfallPeriode(
    val id: Int,
    val behandlingsutfallId: Int,
    val fom: LocalDate,
    val tom: LocalDate,
) {
    fun toPeriode(): Periode = Periode(fom = fom, tom = tom)
}

data class PBehandlingsutfall(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val soknadId: Int,
    val utfall: String,
    val fattetAv: String,
    val fattetTidspunkt: OffsetDateTime,
    val document: String,
    val begrunnelse: String?,
    val journalpostId: String?,
    val journalfortTidspunkt: OffsetDateTime?,
    val distribuertTidspunkt: OffsetDateTime?,
) {
    fun toBehandlingsutfall(innvilgedePerioder: List<Periode>): Behandlingsutfall =
        Behandlingsutfall(
            behandlingsutfallId = uuid,
            utfall = utfall.toUtfall(innvilgedePerioder),
            fattetAv = Navident(fattetAv),
            fattetTidspunkt = fattetTidspunkt,
            innvilgedePerioder = innvilgedePerioder,
            document = document.toDocumentComponents(),
            begrunnelse = begrunnelse,
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
