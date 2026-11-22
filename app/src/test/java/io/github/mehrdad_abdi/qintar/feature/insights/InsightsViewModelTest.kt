package io.github.mehrdad_abdi.qintar.feature.insights

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.insightsViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Insights (§5.7): overview, calendar and Revisit through the view model over the in-memory domain. */
class InsightsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture() // Sunday 15 Nov 2026, 09:00
    private val today = app.clock.today()

    private suspend fun read(
        day: LocalDate,
        count: Int,
        from: Int = 1,
        context: ReadContext = ReadContext.Browse,
        method: ReadMethod = ReadMethod.BULK,
    ) = app.tracker.markRange(AyahRange.of(from, from + count - 1), context, method, RangeMode.ADD, day = day)

    /** 30 today, 60 yesterday, 120 on 10 Nov; 15 on 1 Nov; 200 on 18 Oct; 500 on 5 Jan; 40 in June 2025. */
    private suspend fun seedHistory() {
        read(today, 30)
        read(today.minusDays(1), 60)
        read(LocalDate.of(2026, 11, 10), 120)
        read(LocalDate.of(2026, 11, 1), 15)
        read(LocalDate.of(2026, 10, 18), 200)
        read(LocalDate.of(2026, 1, 5), 500)
        read(LocalDate.of(2025, 6, 1), 40)
    }

    private fun open(firstDay: DayOfWeek = DayOfWeek.SATURDAY) = app.insightsViewModel(main.dispatcher, firstDay)

    // ---- the overview -----------------------------------------------------------------------------------

    @Test
    fun `a new user has the empty state and still a calendar`() {
        val s = open().state.value
        assertFalse(s.loading)
        assertTrue(s.isNewUser)
        assertEquals(0, s.summary.totalAyahs)
        assertNotNull(s.calendar)
        assertEquals(30, s.calendar!!.days.size)
    }

    @Test
    fun `the summary of each period counts only its days`() = runTest {
        seedHistory()
        val vm = open()

        val week = vm.state.value
        assertEquals(InsightsPeriod.WEEK, week.period)
        assertEquals(210, week.summary.totalAyahs)
        assertEquals(3, week.summary.activeDays)
        assertEquals(70.0, week.summary.averagePerActiveDay, 0.0)
        assertEquals(LocalDate.of(2026, 11, 10), week.summary.bestDay)
        assertEquals(120, week.summary.bestDayTotal)

        vm.onPeriod(InsightsPeriod.MONTH)
        assertEquals(425, vm.state.value.summary.totalAyahs)
        assertEquals(5, vm.state.value.summary.activeDays)
        assertEquals(LocalDate.of(2026, 10, 18), InsightsPeriod.MONTH.from(today)?.plusDays(1))

        vm.onPeriod(InsightsPeriod.YEAR)
        assertEquals(925, vm.state.value.summary.totalAyahs)
        assertEquals(6, vm.state.value.summary.activeDays)
        assertEquals(500, vm.state.value.summary.bestDayTotal)

        vm.onPeriod(InsightsPeriod.ALL)
        assertEquals(965, vm.state.value.summary.totalAyahs)
        assertEquals(7, vm.state.value.summary.activeDays)
        assertFalse(vm.state.value.isNewUser)
    }

    @Test
    fun `rank distribution counts days at each rank`() = runTest {
        seedHistory()
        read(today.minusDays(2), 5, from = 400) // active, below the first rank
        val vm = open()
        val days = vm.state.value.rankDays.toMap()
        assertEquals(7, vm.state.value.rankDays.size)
        assertEquals(1, days[Rank.NOT_HEEDLESS])
        assertEquals(1, days[Rank.DHAKIR])
        assertEquals(1, days[Rank.QANIT])
        assertEquals(0, days[Rank.QINTAR])
        assertEquals(1, vm.state.value.belowRankDays)
    }

    @Test
    fun `bars follow the period and carry their rank`() = runTest {
        seedHistory()
        val vm = open()
        val week = vm.state.value.bars
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().start)
        assertEquals(today, week.last().start)
        assertEquals(30, week.last().count)
        assertEquals(Rank.NOT_HEEDLESS, week.last().rank)
        assertEquals(Rank.DHAKIR, week[5].rank)
        assertEquals(Rank.NONE, week.first().rank)
        assertFalse(week.any { it.monthly })

        vm.onPeriod(InsightsPeriod.MONTH)
        assertEquals(30, vm.state.value.bars.size)

        vm.onPeriod(InsightsPeriod.YEAR)
        val year = vm.state.value.bars
        assertEquals(12, year.size)
        assertTrue(year.all { it.monthly })
        assertEquals(LocalDate.of(2025, 12, 1), year.first().start)
        assertEquals(30 + 60 + 120 + 15, year.last().count)
        assertEquals(500, year[1].count)

        vm.onPeriod(InsightsPeriod.ALL)
        val all = vm.state.value.bars
        assertEquals(LocalDate.of(2025, 6, 1), all.first().start)
        assertEquals(18, all.size)
        assertEquals(40, all.first().count)
    }

    // ---- streaks and goal -------------------------------------------------------------------------------

    @Test
    fun `current and longest streak are counted from days of ten ayahs or more`() = runTest {
        for (back in listOf(0L, 1L, 2L, 4L, 5L, 6L, 7L)) read(today.minusDays(back), 10)
        read(today.minusDays(3), 9) // one short: breaks the run
        val s = open().state.value
        assertEquals(3, s.streak.current)
        assertEquals(4, s.streak.longest)
        assertFalse(s.streak.atRisk)
    }

    @Test
    fun `a streak that has not been read for today yet is at risk`() = runTest {
        read(today.minusDays(1), 12)
        read(today.minusDays(2), 12)
        val s = open().state.value
        assertEquals(2, s.streak.current)
        assertTrue(s.streak.atRisk)
    }

    @Test
    fun `goal met days are counted in the period, and absent without a goal`() = runTest {
        seedHistory()
        val vm = open()
        assertNull(vm.state.value.goal)
        assertNull(vm.state.value.goalMetDays)

        app.prefs.update { it.copy(goalRank = Rank.DHAKIR) }
        assertEquals(Rank.DHAKIR, vm.state.value.goal)
        assertEquals(2, vm.state.value.goalMetDays) // 60 and 120; 30 is short of 50
        assertEquals(7, vm.state.value.periodDays)

        vm.onPeriod(InsightsPeriod.MONTH)
        assertEquals(3, vm.state.value.goalMetDays) // + 200 on 18 Oct
        assertEquals(30, vm.state.value.periodDays)

        app.prefs.update { it.copy(goalRank = Rank.QANIT) }
        assertEquals(2, vm.state.value.goalMetDays) // 120 and 200
    }

    // ---- sources ----------------------------------------------------------------------------------------

    @Test
    fun `the source split separates where and how it was read, and counts tallies as manual`() = runTest {
        val khatm = app.khatms.start("K")
        val bookmark = app.library.create(BookmarkDrafts.surah(112, app.index))
        read(today, 5, from = 1, context = ReadContext.Khatm(khatm.id), method = ReadMethod.TAP)
        read(today, 3, from = 20, context = ReadContext.Bookmark(bookmark.id), method = ReadMethod.BULK)
        read(today, 2, from = 40, context = ReadContext.Browse, method = ReadMethod.AUDIO)
        read(today, 1, from = 50, context = ReadContext.Random, method = ReadMethod.TAP)
        read(today, 6, from = 60, context = ReadContext.Log, method = ReadMethod.MANUAL)
        app.tracker.addTally(today, 4, "note")
        read(today.minusDays(20), 100, from = 100, context = ReadContext.Browse, method = ReadMethod.BULK)

        val vm = open()
        val week = vm.state.value.sources
        assertEquals(21, week.total)
        assertEquals(5, week.bySource[SourceKind.KHATM])
        assertEquals(3, week.bySource[SourceKind.BOOKMARK])
        assertEquals(2, week.bySource[SourceKind.BROWSE])
        assertEquals(1, week.bySource[SourceKind.RANDOM])
        assertEquals(10, week.bySource[SourceKind.MANUAL])
        assertEquals(6, week.byMethod[MethodKind.TAPPED])
        assertEquals(3, week.byMethod[MethodKind.BULK])
        assertEquals(2, week.byMethod[MethodKind.LISTENED])
        assertEquals(10, week.byMethod[MethodKind.MANUAL])
        assertEquals(week.total, week.byMethod.values.sum())
        assertEquals(week.total, week.bySource.values.sum())

        vm.onPeriod(InsightsPeriod.MONTH)
        assertEquals(121, vm.state.value.sources.total)
        assertEquals(103, vm.state.value.sources.byMethod[MethodKind.BULK])
    }

    // ---- calendar ---------------------------------------------------------------------------------------

    @Test
    fun `the month grid honours the week start`() = runTest {
        // 1 Nov 2026 is a Sunday.
        val vm = open(DayOfWeek.SATURDAY)
        var cal = vm.state.value.calendar!!
        assertEquals(YearMonth.of(2026, 11), cal.month)
        assertEquals(DayOfWeek.SATURDAY, cal.weekdays.first())
        assertEquals(1, cal.leadingBlanks)

        app.prefs.update { it.copy(weekStart = WeekStart.MONDAY) }
        cal = vm.state.value.calendar!!
        assertEquals(DayOfWeek.MONDAY, cal.weekdays.first())
        assertEquals(DayOfWeek.SUNDAY, cal.weekdays.last())
        assertEquals(6, cal.leadingBlanks)

        app.prefs.update { it.copy(weekStart = WeekStart.SUNDAY) }
        assertEquals(0, vm.state.value.calendar!!.leadingBlanks)

        app.prefs.update { it.copy(weekStart = WeekStart.SYSTEM) }
        assertEquals(DayOfWeek.SATURDAY, vm.state.value.calendar!!.weekdays.first())
    }

    @Test
    fun `each calendar day shows its total and rank, and today and the future are marked`() = runTest {
        seedHistory()
        val cal = open().state.value.calendar!!
        fun day(d: Int) = cal.days.first { it.date.dayOfMonth == d }
        assertEquals(30, cal.days.size)
        assertEquals(Rank.DHAKIR, day(14).rank)
        assertEquals(60, day(14).total)
        assertEquals(Rank.QANIT, day(10).rank)
        assertEquals(Rank.NOT_HEEDLESS, day(1).rank) // 15
        assertEquals(Rank.NOT_HEEDLESS, day(15).rank) // 30
        assertEquals(Rank.NONE, day(3).rank)
        assertEquals(0, day(3).total)
        assertTrue(day(15).isToday)
        assertFalse(day(14).isToday)
        assertTrue(day(16).isFuture)
        assertFalse(day(15).isFuture)
    }

    @Test
    fun `months can be browsed back and forth but not past the current one`() = runTest {
        seedHistory()
        val vm = open()
        assertFalse(vm.state.value.calendar!!.canGoNext)
        vm.onNextMonth()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.calendar!!.month)

        vm.onPreviousMonth()
        val october = vm.state.value.calendar!!
        assertEquals(YearMonth.of(2026, 10), october.month)
        assertTrue(october.canGoNext)
        assertEquals(31, october.days.size)
        assertEquals(Rank.KHASHI, october.days.first { it.date.dayOfMonth == 18 }.rank)
        assertTrue(october.days.none { it.isFuture || it.isToday })

        vm.onNextMonth()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.calendar!!.month)
        vm.onPreviousMonth()
        vm.onThisMonth()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.calendar!!.month)
    }

    @Test
    fun `an edit to a past day shows in the calendar at once`() = runTest {
        val vm = open()
        assertEquals(Rank.NONE, vm.state.value.calendar!!.days.first { it.date.dayOfMonth == 12 }.rank)
        app.tracker.addTally(LocalDate.of(2026, 11, 12), 55, null)
        assertEquals(Rank.DHAKIR, vm.state.value.calendar!!.days.first { it.date.dayOfMonth == 12 }.rank)
        assertFalse(vm.state.value.isNewUser)
    }

    // ---- revisit ----------------------------------------------------------------------------------------

    @Test
    fun `revisit lists the pages read longest ago and the pages not read yet`() = runTest {
        // Page 1 fully read on 1 Oct, page 2 fully read on 10 Nov, page 3 partly read.
        val p1 = app.index.pageRange(1)
        val p2 = app.index.pageRange(2)
        val p3 = app.index.pageRange(3)
        app.tracker.markRange(p1, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD, day = LocalDate.of(2026, 10, 1))
        app.tracker.markRange(p2, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD, day = LocalDate.of(2026, 11, 10))
        app.tracker.markRange(AyahRange(p3.start, p3.start), ReadContext.Browse, ReadMethod.TAP, RangeMode.ADD, day = today)

        val vm = open()
        val r = vm.state.value.revisit
        assertEquals(listOf(1, 2), r.readLongAgo.map { it.page })
        assertEquals(LocalDate.of(2026, 10, 1), r.readLongAgo.first().lastRead)
        assertEquals(p1.start, r.readLongAgo.first().firstAyah)
        assertEquals(1, r.readLongAgo.first().surahs.first().number)
        assertEquals(2, r.readLongAgoTotal)

        assertEquals(5, r.notReadYet.size)
        assertEquals(3, r.notReadYet.first().page)
        assertEquals(p3.size - 1, r.notReadYet.first().neverRead)
        assertEquals(604 - 2, r.notReadYetTotal)
        assertEquals(p3.start, r.notReadYet.first().firstAyah)

        vm.onToggleRevisit()
        assertEquals(20, vm.state.value.revisit.notReadYet.size)
        assertTrue(vm.state.value.revisit.expanded)
        vm.onToggleRevisit()
        assertEquals(5, vm.state.value.revisit.notReadYet.size)
    }

    @Test
    fun `reading a page again moves it to the back of read long ago`() = runTest {
        val p1 = app.index.pageRange(1)
        val p2 = app.index.pageRange(2)
        app.tracker.markRange(p1, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD, day = LocalDate.of(2026, 10, 1))
        app.tracker.markRange(p2, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD, day = LocalDate.of(2026, 10, 5))
        val vm = open()
        assertEquals(listOf(1, 2), vm.state.value.revisit.readLongAgo.map { it.page })
        app.tracker.markRange(p1, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD, day = today)
        assertEquals(listOf(2, 1), vm.state.value.revisit.readLongAgo.map { it.page })
    }
}
