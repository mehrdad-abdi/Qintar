package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.Rank
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The tiles of Insights → Overview for one period (§5.7). */
data class PeriodSummary(
    val totalAyahs: Int,
    /** Days with at least one reading. */
    val activeDays: Int,
    val averagePerActiveDay: Double,
    val bestDay: LocalDate?,
    val bestDayTotal: Int,
    /** Days at each real rank, plus [Rank.NONE] for active days below 10. */
    val rankDistribution: Map<Rank, Int>,
)

object InsightsCalculator {
    /** Summarises the totals that fall within `from..to` (null = unbounded). */
    fun summary(totals: Map<LocalDate, Int>, from: LocalDate? = null, to: LocalDate? = null): PeriodSummary {
        val inPeriod = totals.filter { (day, total) ->
            total > 0 && (from == null || day >= from) && (to == null || day <= to)
        }
        val total = inPeriod.values.sum()
        // The earliest day wins a tie so that the answer does not depend on map order.
        val best = inPeriod.entries.sortedBy { it.key }.maxByOrNull { it.value }
        return PeriodSummary(
            totalAyahs = total,
            activeDays = inPeriod.size,
            averagePerActiveDay = if (inPeriod.isEmpty()) 0.0 else total.toDouble() / inPeriod.size,
            bestDay = best?.key,
            bestDayTotal = best?.value ?: 0,
            rankDistribution = inPeriod.values.groupingBy(RankCalculator::rankFor).eachCount(),
        )
    }

    /** "Goal met 22 of the last 30 days" (§4.5): days in `from..to` whose total reaches [goal]. */
    fun goalMetDays(totals: Map<LocalDate, Int>, goal: Rank, from: LocalDate, to: LocalDate): Int =
        totals.count { (day, total) -> day in from..to && total >= goal.threshold }
}

/** Week and month layout for the week strip and the calendar (R-SET-4). Mirroring for RTL is the UI's job. */
object CalendarWeeks {
    /** The seven weekdays in display order for a week that starts on [firstDay]. */
    fun weekdays(firstDay: DayOfWeek): List<DayOfWeek> = List(7) { firstDay.plus(it.toLong()) }

    /** The seven dates of the week containing [day], starting on [firstDay]. */
    fun weekContaining(day: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
        val back = (day.dayOfWeek.value - firstDay.value + 7) % 7
        val start = day.minusDays(back.toLong())
        return List(7) { start.plusDays(it.toLong()) }
    }

    /**
     * A Gregorian month as rows of seven cells starting on [firstDay]; cells outside the month are null.
     * Other calendar systems are laid out by the UI with the platform calendar and these same rules.
     */
    fun monthGrid(month: YearMonth, firstDay: DayOfWeek): List<List<LocalDate?>> = monthGrid(month.atDay(1), month.atEndOfMonth(), firstDay)

    /** The weeks of a month of any calendar, [first] to [last], blank cells before and after. */
    fun monthGrid(first: LocalDate, last: LocalDate, firstDay: DayOfWeek): List<List<LocalDate?>> {
        val lead = (first.dayOfWeek.value - firstDay.value + 7) % 7
        val length = (last.toEpochDay() - first.toEpochDay() + 1).toInt()
        val cells = List<LocalDate?>(lead) { null } + List(length) { first.plusDays(it.toLong()) }
        return cells.chunked(7) { row -> row + List<LocalDate?>(7 - row.size) { null } }
    }
}
