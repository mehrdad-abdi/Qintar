package io.github.mehrdad_abdi.qintar.feature.insights

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.today.TODAY_LOG_TAG
import io.github.mehrdad_abdi.qintar.feature.today.TodayScreen
import io.github.mehrdad_abdi.qintar.testing.dayDetailViewModel
import io.github.mehrdad_abdi.qintar.testing.insightsViewModel
import io.github.mehrdad_abdi.qintar.testing.logReadingViewModel
import io.github.mehrdad_abdi.qintar.testing.todayViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReadRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real Insights, Day detail and Log reading screens, driven by taps, over the in-memory domain. */
@RunWith(RobolectricTestRunner::class)
class InsightsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture() // Sunday 15 Nov 2026, 09:00
    private val today = app.clock.today()
    private val yesterday = today.minusDays(1)
    private val navigated = ArrayList<Any>()

    private fun read(day: LocalDate, from: Int, to: Int, context: ReadContext = ReadContext.Browse) = runBlocking {
        app.tracker.markRange(AyahRange.of(from, to), context, ReadMethod.TAP, RangeMode.ADD, day = day)
    }

    private fun openInsights() {
        val vm = app.insightsViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { InsightsScreen(onNavigate = { navigated += it }, viewModel = vm) } }
    }

    private fun openDay(day: LocalDate) {
        val vm = app.dayDetailViewModel(day.toString(), Dispatchers.Unconfined)
        val log = app.logReadingViewModel(Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme {
                DayDetailScreen(DayDetailRoute(day.toString()), onBack = {}, onNavigate = { navigated += it }, viewModel = vm, logViewModel = log)
            }
        }
    }

    /** A calendar cell by the start of its description; the chart's bars start with the weekday instead. */
    private fun calendarDay(prefix: String) = compose.onNode(
        SemanticsMatcher("calendar day $prefix") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(prefix) } == true
        },
    )

    // ---- Insights ---------------------------------------------------------------------------------------

    @Test
    fun `a new user sees the empty state and the calendar, and can go to reading`() {
        openInsights()
        compose.onNodeWithText("Nothing read yet").assertIsDisplayed()
        compose.onNodeWithText("Calendar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start reading").performClick()
        assertEquals(listOf<Any>(ReadRoute), navigated)
    }

    @Test
    fun `the tiles follow the period switch`() {
        read(today, 1, 30)
        read(today.minusDays(20), 100, 220)
        openInsights()
        compose.onNodeWithContentDescription("Total ayahs, 30").assertIsDisplayed()
        compose.onNodeWithText("30 days").performClick()
        compose.onNodeWithContentDescription("Total ayahs, 151").assertIsDisplayed()
        compose.onNodeWithContentDescription("Active days, 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Current streak (days), 1").assertIsDisplayed()
    }

    @Test
    fun `tapping a day of the calendar opens its detail, and the future is not tappable`() {
        read(yesterday, 1, 60)
        openInsights()
        calendarDay("14 November, 60 ayahs").performScrollTo().performClick()
        assertEquals(listOf<Any>(DayDetailRoute("2026-11-14")), navigated)
        calendarDay("16 November").performScrollTo().performClick()
        assertEquals("a future day does nothing", 1, navigated.size)
    }

    @Test
    fun `the calendar moves between months`() {
        read(LocalDate.of(2026, 10, 18), 1, 200)
        openInsights()
        compose.onNodeWithText("November 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").performScrollTo().performClick()
        compose.onNodeWithText("October 2026").performScrollTo().assertIsDisplayed()
        calendarDay("18 October, 200 ayahs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        compose.onNodeWithText("November 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a revisit bookmark opens it in the reader`() {
        read(yesterday, 1, 7)
        val bookmark = runBlocking { app.library.create(io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts.surah(67, app.index).copy(label = "Mulk")) }
        openInsights()
        compose.onNodeWithText("Mulk").performScrollTo().performClick()
        assertEquals(listOf<Any>(ReaderRoute.bookmark(bookmark.id)), navigated)
    }

    // ---- Day detail -------------------------------------------------------------------------------------

    @Test
    fun `day detail shows the total and rank, and deleting a tally can be undone`() {
        read(yesterday, 1, 10)
        runBlocking { app.tracker.addTally(yesterday, 45, "Walk") }
        openDay(yesterday)
        compose.onNodeWithTag(DAY_TOTAL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DAY_TOTAL_TAG).assertTextEquals("55")
        compose.onNodeWithText("Walk").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Delete Walk").performScrollTo().performClick()
        compose.onNodeWithText("Entry deleted").assertIsDisplayed()
        compose.onNodeWithTag(DAY_TOTAL_TAG).assertTextEquals("10")
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Walk").performScrollTo().assertIsDisplayed()
        assertEquals(55, runBlocking { app.totals.total(yesterday) })
    }

    @Test
    fun `a run's count can be changed from its row`() {
        val surah = app.index.surah(112)
        read(yesterday, app.ayah(112, 1).value, app.ayah(112, 4).value)
        openDay(yesterday)
        compose.onNodeWithText("${surah.nameLatin} 1–4").performScrollTo().performClick()
        compose.onNodeWithText("Set count", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(8, runBlocking { app.totals.total(yesterday) })
        compose.onNodeWithText("${surah.nameLatin} 1–4 ×2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `deleting a run from its row removes it`() {
        val surah = app.index.surah(112)
        read(yesterday, app.ayah(112, 1).value, app.ayah(112, 4).value)
        openDay(yesterday)
        compose.onNodeWithContentDescription("Delete ${surah.nameLatin} 1–4").performScrollTo().performClick()
        assertEquals(0, runBlocking { app.totals.total(yesterday) })
        compose.onNodeWithText("Nothing read on this day").assertIsDisplayed()
    }

    @Test
    fun `a reading can be added to a past day through the log sheet`() {
        openDay(yesterday)
        compose.onNodeWithText("Nothing read on this day").assertIsDisplayed()
        compose.onNodeWithTag(DAY_ADD_READING_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(LOG_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("Just a number").performClick()
        compose.onNodeWithText("Number of ayahs").performTextInput("40")
        compose.onNodeWithText("Add 40 ayahs").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(40, runBlocking { app.totals.total(yesterday) })
        assertEquals(0, runBlocking { app.totals.today() })
        compose.onNodeWithText("Undo").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(0, runBlocking { app.totals.total(yesterday) })
    }

    @Test
    fun `a day in the future is shown without any way to change it`() {
        openDay(today.plusDays(1))
        compose.onNodeWithText("This day has not come yet").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithContentDescription("Add reading").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Add reading").fetchSemanticsNodes().isEmpty())
    }

    // ---- Log reading from Today -------------------------------------------------------------------------

    @Test
    fun `Today's Log reading button adds to today`() {
        val today = app.todayViewModel(Dispatchers.Unconfined)
        val log = app.logReadingViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { TodayScreen(onNavigate = {}, viewModel = today, logViewModel = { log }) } }
        compose.onNodeWithTag(TODAY_LOG_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(LOG_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("Add 1 ayah").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, today.state.value.rank.count)
        compose.onNodeWithText("1 ayah added to today").assertIsDisplayed()
    }
}
