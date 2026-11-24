package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.ui.text.style.BaselineShift
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amiri Quran draws a pause mark high above its line, so on every line but the first it reached into
 * the line above (owner, 2026-11-25, Al-Muzzammil 73:20). The mark and the space it sits on are drawn
 * lower; the text itself is unchanged.
 */
class QuranDisplayTest {
    private val ayah = "مِنَ ٱلَّذِينَ مَعَكَ ۚ وَٱللَّهُ يُقَدِّرُ ٱلَّيْلَ وَٱلنَّهَارَ ۚ عَلِمَ أَن لَّن تُحْصُوهُ فَتَابَ عَلَيْكُمْ ۖ فَٱقْرَءُوا۟"

    @Test
    fun `every pause mark is lowered with the space it sits on, and nothing else`() {
        val shown = AyahTextFormatter.format(ayah, 20, breakAtPauseMarks = true)
        val display = quranDisplay(shown)

        assertEquals("the text is not changed", shown, display.text)
        val lowered = display.spanStyles.filter { it.item.baselineShift != null }
        val marks = shown.indices.filter { shown[it] in 'ۖ'..'ۜ' }
        assertEquals(3, marks.size)
        assertEquals(marks.map { (it - 1)..it }, lowered.map { it.start until it.end })
        assertTrue(lowered.all { it.item.baselineShift!!.multiplier < 0f })
    }

    @Test
    fun `text without pause marks is drawn as it is`() {
        assertTrue(quranDisplay("بِسْمِ ٱللَّهِ").spanStyles.isEmpty())
    }
}
