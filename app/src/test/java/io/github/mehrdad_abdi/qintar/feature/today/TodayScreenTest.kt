package io.github.mehrdad_abdi.qintar.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.todayViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarksRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.InsightsRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReadRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.SettingsRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real Today screen, driven by taps, over the in-memory domain. */
@RunWith(RobolectricTestRunner::class)
class TodayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private val navigated = ArrayList<Any>()

    private fun open() {
        val viewModel = app.todayViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { TodayScreen(onNavigate = { navigated += it }, viewModel = viewModel) } }
    }

    @Test
    fun `a new user is invited to start a khatm, and can continue it at once`() {
        open()
        compose.onNodeWithText("Sunday 15 November").assertIsDisplayed()
        compose.onNodeWithText("10 ayahs to the first rank").assertIsDisplayed()
        compose.onNodeWithText("No streak yet").assertIsDisplayed()

        compose.onNodeWithText("Start a khatm").performScrollTo().performClick()

        compose.onNodeWithText("My khatm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        val khatm = runBlocking { app.khatmStore.khatms() }.single()
        assertEquals(listOf<Any>(ReaderRoute.khatm(khatm.id)), navigated)
    }

    @Test
    fun `the empty routine card leads to Bookmarks, and the bottom button to Read`() {
        open()

        compose.onNodeWithText("Open bookmarks").performScrollTo().performClick()
        compose.onNodeWithText("Browse the Quran").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
        compose.onNodeWithText("No streak yet").performScrollTo().performClick()

        assertEquals(listOf(BookmarksRoute, ReadRoute, SettingsRoute, InsightsRoute), navigated)
    }

    @Test
    fun `with many unread bookmarks Today lists five and Show all opens Bookmarks`() {
        runBlocking { for (a in 1..8) app.library.create(io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts.ayah(app.ayah(2, a))) }
        open()
        compose.onNodeWithText("8 left").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Surah 2 5", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Surah 2 6", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Show all").performScrollTo().performClick()
        assertEquals(listOf<Any>(BookmarksRoute), navigated)
    }

    @Test
    fun `today's readings opens the list of today's readings`() {
        open()
        compose.onNodeWithTag(TODAY_READINGS_TAG).performScrollTo().performClick()
        assertEquals(listOf<Any>(io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute("2026-11-15")), navigated)
    }

    @Test
    fun `mid-day shows the count, the rank and the streak, and due bookmarks open the reader`() {
        val (group, kahf) = runBlocking {
            app.tracker.markRange(AyahRange.of(1, 47), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
            val group = app.library.createGroup("Morning", schedule = Schedule.daily())
            app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = group.id, label = "Ayat al-Kursi"))
            group to app.library.create(BookmarkDrafts.surah(18, app.index).copy(schedule = Schedule.daily()))
        }
        open()

        compose.onNodeWithText("47").assertIsDisplayed()
        compose.onNodeWithText("3 to Dhakir").assertIsDisplayed()
        compose.onNodeWithContentDescription("Not among the heedless").assertIsDisplayed()
        compose.onNodeWithText("1-day streak").assertIsDisplayed()
        compose.onNodeWithText("2 left").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Surah 18").performScrollTo().performClick()
        compose.onNodeWithText("Read").performScrollTo().performClick()

        assertEquals(listOf<Any>(ReaderRoute.bookmark(kahf.id), ReaderRoute.group(group.id)), navigated)
    }

    @Test
    fun `a streak that today has not yet kept says so`() {
        runBlocking { app.tracker.markRange(AyahRange.of(1, 10), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD) }
        app.clock.advanceDays(1)
        open()

        compose.onNodeWithText("1-day streak · read today to keep it").assertIsDisplayed()
    }
}
