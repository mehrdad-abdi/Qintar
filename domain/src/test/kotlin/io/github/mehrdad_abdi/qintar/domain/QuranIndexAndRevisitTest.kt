package io.github.mehrdad_abdi.qintar.domain

import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.quran.ArrayQuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.PassageSummary
import io.github.mehrdad_abdi.qintar.domain.quran.ayahId
import io.github.mehrdad_abdi.qintar.domain.quran.pagesRange
import io.github.mehrdad_abdi.qintar.domain.quran.summary
import io.github.mehrdad_abdi.qintar.domain.quran.surahsRange
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.domain.testing.QuranFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** §7.4 the structural index, and §4.8 recency and Revisit. */
class QuranIndexAndRevisitTest {
    private val app = DomainFixture()
    private val index = app.index

    // ---- the index ----

    @Test
    fun `references and global ayah numbers convert both ways for all 6236 ayahs`() {
        var count = 0
        for (surah in index.surahs) {
            for (number in 1..surah.ayahCount) {
                val id = index.ayahId(AyahRef(surah.number, number))!!
                assertEquals(AyahRef(surah.number, number), index.ref(id))
                count++
                assertEquals("ids follow mushaf order with no gaps", count, id.value)
            }
        }
        assertEquals(Quran.AYAH_COUNT, count)
    }

    @Test
    fun `known landmarks have their global numbers`() {
        assertEquals(AyahId(1), index.ayahId(1, 1))
        assertEquals(AyahId(8), index.ayahId(2, 1))
        assertEquals(AyahId(262), index.ayahId(2, 255))
        assertEquals(AyahId(6236), index.ayahId(114, 6))
        assertEquals(AyahRef(114, 6), index.ref(AyahId.LAST))
    }

    @Test
    fun `a reference to an ayah its surah does not have resolves to nothing`() {
        assertNull(index.ayahId(1, 8))
        assertNull(index.ayahId(2, 287))
        assertNull(index.ayahId(115, 1))
        assertNull(index.ayahId(0, 1))
        assertNull(index.ayahId(2, 0))
    }

    @Test
    fun `basmala is never an ayah, so it can never be counted by tap or by audio`() {
        // A reading is recorded against an AyahId, and the basmala row has none: surahs with a basmala
        // still begin at their own ayah 1 and the ayah total stays 6236.
        assertEquals(Quran.AYAH_COUNT, index.surahs.sumOf { it.ayahCount })
        assertEquals("only al-Fatiha (where it is ayah 1) and at-Tawbah have no basmala row",
            listOf(1, 9), index.surahs.filterNot { it.hasBasmala }.map { it.number })
        for (surah in index.surahs) {
            assertEquals(AyahRef(surah.number, 1), index.ref(surah.firstAyahId))
        }
    }

    @Test
    fun `pages, juz and hizb quarters partition the Quran in order`() {
        fun assertPartition(count: Int, range: (Int) -> AyahRange, unitOf: (AyahId) -> Int) {
            var next = 1
            for (unit in 1..count) {
                val r = range(unit)
                assertEquals("unit $unit starts where the previous ended", next, r.start.value)
                assertEquals(unit, unitOf(r.start))
                assertEquals(unit, unitOf(r.end))
                next = r.end.value + 1
            }
            assertEquals(Quran.AYAH_COUNT + 1, next)
        }
        assertPartition(Quran.PAGE_COUNT, index::pageRange, index::pageOf)
        assertPartition(Quran.JUZ_COUNT, index::juzRange, index::juzOf)
        assertPartition(Quran.HIZB_QUARTER_COUNT, index::hizbQuarterRange, index::hizbQuarterOf)
        assertEquals(Quran.AYAH_COUNT, (1..Quran.PAGE_COUNT).sumOf(index::ayahsOnPage))
    }

