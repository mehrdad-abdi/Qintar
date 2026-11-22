package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.Rank
import java.time.LocalDate

data class Streak(
    /** Consecutive qualifying days ending today, or ending yesterday when today has not qualified yet. */
    val current: Int,
    /** [current] is alive only because of yesterday: "read today to keep it". */
    val atRisk: Boolean,
    val longest: Int,
)

object StreakCalculator {
    /**
     * Fixed at rank 1 by decision (§4.5): it does not follow the user's goal rank, so changing the goal
     * never rewrites the streak.
     */
    const val QUALIFYING_TOTAL = 10

    init {
        check(QUALIFYING_TOTAL == Rank.NOT_HEEDLESS.threshold)
    }

    fun qualifies(total: Int): Boolean = total >= QUALIFYING_TOTAL

    fun streak(totals: Map<LocalDate, Int>, today: LocalDate): Streak {
        fun qualifies(day: LocalDate) = qualifies(totals[day] ?: 0)

        fun runEnding(end: LocalDate): Int {
            var length = 0
            var day = end
            while (qualifies(day)) {
                length++
                day = day.minusDays(1)
            }
            return length
        }

        val todayQualifies = qualifies(today)
        val current = if (todayQualifies) runEnding(today) else runEnding(today.minusDays(1))

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in totals.keys.filter(::qualifies).sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }

        return Streak(current = current, atRisk = current > 0 && !todayQualifies, longest = longest)
    }
}
