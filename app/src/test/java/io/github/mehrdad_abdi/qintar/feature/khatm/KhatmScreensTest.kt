package io.github.mehrdad_abdi.qintar.feature.khatm

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.khatmDetailViewModel
import io.github.mehrdad_abdi.qintar.testing.khatmListViewModel
import io.github.mehrdad_abdi.qintar.testing.newKhatmViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.NewKhatmRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real khatm screens, driven by taps and typing, over the in-memory domain. */
@RunWith(RobolectricTestRunner::class)
class KhatmScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private val navigated = ArrayList<Any>()
    private var backs = 0

    private fun openList() {
        val viewModel = app.khatmListViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { KhatmListScreen(onBack = { backs++ }, onNavigate = { navigated += it }, viewModel = viewModel) } }
    }

    private fun openDetail(id: Long) {
        val viewModel = app.khatmDetailViewModel(id, Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { KhatmDetailScreen(KhatmDetailRoute(id), onBack = { backs++ }, onNavigate = { navigated += it }, viewModel = viewModel) }
        }
    }

    @Test
    fun `the list shows each khatm in progress with its goal and the finished ones apart`() {
        val (personal, ramadan) = runBlocking {
            val done = app.khatms.start("Old one")
            app.tracker.markRange(AyahRange.ALL, ReadContext.Khatm(done.id), ReadMethod.BULK, RangeMode.FILL)
            app.khatms.start("Personal") to app.khatms.start("Ramadan", dailyPages = 4f)
        }
        openList()

        compose.onNodeWithText("Personal").assertIsDisplayed()
        compose.onNodeWithText("Ramadan").assertIsDisplayed()
        compose.onNodeWithText("4 pages a day").assertIsDisplayed()
        compose.onNodeWithTag(KHATM_LIST_TAG).performScrollToNode(hasText("Old one"))
        compose.onNodeWithText("Old one").assertIsDisplayed()
        compose.onNodeWithText("Completed").assertIsDisplayed()

        compose.onNodeWithTag(KHATM_LIST_TAG).performScrollToNode(hasText("Ramadan"))
        compose.onNodeWithText("Ramadan").performClick()
        compose.onAllNodesWithText("Continue")[0].performClick()
        compose.onNodeWithText("New khatm").performClick()

        assertEquals(KhatmDetailRoute(ramadan.id), navigated[0])
        assertTrue(navigated[1] is ReaderRoute)
        assertEquals(NewKhatmRoute, navigated[2])
        assertEquals(setOf(personal.id, ramadan.id), runBlocking { app.khatmStore.khatms() }.filter { it.completedAt == null }.map { it.id }.toSet())
    }

    @Test
    fun `an empty list invites the first khatm`() {
        openList()
        compose.onNodeWithText("Start a khatm").performClick()
        assertEquals(listOf<Any>(NewKhatmRoute), navigated)
    }

    @Test
    fun `the form starts a khatm with a default name, a position and a goal, then opens it`() {
        val viewModel = app.newKhatmViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { NewKhatmScreen(onBack = { backs++ }, onNavigate = { navigated += it }, viewModel = viewModel) } }

        compose.onNodeWithText("Khatm 1").assertIsDisplayed()
        compose.onNodeWithText("Start from (optional)").performTextInput("2:255")
        compose.onNodeWithText("Starts at 2:255").assertIsDisplayed()
        compose.onNodeWithText("Pages per day (optional)").performTextInput("4")
        compose.onNodeWithText("Start khatm").performClick()

        val khatm = runBlocking { app.khatmStore.khatms() }.single()
        assertEquals("Khatm 1", khatm.name)
        assertEquals(4f, khatm.dailyPages)
        assertEquals(app.ayah(2, 255).value - 1, khatm.assumedCoveredUpTo)
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(listOf<Any>(KhatmDetailRoute(khatm.id)), navigated)
    }

    @Test
    fun `the form explains a position it cannot use`() {
        val viewModel = app.newKhatmViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { NewKhatmScreen(onBack = {}, onNavigate = {}, viewModel = viewModel) } }

        compose.onNodeWithText("Start from (optional)").performTextInput("p999")
        compose.onNodeWithText("Pages go from 1 to 604.").assertIsDisplayed()
    }

    @Test
    fun `the detail has the goal bar only with a goal, and Continue opens the reader`() {
        val (plain, goal) = runBlocking { app.khatms.start("Plain") to app.khatms.start("Goal", dailyPages = 4f) }

        openDetail(plain.id)
        compose.onNodeWithContentDescription("Whole Quran, 0%").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Today, 0 of 4 pages")).assertCountEquals(0)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf<Any>(ReaderRoute.khatm(plain.id)), navigated)
        assertTrue(goal.id != plain.id)
    }

    @Test
    fun `the detail with a goal shows today's pages against it`() {
        val goal = runBlocking { app.khatms.start("Goal", dailyPages = 4f) }
        openDetail(goal.id)
        compose.onNodeWithContentDescription("Today, 0 of 4 pages").assertIsDisplayed()
    }

    @Test
    fun `rename, change the goal and delete from the menu`() {
        val khatm = runBlocking { app.khatms.start("Old") }
        openDetail(khatm.id)

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Fresh")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Fresh").assertIsDisplayed()

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Change daily goal").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("3")
        compose.onNodeWithText("Save").performClick()
        assertEquals(3f, runBlocking { app.khatmStore.khatms() }.single().dailyPages)

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this khatm?").assertIsDisplayed()
        assertEquals("nothing is deleted before confirming", 1, runBlocking { app.khatmStore.khatms() }.size)
        compose.onNodeWithText("Delete").performClick()

        compose.waitForIdle()
        assertTrue(runBlocking { app.khatmStore.khatms() }.isEmpty())
        assertEquals("the screen leaves", 1, backs)
    }

    @Test
    fun `a completed khatm shows its summary and offers a new one`() {
        val khatm = runBlocking {
            app.khatms.start("Whole").also {
                app.tracker.markRange(AyahRange.ALL, ReadContext.Khatm(it.id), ReadMethod.BULK, RangeMode.FILL)
            }
        }
        openDetail(khatm.id)

        compose.onNodeWithText("Khatm complete").assertIsDisplayed()
        compose.onNodeWithText("Completed in 1 days").assertIsDisplayed()
        compose.onNodeWithText("Start a new khatm").performClick()
        assertEquals(listOf<Any>(NewKhatmRoute), navigated)
    }
}
