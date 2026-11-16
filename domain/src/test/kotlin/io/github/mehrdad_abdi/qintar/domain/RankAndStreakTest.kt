package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.domain.service.CalendarWeeks
import io.github.mehrdad_abdi.qintar.domain.service.InsightsCalculator
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.service.Streak
import io.github.mehrdad_abdi.qintar.domain.service.StreakCalculator
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §4.4 ranks, §4.5 streaks, and the Insights figures derived from day totals. */
class RankAndStreakTest {
    private val today: LocalDate = LocalDate.of(2026, 11, 15)

    private fun days(vararg totals: Pair<Int, Int>): Map<LocalDate, Int> =
        totals.associate { (daysAgo, total) -> today.minusDays(daysAgo.toLong()) to total }

    // ---- ranks ----

    @Test
    fun `each rank is earned exactly at its threshold and not one ayah before`() {
        val ladder = listOf(
            10 to Rank.NOT_HEEDLESS, 50 to Rank.DHAKIR, 100 to Rank.QANIT, 200 to Rank.KHASHI,
            300 to Rank.FAIZ, 500 to Rank.MUJTAHID, 1000 to Rank.QINTAR,
        )
        var below = Rank.NONE
        for ((threshold, rank) in ladder) {
            assertEquals("at ${threshold - 1}", below, RankCalculator.rankFor(threshold - 1))
            assertEquals("at $threshold", rank, RankCalculator.rankFor(threshold))
            assertEquals("at ${threshold + 1}", rank, RankCalculator.rankFor(threshold + 1))
            below = rank
        }
        assertEquals(7, Rank.ladder.size)
    }

    @Test
    fun `before ten ayahs there is no rank, only the distance to the first`() {
        val nothing = RankCalculator.progress(0)
        assertEquals(Rank.NONE, nothing.rank)
        assertEquals(Rank.NOT_HEEDLESS, nothing.next)
        assertEquals("10 ayahs to the first rank", 10, nothing.toNext)
        assertEquals(0f, nothing.ringFraction)

        assertEquals(1, RankCalculator.progress(9).toNext)
    }

    @Test
    fun `the ring fills toward the next threshold, so 47 of 50 looks nearly full`() {
        val progress = RankCalculator.progress(47)

        assertEquals(Rank.NOT_HEEDLESS, progress.rank)
        assertEquals(Rank.DHAKIR, progress.next)
        assertEquals(3, progress.toNext)
        assertEquals(47f / 50f, progress.ringFraction, 1e-6f)
    }

    @Test
    fun `at Qintar the ring is complete and the count keeps rising`() {
        for (count in listOf(1000, 1001, 6236)) {
            val progress = RankCalculator.progress(count)
            assertEquals(Rank.QINTAR, progress.rank)
            assertNull(progress.next)
            assertEquals(0, progress.toNext)
            assertEquals(1f, progress.ringFraction)
            assertEquals(count, progress.count)
        }
    }

    @Test
    fun `while a goal rank is unmet the caption counts down to the goal, afterwards to the next rank`() {
        val before = RankCalculator.progress(47, goal = Rank.QANIT)
        assertTrue(before.captionTargetIsGoal)
        assertEquals(Rank.QANIT, before.captionTarget)
        assertEquals("53 to Qanit, your goal", 53, before.toCaptionTarget)
        assertFalse(before.goalMet)

        val after = RankCalculator.progress(120, goal = Rank.QANIT)
        assertTrue(after.goalMet)
        assertFalse(after.captionTargetIsGoal)
        assertEquals(Rank.KHASHI, after.captionTarget)
        assertEquals(80, after.toCaptionTarget)

        val noGoal = RankCalculator.progress(47)
        assertEquals(Rank.DHAKIR, noGoal.captionTarget)
        assertFalse(noGoal.goalMet)
    }

