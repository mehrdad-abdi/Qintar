package io.github.mehrdad_abdi.qintar.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import io.github.mehrdad_abdi.qintar.domain.service.CalendarDates
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Dates are written in the chosen calendar (R-SET-5, owner 2026-11-25). */
@RunWith(RobolectricTestRunner::class)
class CalendarTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.parse("2026-11-25")

    private fun show(system: CalendarSystem, content: @androidx.compose.runtime.Composable () -> String) = compose.setContent {
        QintarTheme {
            CompositionLocalProvider(LocalCalendarDates provides CalendarDates(system)) { BasicText(content()) }
        }
    }

    @Test
    fun `Gregorian, as before`() {
        show(CalendarSystem.GREGORIAN) { calendarDate(day, weekday = true) }
        compose.onNodeWithText("Wednesday 25 November 2026").assertExists()
    }

    @Test
    fun `Persian solar month names`() {
        show(CalendarSystem.PERSIAN) { calendarDate(day, weekday = true) }
        compose.onNodeWithText("Wednesday 4 Azar 1405").assertExists()
    }

    @Test
    @Config(qualifiers = "fa")
    fun `in Persian, with Persian digits and names`() {
        show(CalendarSystem.PERSIAN) { calendarDate(day) }
        compose.onNodeWithText("۴ آذر ۱۴۰۵").assertExists()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the Hijri month in Arabic`() {
        show(CalendarSystem.ISLAMIC) { monthTitle(LocalCalendarDates.current.monthOf(day)) }
        compose.onNodeWithText("جمادى الآخرة ١٤٤٨").assertExists()
    }
}
