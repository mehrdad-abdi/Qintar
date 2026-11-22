package io.github.mehrdad_abdi.qintar.feature.today

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.todayViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Today's state for the situations a user can be in. The fixture starts on Sunday 15 November 2026, 09:00. */
class TodayViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun today(systemFirstDay: DayOfWeek = DayOfWeek.SATURDAY) = app.todayViewModel(main.dispatcher, systemFirstDay, ticks)

    /** [count] readings today, as a browse reading of the first ayahs. */
    private suspend fun read(count: Int) {
        app.tracker.markRange(AyahRange.of(1, count), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
    }

    @Test
    fun `a new user sees an empty ring, no streak, and the two invitations`() = runTest {
        val state = today().state.value

        assertFalse(state.loading)
        assertEquals(LocalDate.of(2026, 11, 15), state.day)
        assertEquals(0, state.rank.count)
        assertEquals(Rank.NONE, state.rank.rank)
        assertEquals("10 ayahs to the first rank", 10, state.rank.toCaptionTarget)
        assertEquals(0f, state.rank.ringFraction)
        assertEquals(0, state.streak.current)
        assertFalse(state.streak.atRisk)
        assertNull("start a khatm", state.khatm)
        assertTrue(state.now.isEmpty())
        assertFalse("no bookmarks yet", state.hasBookmarks)
    }

    @Test
    fun `mid-day, the ring counts toward the next rank and the week shows today`() = runTest {
        val vm = today()
        read(47)
        val state = vm.state.value

        assertEquals(47, state.rank.count)
        assertEquals(Rank.NOT_HEEDLESS, state.rank.rank)
        assertEquals(Rank.DHAKIR, state.rank.captionTarget)
        assertEquals(3, state.rank.toCaptionTarget)
        assertEquals(47f / 50f, state.rank.ringFraction)
        assertEquals("one qualifying day", 1, state.streak.current)
        assertFalse(state.streak.atRisk)
        val todayCell = state.week.single { it.isToday }
        assertEquals(47, todayCell.total)
        assertEquals(Rank.NOT_HEEDLESS, todayCell.rank)
        assertEquals(7, state.week.size)
    }

    @Test
    fun `with a goal rank the caption names the goal until it is met`() = runTest {
        app.prefs.update { it.copy(goalRank = Rank.QANIT) }
        val vm = today()
        read(47)

        assertTrue(vm.state.value.rank.captionTargetIsGoal)
        assertEquals(Rank.QANIT, vm.state.value.rank.captionTarget)
        assertEquals(53, vm.state.value.rank.toCaptionTarget)

        read(60)
        assertFalse(vm.state.value.rank.captionTargetIsGoal)
        assertEquals(Rank.KHASHI, vm.state.value.rank.captionTarget)
    }

    @Test
    fun `the week strip starts on the user's first weekday`() = runTest {
        // Sunday 15 November 2026.
        val saturdayFirst = today(systemFirstDay = DayOfWeek.SATURDAY).state.value.week
        assertEquals(LocalDate.of(2026, 11, 14), saturdayFirst.first().day)
        assertEquals(1, saturdayFirst.indexOfFirst { it.isToday })
        assertEquals("the days after today are future", 5, saturdayFirst.count { it.isFuture })

        app.prefs.update { it.copy(weekStart = WeekStart.MONDAY) }
        val mondayFirst = today(systemFirstDay = DayOfWeek.SATURDAY).state.value.week
        assertEquals(LocalDate.of(2026, 11, 9), mondayFirst.first().day)
        assertTrue(mondayFirst.last().isToday)
        assertEquals(0, mondayFirst.count { it.isFuture })
    }

    @Test
    fun `yesterday's streak is at risk until today qualifies`() = runTest {
        read(10)
        app.clock.advanceDays(1)
        read(25)
        app.clock.advanceDays(1)
        val vm = today()

        assertEquals(2, vm.state.value.streak.current)
        assertTrue("read today to keep it", vm.state.value.streak.atRisk)
        assertEquals(listOf(10, 25, 0), vm.state.value.week.filter { !it.isFuture }.takeLast(3).map { it.total })

        read(9)
        assertTrue("nine is not enough", vm.state.value.streak.atRisk)

        read(1)
        assertEquals(3, vm.state.value.streak.current)
        assertFalse(vm.state.value.streak.atRisk)
    }

    @Test
    fun `Today left open across midnight starts the new day at zero`() = runTest {
        val vm = today()
        read(12)
        assertEquals(12, vm.state.value.rank.count)
        main.advanceTimeBy(3_000)

        app.clock.advanceDays(1)

        val state = vm.state.value
        assertEquals(LocalDate.of(2026, 11, 16), state.day)
        assertEquals(0, state.rank.count)
        assertEquals(1, state.streak.current)
        assertTrue(state.streak.atRisk)
        assertNull("a new day is not an achievement", state.reachedRank)
    }

    @Test
    fun `reaching a rank while Today is open shows the effect once`() = runTest {
        val vm = today()
        read(9)
        assertNull(vm.state.value.reachedRank)

        read(1)
        assertEquals(Rank.NOT_HEEDLESS, vm.state.value.reachedRank)

        main.advanceTimeBy(3_000)
        assertNull(vm.state.value.reachedRank)
        read(1)
        assertNull(vm.state.value.reachedRank)
    }

    // ---- Khatm -----------------------------------------------------------------------------------

    @Test
    fun `start a khatm from the empty card and continue it`() = runTest {
        val vm = today()

        vm.onStartKhatm("My khatm")

        val card = vm.state.value.khatm!!
        assertEquals("My khatm", card.name)
        assertEquals(1, card.page)
        assertEquals(0, card.whole.covered)
        assertNull("no daily goal", card.todayGoalFraction)
        assertEquals(app.khatmStore.khatms().single().id, card.id)
    }

    @Test
    fun `a khatm with a daily goal shows today's pages against it and its position bars`() = runTest {
        val khatm = app.khatms.start("Ramadan khatm", dailyPages = 2f)
        val vm = today()
        val firstPage = app.index.pageRange(1)
        app.tracker.markRange(firstPage, ReadContext.Khatm(khatm.id), ReadMethod.BULK, RangeMode.FILL)

        val card = vm.state.value.khatm!!

        assertEquals(2f, card.dailyPages)
        assertEquals(1.0, card.todayPages, 1e-6)
        assertEquals("one page of two", 0.5f, card.todayGoalFraction!!, 1e-4f)
        assertEquals(firstPage.size, card.whole.covered)
        assertEquals(6236, card.whole.total)
        assertEquals("resumes on page 2", 2, card.page)
        assertEquals(2, card.surah.number)
        assertEquals(firstPage.size - 7, card.surahBar.covered)
        assertEquals(1, card.juz)
        assertEquals(firstPage.size, card.juzBar.covered)
        assertEquals("khatm readings count toward the day", firstPage.size, vm.state.value.rank.count)
    }

    @Test
    fun `the khatm read most recently is the primary one and the others follow`() = runTest {
        val old = app.khatms.start("First")
        val recent = app.khatms.start("Second")
        app.tracker.mark(app.ayah(1, 1), ReadContext.Khatm(old.id), ReadMethod.TAP)
        app.clock.advanceMinutes(5)
        app.tracker.mark(app.ayah(1, 1), ReadContext.Khatm(recent.id), ReadMethod.TAP)
        app.clock.advanceMinutes(5)
        app.tracker.mark(app.ayah(1, 2), ReadContext.Khatm(old.id), ReadMethod.TAP)

        val state = today().state.value

        assertEquals("First", state.khatm!!.name)
        assertEquals(listOf("Second"), state.otherKhatms.map { it.name })
    }

    // ---- Available now ---------------------------------------------------------------------------

    @Test
    fun `bookmarks not read today are listed under their group, schedule or not`() = runTest {
        // Schedules are hidden (owner, 2026-11-22): Today lists what is left to read today.
        val morning = app.library.createGroup("Morning", schedule = Schedule.daily(TimeWindow(LocalTime.of(18, 0), LocalTime.of(20, 0))))
        val kursi = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = morning.id))
        val kahf = app.library.create(BookmarkDrafts.surah(18, app.index))
        val yasin = app.library.create(BookmarkDrafts.surah(36, app.index))
        val vm = today()
        val state = vm.state.value

        assertTrue(state.hasBookmarks)
        assertEquals(3, state.remainingNow)
        assertEquals(listOf("Morning", null), state.now.map { it.group?.group?.name })
        assertEquals(listOf(kursi.id), state.now[0].items.map { it.state.bookmark.id })
        assertEquals(listOf(kahf.id, yasin.id), state.now[1].items.map { it.state.bookmark.id })
        assertTrue(state.now[1].items.first().title is BookmarkTitle.Surah)

        app.tracker.mark(app.ayah(2, 255), ReadContext.Bookmark(kursi.id), ReadMethod.TAP)

        assertEquals("a bookmark read today leaves the list", 2, vm.state.value.remainingNow)
        assertEquals(listOf(kahf.id, yasin.id), vm.state.value.now.flatMap { it.items }.map { it.state.bookmark.id })
    }
}