    @Test
    fun `a rank is announced when a reading crosses its threshold, once, and for the highest rank crossed`() {
        assertNull(RankCalculator.reached(before = 8, after = 9))
        assertEquals(Rank.NOT_HEEDLESS, RankCalculator.reached(before = 9, after = 10))
        assertNull("already there", RankCalculator.reached(before = 10, after = 11))
        assertEquals("one effect for a bulk mark that crosses three thresholds", Rank.QANIT, RankCalculator.reached(before = 5, after = 110))
        assertNull("a correction downwards announces nothing", RankCalculator.reached(before = 110, after = 5))
    }

    @Test
    fun `set count 100 on one ayah takes the day from no rank to Qanit`() = runTest {
        val app = DomainFixture()
        val before = app.totals.today()

        app.tracker.setCount(app.ayah(112, 1), ReadContext.Browse, app.clock.today(), 100)

        assertEquals(Rank.QANIT, RankCalculator.reached(before, app.totals.today()))
    }

    // ---- streaks ----

    @Test
    fun `streak is not zero in the morning after a qualifying yesterday`() {
        val streak = StreakCalculator.streak(days(1 to 40, 2 to 12, 3 to 10), today)

        assertEquals("three days, kept alive by yesterday", 3, streak.current)
        assertTrue("shown with 'read today to keep it'", streak.atRisk)
    }

    @Test
    fun `reading ten ayahs today extends the streak and takes it out of risk`() {
        assertEquals(Streak(current = 3, atRisk = true, longest = 3), StreakCalculator.streak(days(0 to 9, 1 to 40, 2 to 12, 3 to 10), today))
        assertEquals(Streak(current = 4, atRisk = false, longest = 4), StreakCalculator.streak(days(0 to 10, 1 to 40, 2 to 12, 3 to 10), today))
    }

    @Test
    fun `a day counts toward the streak from ten ayahs, whatever the user's goal`() {
        assertEquals(0, StreakCalculator.streak(days(0 to 9), today).current)
        assertEquals(1, StreakCalculator.streak(days(0 to 10), today).current)
        assertTrue(StreakCalculator.qualifies(10))
        assertFalse(StreakCalculator.qualifies(9))
    }

    @Test
    fun `missing both today and yesterday ends the streak`() {
        val streak = StreakCalculator.streak(days(2 to 100, 3 to 100, 4 to 100), today)

        assertEquals(0, streak.current)
        assertFalse(streak.atRisk)
        assertEquals("the past run is still the record", 3, streak.longest)
    }

    @Test
    fun `a day below ten breaks the run`() {
        val streak = StreakCalculator.streak(days(0 to 20, 1 to 20, 2 to 9, 3 to 20, 4 to 20, 5 to 20), today)

        assertEquals(2, streak.current)
        assertEquals(3, streak.longest)
    }

    @Test
    fun `with nothing ever read there is no streak and nothing at risk`() {
        assertEquals(Streak(0, atRisk = false, longest = 0), StreakCalculator.streak(emptyMap(), today))
    }

    @Test
    fun `the longest streak is the longest run ever, even across a month boundary`() {
        val totals = (0L..40L).associate { LocalDate.of(2026, 1, 20).plusDays(it) to 15 } + days(0 to 15)
        val streak = StreakCalculator.streak(totals, today)

        assertEquals(41, streak.longest)
        assertEquals(1, streak.current)
    }

    @Test
    fun `adding a reading to a past day changes that day's rank and the streak`() = runTest {
        val app = DomainFixture()
        val now = app.clock.today()
        // Read today and two days ago; yesterday was forgotten.
        app.tracker.addTally(now, 20, null)
        app.tracker.addTally(now.minusDays(2), 20, null)
        suspend fun streak() = StreakCalculator.streak(app.totals.totals(null, null), now)
        assertEquals(1, streak().current)
        assertEquals(Rank.NONE, RankCalculator.rankFor(app.totals.total(now.minusDays(1))))

        // The user logs yesterday's reading of al-Fatiha and the opening of al-Baqarah after the fact.
        app.tracker.markRange(AyahRange.of(1, 57), ReadContext.Log, ReadMethod.MANUAL, RangeMode.ADD, day = now.minusDays(1))

        assertEquals(Rank.DHAKIR, RankCalculator.rankFor(app.totals.total(now.minusDays(1))))
        assertEquals("the gap is closed", 3, streak().current)
        assertEquals(3, streak().longest)
    }

