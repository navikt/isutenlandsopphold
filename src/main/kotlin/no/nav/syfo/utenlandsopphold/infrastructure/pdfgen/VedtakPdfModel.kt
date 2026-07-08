package no.nav.syfo.utenlandsopphold.infrastructure.pdfgen

import no.nav.syfo.utenlandsopphold.domain.DocumentComponent
import java.time.LocalDate

/**
 * Request body sent to ispdfgen for å generere PDF-en for et vedtak om
 * utenlandsopphold. Feltnavnene må matche malen (template) registrert for
 * denne appen i ispdfgen (`isutenlandsopphold/vedtak`).
 *
 * NB: bruker rå `String` for fødselsnummer (ikke value-typen [no.nav.syfo.common.types.ident.Personident])
 * for å unngå overraskelser ved Jackson-serialisering av inline-klasser, i tråd med
 * hvordan andre DTO-er i common-biblioteket (f.eks. Bruker) gjør det.
 */
data class VedtakPdfModel(
    val mottakerFodselsnummer: String,
    val mottakerNavn: String,
    val documentComponents: List<DocumentComponent>,
    val datoSendt: LocalDate = LocalDate.now(),
)
