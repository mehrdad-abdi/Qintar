package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import io.github.mehrdad_abdi.qintar.domain.service.CalDate
import io.github.mehrdad_abdi.qintar.domain.service.CalMonth
import io.github.mehrdad_abdi.qintar.domain.service.CalendarDates
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dates shown in the chosen calendar (R-SET-5, owner 2026-11-25); storage stays ISO. */
class CalendarDatesTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun `the Persian solar calendar starts each year at Nowruz`() {
        val persian = CalendarDates(CalendarSystem.PERSIAN)
        assertEquals(CalDate(1405, 9, 4), persian.of(d("2026-11-25")))
        assertEquals(CalDate(1405, 1, 1), persian.of(d("2026-03-21")))
        assertEquals(CalDate(1404, 12, 29), persian.of(d("2026-03-20")))
        assertEquals("1403 began a day earlier", CalDate(1403, 1, 1), persian.of(d("2024-03-20")))
        assertEquals("the last day of the leap year 1403", CalDate(1403, 12, 30), persian.of(d("2025-03-20")))
        assertEquals(CalDate(1405, 7, 1), persian.of(d("2026-09-23")))
    }

    @Test
    fun `the Hijri calendar is Umm al-Qura, shifted by the user's adjustment`() {
        val hijri = CalendarDates(CalendarSystem.ISLAMIC)
        assertEquals(CalDate(1447, 9, 1), hijri.of(d("2026-02-18")))
        assertEquals(CalDate(1448, 6, 15), hijri.of(d("2026-11-25")))
        // Where the month was seen a day later, the user sets -1: 18 February is then still Sha'ban.
        assertEquals(CalDate(1447, 8, 29), CalendarDates(CalendarSystem.ISLAMIC, adjustDays = -1).of(d("2026-02-18")))
        assertEquals(CalDate(1447, 9, 1), CalendarDates(CalendarSystem.ISLAMIC, adjustDays = -1).of(d("2026-02-19")))
    }

    @Test
    fun `Gregorian is the date itself`() {
        assertEquals(CalDate(2026, 11, 25), CalendarDates(CalendarSystem.GREGORIAN).of(d("2026-11-25")))
    }

    @Test
    fun `a month knows its first and last day, and steps back and forth across years`() {
        for (system in CalendarSystem.entries) {
            val dates = CalendarDates(system)
            var month = dates.monthOf(d("2026-11-25"))
            repeat(30) {
                val first = dates.firstDay(month)
                val last = dates.lastDay(month)
                assertEquals("$system $month", month, dates.monthOf(first))
                assertEquals("$system $month", month, dates.monthOf(last))
                assertEquals("$system $month", dates.monthOf(last.plusDays(1)), dates.plus(month, 1))
                assertEquals("$system $month", dates.monthOf(first.minusDays(1)), dates.plus(month, -1))
                month = dates.plus(month, -1)
            }
        }
        val persian = CalendarDates(CalendarSystem.PERSIAN)
        assertEquals(CalMonth(1404, 12), persian.plus(CalMonth(1405, 1), -1))
        assertEquals(d("2026-03-21"), persian.firstDay(CalMonth(1405, 1)))
        assertEquals(d("2026-11-21"), persian.lastDay(CalMonth(1405, 8)))
    }

    @Test
    fun `every day from 1900 to 2100 is one day after the one before, in every calendar`() {
        for (system in CalendarSystem.entries) {
            val dates = CalendarDates(system)
            var day = d("1900-01-01")
            var before = dates.of(day)
            while (day.year < 2100) {
                day = day.plusDays(1)
                val now = dates.of(day)
                val next = if (now.month == before.month && now.year == before.year) now.day == before.day + 1 else now.day == 1
                assertEquals("$system $day: $before then $now", true, next)
                assertEquals("$system $day", day, dates.firstDay(CalMonth(now.year, now.month)).plusDays(now.day - 1L))
                before = now
            }
        }
    }
}
