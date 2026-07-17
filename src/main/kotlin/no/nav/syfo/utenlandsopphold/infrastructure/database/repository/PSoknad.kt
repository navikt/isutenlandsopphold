package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Vedtak
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
        vedtak: PVedtak?,
        vedtakPerioder: List<PVedtakPeriode>,
    ): Soknad =
        Soknad(
            id = uuid,
            eksternId = eksternId,
            personident = personident,
            soktePerioder = soktePerioder.map { it.toPeriode() },
            innsendtTidspunkt = innsendtTidspunkt,
            vedtak = vedtak?.toVedtak(innvilgetePerioder = vedtakPerioder.map { it.toPeriode() }),
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

data class PVedtakPeriode(
    val id: Int,
    val vedtakId: Int,
    val fom: LocalDate,
    val tom: LocalDate,
) {
    fun toPeriode(): Periode = Periode(fom = fom, tom = tom)
}

data class PVedtak(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val soknadId: Int,
    val utfall: String,
    val fattetAv: String,
    val fattetTidspunkt: OffsetDateTime,
    val document: String,
    val journalpostId: String?,
    val journalfortTidspunkt: OffsetDateTime?,
    val distribuertTidspunkt: OffsetDateTime?,
) {
    fun toVedtak(innvilgetePerioder: List<Periode>): Vedtak =
        Vedtak(
            vedtakId = uuid,
            utfall = utfall.toUtfall(innvilgetePerioder),
            fattetAv = Navident(fattetAv),
            fattetTidspunkt = fattetTidspunkt.toInstant(),
            innvilgetePerioder = innvilgetePerioder,
            document = document.toDocumentComponents(),
            journalpostId = journalpostId?.let { JournalpostId(it) },
            journalfortTidspunkt = journalfortTidspunkt?.toInstant(),
            distribuertTidspunkt = distribuertTidspunkt?.toInstant(),
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
    }

private fun String.toUtfall(innvilgetePerioder: List<Periode>): Utfall =
    when (this) {
        "INNVILGET" -> Utfall.Innvilget
        "DELVIS_INNVILGET" -> Utfall.DelvisInnvilget(innvilgetePerioder)
        "AVSLAG" -> Utfall.Avslag
        else -> throw IllegalStateException("Ukjent utfall lagret i database: $this")
    }
