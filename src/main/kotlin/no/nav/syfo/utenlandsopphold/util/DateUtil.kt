package no.nav.syfo.utenlandsopphold.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

val osloZone: ZoneId = ZoneId.of("Europe/Oslo")

/**
 * Konverterer til lokal Oslo-tid uten offset/zone-info. Brukes når tidspunkt skal ut i
 * DTO-er mot frontend, som ønsker et enkelt, lesbart klokkeslett fremfor UTC-offset.
 */
fun OffsetDateTime.toLocalDateTimeOslo(): LocalDateTime = atZoneSameInstant(osloZone).toLocalDateTime()
