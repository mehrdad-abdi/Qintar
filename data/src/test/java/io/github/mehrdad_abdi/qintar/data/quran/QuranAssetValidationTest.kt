package io.github.mehrdad_abdi.qintar.data.quran

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The validation checks of DESIGN.md §7.4, run against the committed `assets/quran.db`
 * opened through Room exactly as the app opens it.
 */
@RunWith(AndroidJUnit4::class)
class QuranAssetValidationTest {

    private lateinit var context: Context
    private lateinit var db: QuranDatabase
    private lateinit var dao: QuranDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = QuranDatabase.create(context)
        dao = db.quranDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun has114SurahsNumbered1To114() = runTest {
        assertEquals((1..114).toList(), dao.surahs().map { it.number })
    }

    @Test
    fun has6236AyahsWithContiguousIds() = runTest {
        assertEquals((1..6236).toList(), dao.ayahStructures().map { it.id })
    }

    @Test
    fun perSurahAyahCountsAndFirstIdsMatchTanzilMetadata() = runTest {
        val surahs = dao.surahs()
        val bySurah = dao.ayahStructures().groupBy { it.surah }
        TanzilSources.surahs.forEach { meta ->
            val surah = surahs[meta.number - 1]
            assertEquals("ayah_count of ${meta.number}", meta.ayahs, surah.ayahCount)
            assertEquals("first_ayah_id of ${meta.number}", meta.start + 1, surah.firstAyahId)
            val ayahs = bySurah.getValue(meta.number)
            assertEquals("ayah numbers of ${meta.number}", (1..meta.ayahs).toList(), ayahs.map { it.number })
            assertEquals(surah.firstAyahId, ayahs.first().id)
        }
        assertEquals(6236, surahs.sumOf { it.ayahCount })
    }

    @Test
    fun pagesCover1To604AndNeverDecrease() = runTest {
        val pages = dao.ayahStructures().map { it.page }
        assertEquals(pages.sorted(), pages)
        assertEquals((1..604).toList(), pages.distinct())
    }

    @Test
    fun juzCover1To30AndNeverDecrease() = runTest {
        val juz = dao.ayahStructures().map { it.juz }
        assertEquals(juz.sorted(), juz)
        assertEquals((1..30).toList(), juz.distinct())
    }

    @Test
    fun hizbQuartersCover1To240AndNeverDecrease() = runTest {
        val quarters = dao.ayahStructures().map { it.hizbQuarter }
        assertEquals(quarters.sorted(), quarters)
        assertEquals((1..240).toList(), quarters.distinct())
    }

    @Test
    fun exactly15SajdaAyahsOfWhichFourAreObligatory() = runTest {
        val sajdas = dao.ayahStructures().filter { it.sajda != AyahEntity.SAJDA_NONE }
        assertEquals(15, sajdas.size)
        val obligatory = sajdas.filter { it.sajda == AyahEntity.SAJDA_OBLIGATORY }
            .map { "${it.surah}:${it.number}" }
        assertEquals(listOf("32:15", "41:38", "53:62", "96:19"), obligatory)
        assertTrue(sajdas.all { it.sajda == AyahEntity.SAJDA_RECOMMENDED || it.sajda == AyahEntity.SAJDA_OBLIGATORY })
    }

    @Test
    fun everySajdaAyahAndOnlyThoseCarryTheSajdaSign() = runTest {
        val sajdaIds = dao.ayahStructures().filter { it.sajda != 0 }.map { it.id }.toSet()
        val withSign = dao.ayahTexts(1, 6236).filter { '۩' in it.text }.map { it.id }.toSet()
        assertEquals(sajdaIds, withSign)
    }

    @Test
    fun noAyahTextBeginsWithTheBasmalaExceptAlFatihahOne() = runTest {
        val basmala = dao.ayah(1)!!.text
        // Spelled out because Tanzil orders shadda before fatha, unlike most keyboards.
        val expected = "بِسْمِ " +
            "ٱللَّهِ " +
            "ٱلرَّحْمَـٰنِ " +
            "ٱلرَّحِيمِ"
        assertEquals(expected, basmala)
        val bismillahWords = basmala.split(' ').take(2).joinToString(" ")
        val startingWithIt = dao.ayahTexts(1, 6236).filter { it.text.startsWith(bismillahWords) }
        assertEquals(listOf(1), startingWithIt.map { it.id })
    }

    @Test
    fun onlySurahs1And9HaveNoSeparateBasmala() = runTest {
        val without = dao.surahs().filterNot { it.hasBasmala }.map { it.number }
        assertEquals(listOf(1, 9), without)
    }

    @Test
    fun codePointsOfTheTextEqualTheCheckedInAllowList() = runTest {
        val allowed = QuranDataFiles.file("allowed_codepoints.txt").readLines()
            .filter { it.isNotBlank() }
            .map { it.substringBefore(' ').removePrefix("U+").toInt(16) }
            .toSortedSet()
        val used = dao.ayahTexts(1, 6236).flatMap { it.text.codePoints().toArray().asList() }.toSortedSet()
        assertEquals(allowed, used)
    }

    @Test
    fun textUsesArabicYehAndKafNotTheirPersianForms() = runTest {
        val texts = dao.ayahTexts(1, 6236).joinToString("") { it.text }
        assertFalse("U+06CC Farsi yeh", 'ی' in texts)
        assertFalse("U+06A9 keheh", 'ک' in texts)
        assertTrue("U+064A Arabic yeh", 'ي' in texts)
        assertTrue("U+0643 Arabic kaf", 'ك' in texts)
    }

    @Test
    fun textHasNoRubElHizbSign() = runTest {
        assertFalse(dao.ayahTexts(1, 6236).any { '۞' in it.text })
    }

    @Test
    fun everyAyahTextIsVerbatimFromTanzil() = runTest {
        assertEquals(TanzilSources.texts, dao.ayahTexts(1, 6236).map { it.text })
    }

    @Test
    fun committedAssetMatchesTheRecordedSha256() {
        val recorded = QuranDataFiles.file("quran.db.sha256").readText().trim().substringBefore(' ')
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(QuranDatabase.ASSET_PATH).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals(recorded, actual)
    }
}
