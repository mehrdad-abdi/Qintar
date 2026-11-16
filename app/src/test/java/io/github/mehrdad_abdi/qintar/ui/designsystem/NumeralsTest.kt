package io.github.mehrdad_abdi.qintar.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class NumeralsTest {
    @Test
    fun `counts use Persian digits in fa, Arabic-Indic in ar and Latin in en`() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", Numerals.format(1234567890, "fa"))
        assertEquals("١٢٣٤٥٦٧٨٩٠", Numerals.format(1234567890, "ar"))
        assertEquals("1234567890", Numerals.format(1234567890, "en"))
    }

    @Test
    fun `digits inside text are localised and everything else is left alone`() {
        assertEquals("۰۸:۰۰–۱۲:۰۰", Numerals.localize("08:00–12:00", "fa"))
        assertEquals("٤٢ / ١١٠", Numerals.localize("42 / 110", "ar"))
        assertEquals("no digits", Numerals.localize("no digits", "fa"))
    }

    @Test
    fun `the ayah-end marker is U+06DD with Arabic-Indic digits whatever the language`() {
        assertEquals("۝٢٥٥", Numerals.ayahEndMarker(255))
        assertEquals("۝١", Numerals.ayahEndMarker(1))
    }
}