    @Test
    fun `there are fifteen sajda ayahs, each with its type`() {
        val sajdas = AyahRange.ALL.filter { index.sajdaOf(it) != SajdaType.NONE }

        assertEquals(15, sajdas.size)
        assertEquals(SajdaType.OBLIGATORY, index.sajdaOf(app.ayah(96, 19)))
        assertEquals(SajdaType.RECOMMENDED, index.sajdaOf(app.ayah(7, 206)))
        assertEquals(SajdaType.NONE, index.sajdaOf(app.ayah(2, 255)))
    }

    @Test
    fun `a passage is summarised by its ayah count and page span`() {
        val kahf = index.surahRange(18)
        assertEquals(PassageSummary(110, index.pageOf(kahf.start), index.pageOf(kahf.end)), index.summary(kahf))
        assertEquals(7 + 286, index.surahsRange(1, 2).size)
        assertEquals(index.ayahsOnPage(2) + index.ayahsOnPage(3), index.pagesRange(2, 3).size)
    }

    @Test
    fun `numbers outside the mushaf are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { index.pageRange(0) }
        assertThrows(IllegalArgumentException::class.java) { index.pageRange(605) }
        assertThrows(IllegalArgumentException::class.java) { index.juzRange(31) }
        assertThrows(IllegalArgumentException::class.java) { index.surah(115) }
        assertThrows(IllegalArgumentException::class.java) { AyahId(0) }
        assertThrows(IllegalArgumentException::class.java) { AyahId(6237) }
        assertThrows(IllegalArgumentException::class.java) { AyahRange.of(10, 9) }
    }

    @Test
    fun `an index refuses data that would give wrong answers`() {
        val rows = QuranFixture.ayahRows
        val surahs = QuranFixture.surahs

        assertThrows("a missing ayah", IllegalArgumentException::class.java) { ArrayQuranIndex(rows.dropLast(1), surahs) }
        assertThrows("a page that goes backwards", IllegalArgumentException::class.java) {
            ArrayQuranIndex(rows.map { if (it.id == 3000) it.copy(page = it.page - 1) else it }, surahs)
        }
        assertThrows("an ayah numbered against its surah", IllegalArgumentException::class.java) {
            ArrayQuranIndex(rows.map { if (it.id == 8) it.copy(number = 2) else it }, surahs)
        }
        assertThrows("a surah count that disagrees with the rows", IllegalArgumentException::class.java) {
            ArrayQuranIndex(rows, surahs.map { if (it.number == 2) it.copy(ayahCount = 285) else it })
        }
    }

    // ---- recency ----

    @Test
    fun `an ayah knows when it was last read and how many times, across every context`() = runTest {
        val kursi = app.ayah(2, 255)
        val khatm = ReadContext.Khatm(app.khatms.start("Khatm 1", startFrom = kursi).id)

        assertNull("never read", app.revisit.observeAyah(kursi).first())

        app.tracker.mark(kursi, khatm, ReadMethod.TAP)
        val firstDay = app.clock.today()
        app.clock.advanceDays(3)
        app.tracker.mark(kursi, ReadContext.Bookmark(1), ReadMethod.AUDIO)
        app.tracker.mark(kursi, ReadContext.Browse, ReadMethod.TAP)

        val recency = app.revisit.observeAyah(kursi).first()!!
        assertEquals(firstDay.plusDays(3), recency.lastRead)
        assertEquals(3, recency.timesRead)
    }

    @Test
    fun `last read is the latest reading day, not the latest entry`() = runTest {
        val kursi = app.ayah(2, 255)
        val today = app.clock.today()
        app.tracker.mark(kursi, ReadContext.Browse, ReadMethod.TAP)
        // Logged afterwards, for a week ago.
        app.tracker.markRange(AyahRange.single(kursi), ReadContext.Log, ReadMethod.MANUAL, RangeMode.ADD, day = today.minusDays(7))

        assertEquals(today, app.revisit.observeAyah(kursi).first()!!.lastRead)
    }

    @Test
    fun `tallies do not affect recency`() = runTest {
        app.tracker.addTally(app.clock.today(), 500, null)

        val revisit = app.revisit.observe().first()
        assertTrue(revisit.readLongAgo.isEmpty())
        assertEquals(Quran.PAGE_COUNT, revisit.notReadYet.size)
    }

    // ---- revisit ----

    private suspend fun readPage(page: Int) =
        app.tracker.markRange(index.pageRange(page), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)

    @Test
    fun `with nothing read every page is not read yet, in mushaf order`() = runTest {
        val revisit = app.revisit.observe().first()

        assertEquals((1..Quran.PAGE_COUNT).toList(), revisit.notReadYet.map { it.page })
        assertTrue(revisit.readLongAgo.isEmpty())
        assertEquals(0f, revisit.pages.first().coverage)
    }

    @Test
    fun `fully read pages are listed oldest first`() = runTest {
        val start = app.clock.today()
        readPage(50)
        app.clock.advanceDays(10)
        readPage(7)
        app.clock.advanceDays(10)
        readPage(300)

        val longAgo = app.revisit.observe().first().readLongAgo

        assertEquals(listOf(50, 7, 300), longAgo.map { it.page })
        assertEquals(listOf(start, start.plusDays(10), start.plusDays(20)), longAgo.map { it.oldest })
    }

    @Test
    fun `a page is as old as its least recently read ayah`() = runTest {
        val start = app.clock.today()
        readPage(50)
        readPage(60)
        app.clock.advanceDays(30)
        // Re-read all of page 60, but only the first ayah of page 50.
        readPage(60)
        app.tracker.mark(index.pageRange(50).start, ReadContext.Browse, ReadMethod.TAP)

        val longAgo = app.revisit.observe().first().readLongAgo

        assertEquals("page 50 still has ayahs last read a month ago", listOf(50, 60), longAgo.map { it.page })
        assertEquals(start, longAgo[0].oldest)
        assertEquals(start.plusDays(30), longAgo[0].newest)
        assertEquals(start.plusDays(30), longAgo[1].oldest)
    }

    @Test
    fun `a page with any never-read ayah is under not read yet, with how many remain`() = runTest {
        val page = index.pageRange(42)
        val allButThree = AyahRange(page.start, AyahId(page.end.value - 3))
        app.tracker.markRange(allButThree, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)

        val revisit = app.revisit.observe().first()
        val row = revisit.notReadYet.first { it.page == 42 }

        assertEquals(3, row.neverRead)
        assertEquals(page.size, row.ayahCount)
        assertEquals((page.size - 3).toFloat() / page.size, row.coverage, 1e-6f)
        assertNull("never-read is older than everything", row.oldest)
        assertFalse(revisit.readLongAgo.any { it.page == 42 })
    }

    @Test
    fun `each page row names the surahs on it`() = runTest {
        val revisit = app.revisit.observe().first()
        // Page 1 of the fixture holds all of al-Fatiha and the opening of al-Baqarah.
        assertEquals(listOf(1, 2), revisit.pages[0].surahs)
        assertEquals(index.pageRange(1), revisit.pages[0].range)
        assertEquals(Quran.PAGE_COUNT, revisit.pages.size)
    }

    @Test
    fun `revisit updates when a page is read`() = runTest {
        app.revisit.observe().test {
            assertTrue(awaitItem().readLongAgo.isEmpty())
            readPage(9)
            assertEquals(listOf(9), awaitItem().readLongAgo.map { it.page })
        }
    }

    @Test
    fun `surah coverage is the share of each surah's ayahs ever read`() = runTest {
        app.tracker.markRange(index.surahRange(112), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
        app.tracker.markRange(AyahRange(app.ayah(2, 1), app.ayah(2, 143)), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
        // A second reading of the same ayahs does not cover more.
        app.tracker.markRange(AyahRange(app.ayah(2, 1), app.ayah(2, 143)), ReadContext.Random, ReadMethod.BULK, RangeMode.ADD)

        val coverage = app.revisit.observeSurahCoverage().first()

        assertEquals(1f, coverage.getValue(112))
        assertEquals(0.5f, coverage.getValue(2))
        assertEquals(0f, coverage.getValue(1))
        assertEquals(114, coverage.size)
    }
}