    @Test
    fun `deleting a past day's reading shortens the streak again`() = runTest {
        val app = DomainFixture()
        val now = app.clock.today()
        app.tracker.addTally(now, 20, null)
        val yesterday = app.tracker.addTally(now.minusDays(1), 20, null)

        app.tracker.deleteTally(yesterday.id)

        assertEquals(1, StreakCalculator.streak(app.totals.totals(null, null), now).current)
    }

    // ---- insights ----

    @Test
    fun `the overview totals, averages over active days only, and finds the best day`() {
        val totals = days(0 to 120, 1 to 30, 3 to 30, 9 to 500)

        val week = InsightsCalculator.summary(totals, from = today.minusDays(6), to = today)

        assertEquals(180, week.totalAyahs)
        assertEquals(3, week.activeDays)
        assertEquals(60.0, week.averagePerActiveDay, 1e-9)
        assertEquals(today, week.bestDay)
        assertEquals(120, week.bestDayTotal)
        assertEquals(mapOf(Rank.QANIT to 1, Rank.NOT_HEEDLESS to 2), week.rankDistribution)

        val all = InsightsCalculator.summary(totals)
        assertEquals(680, all.totalAyahs)
        assertEquals(today.minusDays(9), all.bestDay)
    }

    @Test
    fun `an empty period has no best day and a zero average`() {
        val summary = InsightsCalculator.summary(emptyMap())
        assertEquals(0, summary.totalAyahs)
        assertNull(summary.bestDay)
        assertEquals(0.0, summary.averagePerActiveDay, 0.0)
    }

    @Test
    fun `goal met counts the days of a period that reached the goal rank`() {
        val totals = days(0 to 100, 1 to 99, 2 to 250, 40 to 1000)

        assertEquals(2, InsightsCalculator.goalMetDays(totals, Rank.QANIT, today.minusDays(29), today))
    }

    // ---- week start ----

    @Test
    fun `week strip and calendar honour the week-start setting`() {
        // 2026-11-15 is a Sunday.
        assertEquals(DayOfWeek.SUNDAY, today.dayOfWeek)

        val saturdayWeek = CalendarWeeks.weekContaining(today, WeekStart.SATURDAY.firstDay(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 14), saturdayWeek.first())
        assertEquals(LocalDate.of(2026, 11, 20), saturdayWeek.last())

        val sundayWeek = CalendarWeeks.weekContaining(today, WeekStart.SUNDAY.firstDay(DayOfWeek.MONDAY))
        assertEquals(today, sundayWeek.first())

        val mondayWeek = CalendarWeeks.weekContaining(today, WeekStart.MONDAY.firstDay(DayOfWeek.SATURDAY))
        assertEquals(LocalDate.of(2026, 11, 9), mondayWeek.first())
        assertEquals(today, mondayWeek.last())

        assertEquals("'system' defers to the device", DayOfWeek.SATURDAY, WeekStart.SYSTEM.firstDay(DayOfWeek.SATURDAY))
        assertEquals(
            listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            CalendarWeeks.weekdays(DayOfWeek.SATURDAY),
        )
    }

    @Test
    fun `the month grid starts each row on the chosen weekday and pads outside the month`() {
        // November 2026 starts on a Sunday and has 30 days.
        val saturdayFirst = CalendarWeeks.monthGrid(YearMonth.of(2026, 11), DayOfWeek.SATURDAY)
        assertEquals(listOf(null, LocalDate.of(2026, 11, 1)), saturdayFirst.first().take(2))
        assertTrue(saturdayFirst.all { it.size == 7 })
        assertEquals(30, saturdayFirst.flatten().filterNotNull().size)
        for (row in saturdayFirst) {
            row.forEachIndexed { column, day -> if (day != null) assertEquals(CalendarWeeks.weekdays(DayOfWeek.SATURDAY)[column], day.dayOfWeek) }
        }

        val sundayFirst = CalendarWeeks.monthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sundayFirst.first().first())
        assertEquals(5, sundayFirst.size)
    }
}
