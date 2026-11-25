package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.service.SurahNames
import io.github.mehrdad_abdi.qintar.domain.testing.QuranFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Surah names typed into "Go to" (owner, 2026-11-25): any script, vowels optional. */
class SurahNamesTest {
    /** The fixture's surahs, with the real names (Tanzil) of the ones these tests use. */
    private val surahs: List<SurahInfo> = QuranFixture.index.surahs.map { s ->
        when (s.number) {
            1 -> s.copy(nameAr = "الفاتحة", nameLatin = "Al-Faatiha", nameEn = "The Opening")
            2 -> s.copy(nameAr = "البقرة", nameLatin = "Al-Baqara", nameEn = "The Cow")
            18 -> s.copy(nameAr = "الكهف", nameLatin = "Al-Kahf", nameEn = "The Cave")
            36 -> s.copy(nameAr = "يس", nameLatin = "Yaseen", nameEn = "Yaseen")
            38 -> s.copy(nameAr = "ص", nameLatin = "Saad", nameEn = "The letter Saad")
            67 -> s.copy(nameAr = "الملك", nameLatin = "Al-Mulk", nameEn = "The Sovereignty")
            73 -> s.copy(nameAr = "المزمل", nameLatin = "Al-Muzzammil", nameEn = "The Enshrouded One")
            74 -> s.copy(nameAr = "المدثر", nameLatin = "Al-Muddaththir", nameEn = "The Cloaked One")
            112 -> s.copy(nameAr = "الإخلاص", nameLatin = "Al-Ikhlaas", nameEn = "Sincerity")
            113 -> s.copy(nameAr = "الفلق", nameLatin = "Al-Falaq", nameEn = "The Dawn")
            114 -> s.copy(nameAr = "الناس", nameLatin = "An-Naas", nameEn = "Mankind")
            else -> s.copy(nameAr = "سورة", nameLatin = "Surah", nameEn = "Surah")
        }
    }

    private fun best(query: String) = SurahNames.search(query, surahs).firstOrNull()?.number

    @Test
    fun `English letters, with or without the article, vowels or doubled letters`() {
        assertEquals(36, best("Yaseen"))
        assertEquals(36, best("yasin"))
        assertEquals(36, best("ysn"))
        assertEquals(18, best("kahf"))
        assertEquals(18, best("al-kahf"))
        assertEquals(18, best("Al Kahf"))
        assertEquals(67, best("mulk"))
        assertEquals(73, best("muzammil"))
        assertEquals(114, best("an naas"))
        assertEquals(114, best("nas"))
        assertEquals(112, best("ikhlas"))
    }

    @Test
    fun `Arabic and Persian letters, with or without vowel marks, ي or ی, ك or ک, ة or ه`() {
        assertEquals(36, best("یس"))
        assertEquals(36, best("يس"))
        assertEquals(67, best("ملک"))
        assertEquals(67, best("الملك"))
        assertEquals(67, best("المُلْك"))
        assertEquals(2, best("بقره"))
        assertEquals(18, best("کهف"))
        assertEquals(112, best("اخلاص"))
        assertEquals("one letter: only the surah that is that letter", 38, best("ص"))
    }

    @Test
    fun `the meaning in English is found too`() {
        assertEquals(2, best("cow"))
        assertEquals(113, best("dawn"))
    }

    @Test
    fun `a part of a name suggests every surah it may be, the closest first`() {
        val found = SurahNames.search("mu", surahs).map { it.number }
        assertEquals(listOf(67, 73, 74), found.take(3))
        assertTrue(SurahNames.search("fa", surahs).map { it.number }.containsAll(listOf(1, 113)))
    }

    @Test
    fun `nothing is found for what is no surah`() {
        for (query in listOf("", " ", "p", "j", "x", "qqq", "zzz", "12")) {
            assertEquals(query, emptyList<Int>(), SurahNames.search(query, surahs).map { it.number })
        }
    }
}
