package io.github.mehrdad_abdi.qintar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.service.CalMonth
import io.github.mehrdad_abdi.qintar.domain.service.CalendarDates
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

/**
 * The calendar dates are shown in (R-SET-5, §6.6); provided once from the settings. Every date the
 * user reads goes through the functions below, so the choice reaches all screens. Storage stays ISO.
 */
val LocalCalendarDates = staticCompositionLocalOf { CalendarDates(CalendarSystem.GREGORIAN) }

fun calendarDatesOf(prefs: UserPrefs) = CalendarDates(prefs.calendarSystem, prefs.hijriAdjustDays)

/** The name of [month] (1…12) of the chosen calendar. */
@Composable
fun monthName(month: Int, short: Boolean = false): String {
    val dates = LocalCalendarDates.current
    return when (dates.system) {
        CalendarSystem.GREGORIAN -> Month.of(month).getDisplayName(
            if (short) TextStyle.SHORT_STANDALONE else TextStyle.FULL_STANDALONE,
            LocalConfiguration.current.locales[0],
        )
        CalendarSystem.PERSIAN -> stringArrayResource(R.array.months_persian)[month - 1]
        CalendarSystem.ISLAMIC -> stringArrayResource(R.array.months_hijri)[month - 1]
    }
}

/** The day of the month in the chosen calendar, in the app's digits. */
@Composable
fun dayOfMonthText(day: LocalDate): String = localizeDigits(LocalCalendarDates.current.of(day).day.toString())

/** "25 November 2026", "۴ آذر ۱۴۰۵"; with [weekday] "Wednesday 25 November"; [year] adds the year. */
@Composable
fun calendarDate(day: LocalDate, weekday: Boolean = false, year: Boolean = true, shortMonth: Boolean = false): String {
    val date = LocalCalendarDates.current.of(day)
    val d = localizeDigits(date.day.toString())
    val month = monthName(date.month, shortMonth)
    val core = if (year) {
        stringResource(R.string.date_day_month_year, d, month, localizeDigits(date.year.toString()))
    } else {
        stringResource(R.string.date_day_month, d, month)
    }
    if (!weekday) return core
    val name = day.dayOfWeek.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
    return stringResource(R.string.date_with_weekday, name, core)
}

/** "Azar 1405": the title of a month of the chosen calendar. */
@Composable
fun monthTitle(month: CalMonth, short: Boolean = false): String =
    stringResource(R.string.date_month_year, monthName(month.month, short), localizeDigits(month.year.toString()))

