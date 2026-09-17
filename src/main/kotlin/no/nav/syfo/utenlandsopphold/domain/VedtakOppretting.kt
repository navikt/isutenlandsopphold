package no.nav.syfo.utenlandsopphold.domain

/**
 * Vedtaket en saksbehandler ber om.
 *
 * Skilt fra [Utfall.Vedtak] fordi ved full innvilgelse oppgir ikke saksbehandleren innvilgede perioder,
 * men innvilgede perioder er en del av [Utfall.Innvilget].
 */
sealed interface VedtakOppretting {
    data object Innvilgelse : VedtakOppretting

    data class DelvisInnvilgelse(
        val innvilgedePerioder: List<Periode>,
        val begrunnelse: String,
    ) : VedtakOppretting

    data class Avslag(
        val begrunnelse: String,
    ) : VedtakOppretting

    companion object {
        /**
         * Oversetter utfallsverdien fra API-et. HENLAGT og IKKE_AKTUELL mangler med
         * vilje: en henleggelse er ingen vedtak, og ikke aktuell settes i sitt eget
         * endepunkt.
         */
        fun from(
            utfall: String,
            innvilgedePerioder: List<Periode>,
            begrunnelse: String?,
        ): VedtakOppretting =
            when (utfall) {
                "INNVILGET" -> {
                    require(begrunnelse == null) { "Innvilgelse skal ikke ha begrunnelse" }
                    Innvilgelse
                }
                "DELVIS_INNVILGET" ->
                    DelvisInnvilgelse(
                        innvilgedePerioder = innvilgedePerioder,
                        begrunnelse = paakrevdBegrunnelse(begrunnelse, "delvis innvilgelse"),
                    )
                "AVSLAG" -> {
                    require(innvilgedePerioder.isEmpty()) { "innvilgedePerioder skal være tom ved avslag" }
                    Avslag(begrunnelse = paakrevdBegrunnelse(begrunnelse, "avslag"))
                }
                else -> throw IllegalArgumentException("Invalid utfall: $utfall")
            }
    }
}
