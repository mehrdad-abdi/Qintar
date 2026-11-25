package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** A day as the chosen calendar names it; [month] is 1…12. */
data class CalDate(val year: Int, val month: Int, val day: Int)

/** A month of the chosen calendar; [month] is 1…12. */
data class CalMonth(val year: Int, val month: Int) : Comparable<CalMonth> {
    override fun compareTo(other: CalMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })
}

/**
 * Dates in the calendar the user chose for display (R-SET-5, §6.6); storage and every count stay on
 * ISO dates, so the choice changes only what is shown.
 *
 * - Gregorian: the date itself.
 * - Persian (Solar Hijri): arithmetic, exact (the 33-year-cycle algorithm with its break years).
 * - Islamic (Hijri): Umm al-Qura, as `java.time` carries it, moved by [adjustDays] (−2…2) because the
 *   month's start follows the moon sighting where the user lives (owner, 2026-11-25). Offline.
 */
class CalendarDates(val system: CalendarSystem, private val adjustDays: Int = 0) {

    fun of(day: LocalDate): CalDate = when (system) {
        CalendarSystem.GREGORIAN -> CalDate(day.year, day.monthValue, day.dayOfMonth)
        CalendarSystem.PERSIAN -> Persian.fromEpochDay(day.toEpochDay())
        CalendarSystem.ISLAMIC -> hijri(day)
    }

    fun monthOf(day: LocalDate): CalMonth = of(day).let { CalMonth(it.year, it.month) }

    fun firstDay(month: CalMonth): LocalDate = when (system) {
        CalendarSystem.GREGORIAN -> LocalDate.of(month.year, month.month, 1)
        CalendarSystem.PERSIAN -> LocalDate.ofEpochDay(Persian.toEpochDay(month.year, month.month, 1))
        CalendarSystem.ISLAMIC -> LocalDate.from(HijrahDate.of(month.year, month.month, 1)).minusDays(adjustDays.toLong())
    }

    fun lastDay(month: CalMonth): LocalDate = firstDay(plus(month, 1)).minusDays(1)

    fun plus(month: CalMonth, months: Int): CalMonth {
        val index = month.year * 12 + (month.month - 1) + months
        return CalMonth(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)
    }

    private fun hijri(day: LocalDate): CalDate {
        val shifted = day.plusDays(adjustDays.toLong())
        // Umm al-Qura covers 1300–1600 AH (1882–2174); outside it the Gregorian date is shown.
        val h = runCatching { HijrahDate.from(shifted) }.getOrNull() ?: return CalDate(day.year, day.monthValue, day.dayOfMonth)
        return CalDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }

    /** The Solar Hijri calendar (the algorithm of the jalaali library, by Borkowski's break years). */
    private object Persian {
        private val BREAKS = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
        )

        private class Year(val leap: Int, val gregorianYear: Int, val march: Int)

        private fun div(a: Int, b: Int) = a / b

        private fun mod(a: Int, b: Int) = a - a / b * b

        private fun year(jy: Int): Year {
            val gy = jy + 621
            var leapJ = -14
            var jp = BREAKS[0]
            var jump = 0
            for (i in 1 until BREAKS.size) {
                val jm = BREAKS[i]
                jump = jm - jp
                if (jy < jm) break
                leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
                jp = jm
            }
            var n = jy - jp
            leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
            if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
            val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
            val march = 20 + leapJ - leapG
            if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
            var leap = mod(mod(n + 1, 33) - 1, 4)
            if (leap == -1) leap = 4
            return Year(leap, gy, march)
        }

        fun toEpochDay(jy: Int, jm: Int, jd: Int): Long {
            val y = year(jy)
            val nowruz = LocalDate.of(y.gregorianYear, 3, y.march).toEpochDay()
            return nowruz + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
        }

        fun fromEpochDay(epochDay: Long): CalDate {
            val gy = LocalDate.ofEpochDay(epochDay).year
            var jy = gy - 621
            val y = year(jy)
            var k = (epochDay - LocalDate.of(gy, 3, y.march).toEpochDay()).toInt()
            if (k >= 0) {
                if (k <= 185) return CalDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
                k -= 186
            } else {
                jy -= 1
                k += 179
                if (y.leap == 1) k += 1
            }
            return CalDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
        }
    }
}
