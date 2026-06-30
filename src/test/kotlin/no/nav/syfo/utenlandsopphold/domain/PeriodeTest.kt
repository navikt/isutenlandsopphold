package no.nav.syfo.utenlandsopphold.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PeriodeTest {
    @Test
    fun `periode med tom foer fom kaster`() {
        assertFailsWith<IllegalArgumentException> {
            Periode(
                fom = LocalDate.of(2026, 1, 10),
                tom = LocalDate.of(2026, 1, 5),
            )
        }
    }

    @Test
    fun `periode med en enkelt dag er gyldig`() {
        val dag = LocalDate.of(2026, 1, 5)

        val periode = Periode(fom = dag, tom = dag)

        assertEquals(dag, periode.fom)
        assertEquals(dag, periode.tom)
    }
}
