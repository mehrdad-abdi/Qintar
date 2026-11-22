package io.github.mehrdad_abdi.qintar.data.quran

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What [QuranDao] answers for meaningful questions about the Mushaf. */
@RunWith(AndroidJUnit4::class)
class QuranDaoTest {

    private lateinit var db: QuranDatabase
    private lateinit var dao: QuranDao

    @Before
    fun setUp() {
        db = QuranDatabase.create(ApplicationProvider.getApplicationContext())
        dao = db.quranDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun ref(id: Int) = dao.ayah(id)!!.let { "${it.surah}:${it.number}" }

    @Test
    fun page1ContainsExactlyTheSevenAyahsOfAlFatihah() = runTest {
        val page = dao.ayahTextsOnPage(1)
        assertEquals((1..7).toList(), page.map { it.id })
        assertEquals(listOf("1:1", "1:2", "1:3", "1:4", "1:5", "1:6", "1:7"), page.map { ref(it.id) })
    }

    @Test
    fun page2StartsAlBaqarah() = runTest {
        val page = dao.ayahTextsOnPage(2)
        assertEquals("2:1", ref(page.first().id))
        assertEquals("الٓمٓ", page.first().text)
    }

    @Test
    fun ayatAlKursiIsOnPage42InJuz3() = runTest {
        val structure = dao.ayahStructures().single { it.surah == 2 && it.number == 255 }
        assertEquals(42, structure.page)
        assertEquals(3, structure.juz)
        assertTrue(dao.ayahTextsOnPage(42).any { it.id == structure.id })
    }

    @Test
    fun lastPageHoldsTheLastThreeSurahs() = runTest {
        val page = dao.ayahTextsOnPage(604)
        val surahs = page.map { dao.ayah(it.id)!!.surah }.distinct()
        assertEquals(listOf(112, 113, 114), surahs)
        assertEquals(6236, page.last().id)
    }

    @Test
    fun pagesOutsideTheMushafAreEmpty() = runTest {
        assertTrue(dao.ayahTextsOnPage(0).isEmpty())
        assertTrue(dao.ayahTextsOnPage(605).isEmpty())
    }

    @Test
    fun juzBoundariesFallWhereTheMushafPutsThem() = runTest {
        val structures = dao.ayahStructures()
        fun juzOf(s: Int, n: Int) = structures.single { it.surah == s && it.number == n }.juz
        assertEquals(1, juzOf(2, 141))
        assertEquals(2, juzOf(2, 142))
        assertEquals(29, juzOf(77, 50))
        assertEquals(30, juzOf(78, 1))
        assertEquals(30, juzOf(114, 6))
    }

    @Test
    fun hizbQuarterBoundariesStartAtTheirFirstAyah() = runTest {
        val structures = dao.ayahStructures()
        fun quarterOf(s: Int, n: Int) = structures.single { it.surah == s && it.number == n }.hizbQuarter
        assertEquals(1, quarterOf(2, 25))
        assertEquals(2, quarterOf(2, 26))
        assertEquals(240, quarterOf(100, 9))
        assertEquals(239, quarterOf(100, 8))
    }

    @Test
    fun idRangeIsInclusiveAndOrdered() = runTest {
        val alKawthar = dao.surahs().single { it.number == 108 }
        val texts = dao.ayahTexts(alKawthar.firstAyahId, alKawthar.firstAyahId + alKawthar.ayahCount - 1)
        assertEquals(3, texts.size)
        assertEquals((alKawthar.firstAyahId until alKawthar.firstAyahId + 3).toList(), texts.map { it.id })
    }

    @Test
    fun surah9StartsWithoutBasmala() = runTest {
        val atTawbah = dao.surahs().single { it.number == 9 }
        assertEquals(false, atTawbah.hasBasmala)
        val first = dao.ayah(atTawbah.firstAyahId)!!
        assertEquals(9 to 1, first.surah to first.number)
        // بَرَآءَةٌ as Tanzil spells it: alef followed by a combining maddah, not U+0622.
        assertTrue(first.text.startsWith("بَرَآءَةٌ "))
    }

    @Test
    fun surahNamesAndRevelationComeFromTheMetadata() = runTest {
        val surahs = dao.surahs()
        val fatihah = surahs.first()
        assertEquals("الفاتحة", fatihah.nameAr)
        assertEquals("Al-Faatiha", fatihah.nameLatin)
        assertEquals("The Opening", fatihah.nameEn)
        assertEquals("MECCAN", fatihah.revelation)
        assertEquals("MEDINAN", surahs[1].revelation)
        assertTrue(surahs.none { it.nameAr.startsWith("سورة") })
        assertTrue(surahs.all { it.revelation == "MECCAN" || it.revelation == "MEDINAN" })
    }

    @Test
    fun alAlaqNineteenIsAnObligatorySajdaEndingWithTheSign() = runTest {
        val id = dao.surahs().single { it.number == 96 }.firstAyahId + 18
        val ayah = dao.ayah(id)!!
        assertEquals(96 to 19, ayah.surah to ayah.number)
        assertEquals(AyahEntity.SAJDA_OBLIGATORY, ayah.sajda)
        assertTrue(ayah.text.endsWith(" ۩"))
    }

    @Test
    fun anIdOutsideTheMushafHasNoAyah() = runTest {
        assertNull(dao.ayah(0))
        assertNull(dao.ayah(6237))
    }
}
