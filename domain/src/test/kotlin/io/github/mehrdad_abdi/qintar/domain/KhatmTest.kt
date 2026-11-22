package io.github.mehrdad_abdi.qintar.domain

import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.service.KhatmSnapshot
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** §4.6 and the khatm rules of §4.2.1: coverage, position, the sequence dialog, statistics, completion. */
class KhatmTest {
    private val app = DomainFixture()
    private val today get() = app.clock.today()

    private suspend fun startKhatm(name: String = "Khatm 1", from: AyahId? = null, dailyPages: Float? = null) =
        app.khatms.start(name, from, dailyPages).id

    private suspend fun snapshot(id: Long): KhatmSnapshot = app.khatmProgress.snapshot(id)!!

    private suspend fun tap(khatm: Long, ayah: Int) = app.tracker.mark(AyahId(ayah), ReadContext.Khatm(khatm), ReadMethod.TAP)

    private suspend fun fill(khatm: Long, from: Int, to: Int) =
        app.tracker.markRange(AyahRange.of(from, to), ReadContext.Khatm(khatm), ReadMethod.BULK, RangeMode.FILL)

    // ---- coverage and position ----

    @Test
    fun `a new khatm starts at the first ayah with nothing covered`() = runTest {
        val k = snapshot(startKhatm())

        assertEquals(0, k.coveredAyahs)
        assertEquals(AyahId(1), k.resume)
        assertEquals(AyahId(1), k.firstUnread)
        assertFalse(k.firstUnreadDiffersFromResume)
        assertEquals(today, k.khatm.startedDay)
        assertNull("no goal, no bar", k.todayGoalFraction)
        assertNull("nothing read and no goal: no pace to project from", k.projectedFinish)
    }

    @Test
    fun `khatm marks from yesterday are still shown today`() = runTest {
        val id = startKhatm()
        fill(id, 1, 20)
        app.clock.advanceDays(1)

        val k = snapshot(id)
        assertEquals("coverage persists across days", 20, k.coveredAyahs)
        assertEquals(AyahId(21), k.resume)
        assertEquals("but the new day's own count starts at zero", 0, k.todayAyahs)
        assertEquals(0, app.totals.today())
        assertEquals(20, app.log.distinctAyahs(ReadContext.Khatm(id), AyahRange.ALL).size)
    }

    @Test
    fun `the resume position follows the most recently recorded mark, not the furthest one`() = runTest {
        val id = startKhatm()
        fill(id, 1, 50)
        app.clock.advanceMinutes(1)
        tap(id, 10)

        assertEquals("a re-reading of ayah 10 was the last thing marked", AyahId(11), snapshot(id).resume)
    }

    @Test
    fun `only marking moves the resume position`() = runTest {
        val id = startKhatm()
        tap(id, 1)
        val before = snapshot(id).resume

        // Reading elsewhere, in another context, is not a mark in this khatm.
        app.tracker.mark(AyahId(3000), ReadContext.Browse, ReadMethod.TAP)

        assertEquals(before, snapshot(id).resume)
        assertEquals(AyahId(2), before)
    }

    @Test
    fun `first unread is the earliest gap and is offered when it differs from the resume position`() = runTest {
        val id = startKhatm()
        fill(id, 1, 10)
        tap(id, 500)

        val k = snapshot(id)
        assertEquals(AyahId(501), k.resume)
        assertEquals(AyahId(11), k.firstUnread)
        assertTrue(k.firstUnreadDiffersFromResume)
    }

    @Test
    fun `a khatm started from a later position counts everything before it as covered without any reading`() = runTest {
        val start = app.index.juzRange(12).start
        val id = startKhatm(from = start)

        val k = snapshot(id)
        assertEquals(start.value - 1, k.coveredAyahs)
        assertEquals(start, k.resume)
        assertEquals(start, k.firstUnread)
        assertEquals("assumed coverage is not reading: no day count is invented", 0, app.totals.today())
        assertEquals(12, k.currentJuz)
    }

    @Test
    fun `reading an ayah twice in a khatm covers it once and counts twice today`() = runTest {
        val id = startKhatm()
        tap(id, 1)
        tap(id, 1)

        val k = snapshot(id)
        assertEquals(1, k.coveredAyahs)
        assertEquals(2, k.todayAyahs)
        assertEquals(2, app.totals.today())
    }

    @Test
    fun `two khatms keep separate coverage of the same ayahs`() = runTest {
        val personal = startKhatm("Personal")
        val ramadan = startKhatm("Ramadan")
        fill(personal, 1, 30)
        fill(ramadan, 1, 7)

        assertEquals(30, snapshot(personal).coveredAyahs)
        assertEquals(7, snapshot(ramadan).coveredAyahs)
    }

