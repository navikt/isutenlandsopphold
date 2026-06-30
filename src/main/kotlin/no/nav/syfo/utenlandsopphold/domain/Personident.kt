package no.nav.syfo.utenlandsopphold.domain

// Midlertidig stub. Erstattes av Personident-klassen fra delt bibliotek.
@JvmInline
value class Personident(
    val verdi: String,
) {
    init {
        require(verdi.isNotBlank()) { "Personident kan ikke vaere tom" }
    }

    // Unngaar logging av hele identen (PII). Viser kun de to siste sifrene.
    override fun toString(): String = "Personident(****${verdi.takeLast(2)})"
}
