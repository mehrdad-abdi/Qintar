package io.github.mehrdad_abdi.qintar.feature.insights

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.dayDetailViewModel
import io.github.mehrdad_abdi.qintar.testing.insightsViewModel
import io.github.mehrdad_abdi.qintar.testing.logReadingViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Insights with data, its calendar section and Day detail, in light and dark, English and Persian.
 * Record with `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class InsightsScreenshotTest(private val language: String, private val dark: Boolean) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = DomainFixture() // Sunday 15 Nov 2026
    private val today = app.clock.today()

    private fun shot(name: String, content: @Composable () -> Unit) {
        val night = if (dark) "night" else "notnight"
        RuntimeEnvironment.setQualifiers("$language-w412dp-h${HEIGHT_DP}dp-$night-xhdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    QintarTheme(darkTheme = dark) { Box(Modifier.height(HEIGHT_DP.dp).testTag(TAG)) { content() } }
                }
            }
            val theme = if (dark) "dark" else "light"
            compose.onNodeWithTag(TAG).captureRoboImage("src/test/screenshots/${name}_${theme}_$language.png")
        }
    }

    private fun read(day: LocalDate, count: Int, from: Int = 1, context: ReadContext = ReadContext.Browse, method: ReadMethod = ReadMethod.TAP) =
        runBlocking { app.tracker.markRange(AyahRange.of(from, from + count - 1), context, method, RangeMode.ADD, day = day) }

    /** About five weeks of readings with a gap, over several ranks. */
    private fun seedMonth() = runBlocking {
        val khatm = app.khatms.start("Ramadan khatm")
        val counts = listOf(14, 62, 0, 0, 120, 35, 210, 18, 55, 0, 320, 40, 75, 12, 101, 0, 28, 66, 150, 22, 0, 10, 47, 88, 510, 30, 60, 12, 95, 130)
        counts.forEachIndexed { i, n ->
            if (n > 0) {
                val day = today.minusDays((counts.size - 1 - i).toLong())
                val context = if (i % 3 == 0) ReadContext.Khatm(khatm.id) else ReadContext.Browse
                read(day, n, from = 1 + i * 10, context = context, method = if (i % 4 == 0) ReadMethod.AUDIO else ReadMethod.TAP)
            }
        }
        app.tracker.addTally(today.minusDays(3), 25, "Tarawih")
        app.prefs.update { it.copy(goalRank = Rank.DHAKIR) }
    }

    @Test
    fun insightsOverview() {
        seedMonth()
        val vm = app.insightsViewModel(Dispatchers.Unconfined)
        vm.onPeriod(InsightsPeriod.MONTH)
        shot("feature_insights") { InsightsScreen(onNavigate = {}, viewModel = vm) }
    }

    @Test
    fun insightsPersianCalendar() {
        // R-SET-5: the bars and the month grid in the Persian solar calendar.
        seedMonth()
        runBlocking { app.prefs.update { it.copy(calendarSystem = io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem.PERSIAN) } }
        val vm = app.insightsViewModel(Dispatchers.Unconfined)
        vm.onPeriod(InsightsPeriod.MONTH)
        val dates = io.github.mehrdad_abdi.qintar.domain.service.CalendarDates(io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem.PERSIAN)
        shot("feature_insights_persian_calendar") {
            androidx.compose.runtime.CompositionLocalProvider(io.github.mehrdad_abdi.qintar.ui.common.LocalCalendarDates provides dates) {
                InsightsScreen(onNavigate = {}, viewModel = vm)
            }
        }
    }

    @Test
    fun insightsCalendar() {
        seedMonth()
        val vm = app.insightsViewModel(Dispatchers.Unconfined)
        val calendar = vm.state.value.calendar!!
        shot("feature_insights_calendar") {
            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.screen)) {
                CalendarSection(calendar, onPrevious = {}, onNext = {}, onDay = {})
            }
        }
    }

    @Test
    fun dayDetail() {
        runBlocking {
            val khatm = app.khatms.start("Ramadan khatm")
            val bookmark = app.library.create(BookmarkDrafts.surah(112, app.index))
            val day = today.minusDays(1)
            read(day, 7, from = 1, context = ReadContext.Khatm(khatm.id))
            read(day, 1, from = 8, context = ReadContext.Khatm(khatm.id))
            read(day, 1, from = 8, context = ReadContext.Khatm(khatm.id))
            read(day, 12, from = 20, context = ReadContext.Khatm(khatm.id), method = ReadMethod.AUDIO)
            read(day, 4, from = app.ayah(112, 1).value, context = ReadContext.Bookmark(bookmark.id), method = ReadMethod.BULK)
            read(day, 3, from = 300, context = ReadContext.Random)
            read(day, 10, from = 400, context = ReadContext.Log, method = ReadMethod.MANUAL)
            app.tracker.addTally(day, 15, "Tarawih")
            read(today.minusDays(2), 12)
            read(today, 20)
        }
        val day = today.minusDays(1).toString()
        val vm = app.dayDetailViewModel(day, Dispatchers.Unconfined)
        val log = app.logReadingViewModel(Dispatchers.Unconfined)
        shot("feature_day_detail") { DayDetailScreen(DayDetailRoute(day), onBack = {}, onNavigate = {}, viewModel = vm, logViewModel = log) }
    }

    private companion object {
        const val TAG = "shot"
        const val HEIGHT_DP = 915

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> = listOf(
            arrayOf("en", false),
            arrayOf("en", true),
            arrayOf("fa", false),
            arrayOf("fa", true),
        )
    }
}