    @Test
    fun `active khatms are listed by most recent activity and the first is the primary`() = runTest {
        val personal = startKhatm("Personal")
        val ramadan = startKhatm("Ramadan")
        tap(ramadan, 1)
        app.clock.advanceMinutes(10)
        tap(personal, 1)

        assertEquals(listOf("Personal", "Ramadan"), app.khatmProgress.observeActive().first().map { it.khatm.name })

        app.clock.advanceMinutes(10)
        tap(ramadan, 2)
        assertEquals(listOf("Ramadan", "Personal"), app.khatmProgress.observeActive().first().map { it.khatm.name })
    }

    @Test
    fun `a khatm's snapshot updates as ayahs are marked`() = runTest {
        val id = startKhatm()
        app.khatmProgress.observe(id).test {
            assertEquals(0, awaitItem()!!.coveredAyahs)
            tap(id, 1)
            assertEquals(1, expectMostRecentItem()!!.coveredAyahs)
            app.khatms.delete(id)
            assertNull(expectMostRecentItem())
        }
    }

    // ---- the sequence rule (R-TRK-9) ----

    @Test
    fun `a khatm tick that skips unread ayahs writes nothing until the user chooses`() = runTest {
        val id = startKhatm()
        fill(id, 1, 11)
        val eventsBefore = app.log.events.size

        val gap = app.tracker.khatmGap(id, AyahId(20))

        assertEquals("the dialog offers ayahs 12 to 20", AyahRange.of(12, 20), gap)
        assertEquals(9, gap!!.size)
        assertEquals("asking wrote nothing", eventsBefore, app.log.events.size)
        assertEquals(11, snapshot(id).coveredAyahs)
    }

    @Test
    fun `choosing to mark all fills exactly the gap up to the tapped ayah`() = runTest {
        val id = startKhatm()
        fill(id, 1, 11)

        val gap = app.tracker.khatmGap(id, AyahId(20))!!
        val batch = app.tracker.markRange(gap, ReadContext.Khatm(id), ReadMethod.BULK, RangeMode.FILL)

        assertEquals(9, batch.added)
        assertEquals(20, snapshot(id).coveredAyahs)
        assertEquals(AyahId(21), snapshot(id).resume)
    }

    @Test
    fun `after only this ayah is chosen once, the following ayahs tick silently again`() = runTest {
        val id = startKhatm()
        assertNotNull("jumping to ayah 2000 in a fresh khatm asks", app.tracker.khatmGap(id, AyahId(2000)))

        tap(id, 2000)

        assertNull("2001 follows a covered ayah", app.tracker.khatmGap(id, AyahId(2001)))
        tap(id, 2001)
        assertNull(app.tracker.khatmGap(id, AyahId(2002)))
        assertEquals(AyahRange.of(2002, 2005), app.tracker.khatmGap(id, AyahId(2005)))
    }

    @Test
    fun `an in-sequence tap, a re-reading and the very first ayah never ask`() = runTest {
        val id = startKhatm()
        assertNull("ayah 1 has nothing before it", app.tracker.khatmGap(id, AyahId(1)))
        fill(id, 1, 5)

        assertNull("next in sequence", app.tracker.khatmGap(id, AyahId(6)))
        assertNull("already covered", app.tracker.khatmGap(id, AyahId(3)))
    }

    @Test
    fun `the gap is measured from the nearest earlier covered ayah, including the assumed prefix`() = runTest {
        val id = startKhatm(from = AyahId(101))

        assertNull("first ayah after the assumed prefix is in sequence", app.tracker.khatmGap(id, AyahId(101)))
        assertNull("inside the assumed prefix is a re-reading", app.tracker.khatmGap(id, AyahId(50)))
        assertEquals(AyahRange.of(101, 110), app.tracker.khatmGap(id, AyahId(110)))

        tap(id, 105)
        assertEquals("now measured from the island at 105", AyahRange.of(106, 110), app.tracker.khatmGap(id, AyahId(110)))
    }

    @Test
    fun `in a fresh khatm a jump far ahead offers everything from the beginning`() = runTest {
        val id = startKhatm()
        assertEquals(AyahRange.of(1, 300), app.tracker.khatmGap(id, AyahId(300)))
    }

    @Test
    fun `fill in a khatm skips ayahs covered on an earlier day and those in the assumed prefix`() = runTest {
        val id = startKhatm(from = AyahId(11))
        tap(id, 15)
        app.clock.advanceDays(1)

        val batch = fill(id, 1, 20)

        assertEquals("11–14 and 16–20", 9, batch.added)
        assertEquals(20, snapshot(id).coveredAyahs)
        assertEquals(9, app.totals.today())
    }

