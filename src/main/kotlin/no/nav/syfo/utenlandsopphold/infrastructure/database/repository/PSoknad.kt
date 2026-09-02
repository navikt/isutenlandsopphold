package no.nav.syfo.utenlandsopphold.infrastructure.database.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.common.journalforing.JournalpostId
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.common.util.configuredJacksonMapper
import no.nav.syfo.utenlandsopphold.domain.Behandlingsutfall
import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import no.nav.syfo.utenlandsopphold.domain.Henleggelse
import no.nav.syfo.utenlandsopphold.domain.Periode
import no.nav.syfo.utenlandsopphold.domain.Soknad
import no.nav.syfo.utenlandsopphold.domain.Utfall
import no.nav.syfo.utenlandsopphold.domain.Utsending
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
        behandlingsutfall: PBehandlingsutfall?,
        vedtakPerioder: List<PVedtakPeriode>,
    ): Soknad =
        Soknad(
            id = uuid,
            eksternId = eksternId,
            personident = personident,
            soktePerioder = soktePerioder.map { it.toPeriode() },
            innsendtTidspunkt = innsendtTidspunkt,
            behandlingsutfall =
                behandlingsutfall?.toBehandlingsutfall(innvilgedePerioder = vedtakPerioder.map { it.toPeriode() }),
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

/**
 * Persistert rad i BEHANDLINGSUTFALL. `type`-kolonnen er en diskriminator som enten er et
 * [Utfall] for et [Vedtak] (INNVILGET/DELVIS_INNVILGET/AVSLAG), eller HENLEGGELSE for en
 * [Henleggelse].
 */
data class PBehandlingsutfall(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val soknadId: Int,
    val type: String,
    val fattetAv: String,
    val fattetTidspunkt: OffsetDateTime,
    val document: String,
    val begrunnelse: String?,
    val journalpostId: String?,
    val journalfortTidspunkt: OffsetDateTime?,
    val distribuertTidspunkt: OffsetDateTime?,
) {
    fun toBehandlingsutfall(innvilgedePerioder: List<Periode>): Behandlingsutfall {
        val utsending =
            Utsending(
                document = document.toDocumentComponents(),
                journalpostId = journalpostId?.let { JournalpostId(it) },
                journalfortTidspunkt = journalfortTidspunkt,
                distribuertTidspunkt = distribuertTidspunkt,
            )

        return if (type == HENLEGGELSE_TYPE) {
            Henleggelse(
                behandlingsutfallId = uuid,
                fattetAv = Navident(fattetAv),
                fattetTidspunkt = fattetTidspunkt,
                begrunnelse =
                    requireNotNull(begrunnelse) {
                        "Henleggelse $uuid mangler begrunnelse i databasen"
                    },
                utsending = utsending,
            )
        } else {
            Vedtak(
                behandlingsutfallId = uuid,
                utfall = type.toUtfall(innvilgedePerioder),
                fattetAv = Navident(fattetAv),
                fattetTidspunkt = fattetTidspunkt,
                innvilgedePerioder = innvilgedePerioder,
                begrunnelse = begrunnelse,
                utsending = utsending,
            )
        }
    }

    companion object {
        const val HENLEGGELSE_TYPE = "HENLEGGELSE"
    }
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

/**
 * Verdien som lagres i BEHANDLINGSUTFALL.type: vedtakets utfall for et [Vedtak], eller
 * HENLEGGELSE for en [Henleggelse].
 */
fun Behandlingsutfall.dbTypeValue(): String =
    when (this) {
        is Vedtak -> utfall.dbValue()
        is Henleggelse -> PBehandlingsutfall.HENLEGGELSE_TYPE
    }

private fun String.toUtfall(innvilgedePerioder: List<Periode>): Utfall =
    when (this) {
        "INNVILGET" -> Utfall.Innvilget
        "DELVIS_INNVILGET" -> Utfall.DelvisInnvilget(innvilgedePerioder)
        "AVSLAG" -> Utfall.Avslag
        else -> throw IllegalStateException("Ukjent utfall lagret i database: $this")
    }
