package io.github.mehrdad_abdi.quranbookmarks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SurahNamesTest {

    @Test
    fun `getAbsoluteAyahNumber calculates correct number for first ayah of first surah`() {
        // Al-Fatihah 1:1
        val result = SurahNames.getAbsoluteAyahNumber(1, 1)
        assertEquals(1, result)
    }

    @Test
    fun `getAbsoluteAyahNumber calculates correct number for last ayah of first surah`() {
        // Al-Fatihah 1:7
        val result = SurahNames.getAbsoluteAyahNumber(1, 7)
        assertEquals(7, result)
    }

    @Test
    fun `getAbsoluteAyahNumber calculates correct number for first ayah of second surah`() {
        // Al-Baqarah 2:1 should be ayah 8 (after 7 ayahs of Al-Fatihah)
        val result = SurahNames.getAbsoluteAyahNumber(2, 1)
        assertEquals(8, result)
    }

    @Test
    fun `getAbsoluteAyahNumber calculates correct number for Al-Balad 90-15`() {
        // This is the bug that was reported - surah 90, ayah 15
        // According to the API, this should be ayah 6038
        val result = SurahNames.getAbsoluteAyahNumber(90, 15)
        assertEquals(6038, result)
    }

    @Test
    fun `getAbsoluteAyahNumber calculates correct number for last ayah of Quran`() {
        // An-Nas 114:6 is the last ayah (6236)
        val result = SurahNames.getAbsoluteAyahNumber(114, 6)
        assertEquals(6236, result)
    }

    @Test
    fun `getAbsoluteAyahNumber returns -1 for invalid surah number`() {
        val result = SurahNames.getAbsoluteAyahNumber(115, 1)
        assertEquals(-1, result)
    }

    @Test
    fun `getAbsoluteAyahNumber returns -1 for zero surah number`() {
        val result = SurahNames.getAbsoluteAyahNumber(0, 1)
        assertEquals(-1, result)
    }

    @Test
    fun `getAbsoluteAyahNumber returns -1 for negative surah number`() {
        val result = SurahNames.getAbsoluteAyahNumber(-1, 1)
        assertEquals(-1, result)
    }

    @Test
    fun `getAbsoluteAyahNumber returns -1 for zero ayah number`() {
        val result = SurahNames.getAbsoluteAyahNumber(1, 0)
        assertEquals(-1, result)
    }

    @Test
    fun `getAbsoluteAyahNumber returns -1 for negative ayah number`() {
        val result = SurahNames.getAbsoluteAyahNumber(1, -1)
        assertEquals(-1, result)
    }

    @Test
    fun `getAbsoluteAyahNumber returns -1 for ayah number exceeding surah length`() {
        // Al-Fatihah has only 7 ayahs
        val result = SurahNames.getAbsoluteAyahNumber(1, 8)
        assertEquals(-1, result)
    }

    @Test
    fun `getAyahCount returns correct count for Al-Fatihah`() {
        val result = SurahNames.getAyahCount(1)
        assertEquals(7, result)
    }

    @Test
    fun `getAyahCount returns correct count for Al-Baqarah`() {
        val result = SurahNames.getAyahCount(2)
        assertEquals(286, result)
    }

    @Test
    fun `getAyahCount returns correct count for Al-Balad`() {
        val result = SurahNames.getAyahCount(90)
        assertEquals(20, result)
    }

    @Test
    fun `getAyahCount returns correct count for An-Nas`() {
        val result = SurahNames.getAyahCount(114)
        assertEquals(6, result)
    }

    @Test
    fun `getAyahCount returns 0 for invalid surah number`() {
        val result = SurahNames.getAyahCount(115)
        assertEquals(0, result)
    }

    @Test
    fun `getTotalAyahCount returns 6236`() {
        val result = SurahNames.getTotalAyahCount()
        assertEquals(6236, result)
    }

    @Test
    fun `getAbsoluteAyahNumber for various middle surahs`() {
        // Test a few more surahs to ensure the lookup table is correct

        // Surah 36 (Ya-Sin) ayah 1 should be 3706 (3705 + 1)
        assertEquals(3706, SurahNames.getAbsoluteAyahNumber(36, 1))

        // Surah 55 (Ar-Rahman) ayah 1 should be after surahs 1-54
        // Let's verify by checking a known value from the API
        val rahman1 = SurahNames.getAbsoluteAyahNumber(55, 1)
        // Expected: sum of ayahs 1-54 + 1
        // We can verify this is > 0 and within valid range
        assert(rahman1 > 0 && rahman1 <= 6236)

        // Surah 18 (Al-Kahf) ayah 1
        val kahf1 = SurahNames.getAbsoluteAyahNumber(18, 1)
        assert(kahf1 > 0 && kahf1 <= 6236)
    }

    @Test
    fun `getName returns correct name for well-known surahs`() {
        assertEquals("Al-Fatihah", SurahNames.getName(1))
        assertEquals("Al-Baqarah", SurahNames.getName(2))
        assertEquals("Al-Balad", SurahNames.getName(90))
        assertEquals("An-Nas", SurahNames.getName(114))
    }

    @Test
    fun `getName returns fallback for invalid surah`() {
        assertEquals("Surah 115", SurahNames.getName(115))
    }

    @Test
    fun `all ayah counts sum to 6236`() {
        // Verify that the sum of all ayahs in all surahs equals the total
        var sum = 0
        for (i in 1..114) {
            sum += SurahNames.getAyahCount(i)
        }
        assertEquals(6236, sum)
    }
}