    // ---- statistics, bars, pace ----

    @Test
    fun `pages read per day are the sum of each read ayah's share of its page`() = runTest {
        val id = startKhatm()
        val page1 = app.index.pageRange(1)
        val page2 = app.index.pageRange(2)
        fill(id, page1.start.value, page1.end.value)
        // Half of page 2, as near as its ayah count allows.
        val half = page2.size / 2
        fill(id, page2.start.value, page2.start.value + half - 1)

        val day = snapshot(id).days.single()
        assertEquals(today, day.day)
        assertEquals(page1.size + half, day.ayahs)
        assertEquals(1.0 + half.toDouble() / page2.size, day.pages, 1e-9)
    }

    @Test
    fun `each reading day of a khatm has its own ayah and page figures`() = runTest {
        val id = startKhatm()
        val first = today
        fill(id, 1, 30)
        app.clock.advanceDays(2)
        fill(id, 31, 40)

        val days = snapshot(id).days
        assertEquals(listOf(first, first.plusDays(2)), days.map { it.day })
        assertEquals(listOf(30, 10), days.map { it.ayahs })
        assertEquals(30, app.totals.total(first))
    }

    @Test
    fun `the today bar is pages read today over the daily goal and may pass 100 percent`() = runTest {
        val id = startKhatm(dailyPages = 2f)
        val page1 = app.index.pageRange(1)
        fill(id, page1.start.value, page1.end.value)
        assertEquals(0.5f, snapshot(id).todayGoalFraction!!, 1e-6f)

        val upToPage5 = app.index.pageRange(5).end.value
        fill(id, 1, upToPage5)
        assertEquals(2.5f, snapshot(id).todayGoalFraction!!, 1e-6f)

        app.clock.advanceDays(1)
        assertEquals("a new day starts the bar again", 0f, snapshot(id).todayGoalFraction!!, 1e-6f)
    }

    @Test
    fun `surah and juz bars describe the surah and juz of the resume position`() = runTest {
        val id = startKhatm()
        // All of surah 1 (7 ayahs) and the first 3 ayahs of surah 2.
        fill(id, 1, 10)

        val k = snapshot(id)
        assertEquals(2, k.currentSurah)
        assertEquals(3, k.surahBar.covered)
        assertEquals(286, k.surahBar.total)
        assertEquals(1, k.currentJuz)
        assertEquals(10, k.juzBar.covered)
        assertEquals(app.index.juzRange(1).size, k.juzBar.total)
        assertEquals(10, k.wholeBar.covered)
        assertEquals(Quran.AYAH_COUNT, k.wholeBar.total)
        assertEquals(10f / Quran.AYAH_COUNT, k.progress, 1e-9f)
    }

    @Test
    fun `the juz strip shows how much of each juz is covered`() = runTest {
        val id = startKhatm()
        val juz2 = app.index.juzRange(2)
        fill(id, juz2.start.value, juz2.end.value)

        val strip = snapshot(id).juzCoverage
        assertEquals(30, strip.size)
        assertEquals(0f, strip[0])
        assertEquals(1f, strip[1])
        assertEquals(0f, strip[2])
    }

    @Test
    fun `with a daily goal the projected finish is the remaining pages divided by the goal`() = runTest {
        val id = startKhatm(dailyPages = 4f)

        assertEquals("604 pages at 4 a day", today.plusDays(151), snapshot(id).projectedFinish)

        val endOfPage100 = app.index.pageRange(100).end.value
        fill(id, 1, endOfPage100)
        assertEquals("504 pages left", today.plusDays(126), snapshot(id).projectedFinish)
        assertEquals(504.0, snapshot(id).remainingPages, 1e-6)
    }

    @Test
    fun `without a goal the pace is the average of the last seven days that had reading`() = runTest {
        val id = startKhatm()
        // Nine reading days of one page each, with idle days between them: idle days do not slow the pace.
        for (page in 1..9) {
            val range = app.index.pageRange(page)
            fill(id, range.start.value, range.end.value)
            app.clock.advanceDays(3)
        }

        assertEquals("595 pages left at one page per active day", today.plusDays(595), snapshot(id).projectedFinish)
    }

    @Test
    fun `falling short of the daily goal is never reported as anything but progress`() = runTest {
        val id = startKhatm(dailyPages = 20f)
        tap(id, 1)

        val k = snapshot(id)
        assertTrue(k.todayGoalFraction!! > 0f && k.todayGoalFraction!! < 1f)
        assertNotNull("reading is never blocked by the goal", tap(id, 2))
    }

    // ---- completion and deletion ----

    @Test
    fun `a khatm is completed when its last uncovered ayah is marked, and not before`() = runTest {
        val id = startKhatm()
        fill(id, 1, Quran.AYAH_COUNT - 1)
        assertNull(app.khatmStore.khatm(id)!!.completedAt)
        assertFalse(snapshot(id).isComplete)

        app.clock.advanceDays(9)
        tap(id, Quran.AYAH_COUNT)

        val k = snapshot(id)
        assertTrue(k.isComplete)
        assertEquals(app.clock.now(), k.khatm.completedAt)
        assertNull(k.firstUnread)
        assertNull(k.resume)
        assertNull(k.projectedFinish)
        assertEquals(10, k.completion!!.daysTaken)
        assertEquals(Quran.AYAH_COUNT / 10.0, k.completion!!.averageAyahsPerDay, 1e-9)
        assertEquals(emptyList<String>(), app.khatmProgress.observeActive().first().map { it.khatm.name })
        assertEquals(listOf("Khatm 1"), app.khatmProgress.observeCompleted().first().map { it.khatm.name })
    }

    @Test
    fun `a khatm started mid-way completes once the rest has been read`() = runTest {
        val id = startKhatm(from = AyahId(6001))
        fill(id, 6001, Quran.AYAH_COUNT)

        assertTrue(snapshot(id).isComplete)
        assertEquals(236, app.totals.today())
    }

    @Test
    fun `undoing the mark that completed a khatm reopens it`() = runTest {
        val id = startKhatm()
        fill(id, 1, Quran.AYAH_COUNT - 10)
        val last = fill(id, Quran.AYAH_COUNT - 9, Quran.AYAH_COUNT)
        assertNotNull(app.khatmStore.khatm(id)!!.completedAt)

        app.tracker.undo(last)

        assertNull(app.khatmStore.khatm(id)!!.completedAt)
        assertEquals(listOf("Khatm 1"), app.khatmProgress.observeActive().first().map { it.khatm.name })
    }

    @Test
    fun `deleting a khatm keeps its readings in the day totals`() = runTest {
        val id = startKhatm()
        fill(id, 1, 37)

        app.khatms.delete(id)

        assertEquals(37, app.totals.today())
        assertNull(app.khatmProgress.snapshot(id))
        assertTrue("history still names the context, now without an owner", app.log.events.all { it.context == ReadContext.Khatm(id) })
    }

    @Test
    fun `a manual passage counted toward a khatm advances it, and undo restores both day and coverage`() = runTest {
        val id = startKhatm()
        val yesterday = today.minusDays(1)
        val pages = AyahRange(app.index.pageRange(1).start, app.index.pageRange(2).end)

        val batch = app.tracker.markRange(pages, ReadContext.Khatm(id), ReadMethod.MANUAL, RangeMode.ADD, day = yesterday)

        assertEquals(pages.size, app.totals.total(yesterday))
        assertEquals(pages.size, snapshot(id).coveredAyahs)
        assertEquals(pages.end.next(), snapshot(id).resume)

        app.tracker.undo(batch)
        assertEquals(0, app.totals.total(yesterday))
        assertEquals(0, snapshot(id).coveredAyahs)
    }

    // ---- managing khatms ----

    @Test
    fun `khatms can be renamed and given or relieved of a daily goal`() = runTest {
        val id = startKhatm("Khatm 1")

        app.khatms.rename(id, "  Ramadan khatm ")
        app.khatms.setDailyPages(id, 4f)
        assertEquals("Ramadan khatm", snapshot(id).khatm.name)
        assertEquals(4f, snapshot(id).khatm.dailyPages)

        app.khatms.setDailyPages(id, null)
        assertNull(snapshot(id).khatm.dailyPages)
    }

    @Test
    fun `the default name of the next khatm counts the ones that exist`() = runTest {
        assertEquals(1, app.khatms.nextOrdinal())
        startKhatm()
        assertEquals(2, app.khatms.nextOrdinal())
    }

    @Test
    fun `a khatm needs a name and a positive goal`() = runTest {
        assertThrows(DomainFailure.InvalidInput::class.java) { runBlocking { app.khatms.start("   ") } }
        assertThrows(DomainFailure.InvalidInput::class.java) { runBlocking { app.khatms.start("Khatm", dailyPages = 0f) } }
        assertThrows(DomainFailure.KhatmNotFound::class.java) { runBlocking { app.khatms.rename(99, "x") } }
        assertEquals(0, app.khatmStore.khatms().size)
    }
}
