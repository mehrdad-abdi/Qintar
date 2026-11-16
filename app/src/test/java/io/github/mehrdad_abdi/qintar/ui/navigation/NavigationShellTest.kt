package io.github.mehrdad_abdi.qintar.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.mehrdad_abdi.qintar.HiltTestActivity
import io.github.mehrdad_abdi.qintar.QintarApp
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The shell hosts the real Today and Reader screens, so it runs against the real Hilt graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = QintarApp::class)
class NavigationShellTest {

    @get:Rule
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navController: NavHostController

    private fun launch() {
        compose.setContent {
            navController = rememberNavController()
            QintarTheme { QintarAppShell(navController) }
        }
    }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    @Test
    fun `the app opens on Today with the four tabs`() {
        launch()
        tab("Today").assertIsSelected()
        tab("Read").assertIsDisplayed()
        tab("Bookmarks").assertIsDisplayed()
        tab("Insights").assertIsDisplayed()
    }

    @Test
    fun `tapping a tab selects it`() {
        launch()
        tab("Bookmarks").performClick()
        tab("Bookmarks").assertIsSelected()
        tab("Insights").performClick()
        tab("Insights").assertIsSelected()
    }

    @Test
    fun `the reader opens full screen with its arguments and Back returns to the tabs`() {
        launch()
        compose.runOnUiThread { navController.navigate(ReaderRoute.khatm(khatmId = 7, startAyahId = 1161)) }
        // There is no khatm 7 in a fresh install: the reader says so, full screen.
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText("Nothing to read here").fetchSemanticsNodes().isNotEmpty() }
        tab("Today").assertDoesNotExist()

        compose.onNodeWithContentDescription("Back").performClick()
        tab("Today").assertIsSelected()
    }

    @Test
    fun `every full-screen destination can be reached`() {
        launch()
        val routes: List<Pair<Any, String>> = listOf(
            BookmarkEditorRoute() to "New bookmark",
            BookmarkEditorRoute(bookmarkId = 12) to "This bookmark no longer exists.",
            GroupEditorRoute(5) to "This group no longer exists.",
            DayDetailRoute("2026-11-15") to "2026-11-15",
        )
        for ((route, shown) in routes) {
            compose.runOnUiThread { navController.navigate(route) }
            compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(shown).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(shown).assertIsDisplayed()
            compose.runOnUiThread { navController.popBackStack() }
        }
        compose.runOnUiThread { navController.navigate(SettingsRoute) }
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `the khatm screens are reachable through the real graph`() {
        launch()
        compose.runOnUiThread { navController.navigate(KhatmListRoute) }
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText("In progress").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { navController.popBackStack() }

        compose.runOnUiThread { navController.navigate(NewKhatmRoute) }
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText("Start khatm").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { navController.popBackStack() }

        compose.runOnUiThread { navController.navigate(KhatmDetailRoute(999)) }
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText("This khatm no longer exists").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the reader opens the bundled Quran through the real graph`() {
        launch()
        // Ayat al-Kursi as a random ayah: one row, with its check.
        compose.runOnUiThread { navController.navigate(ReaderRoute.random(firstAyahId = 262)) }
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithContentDescription("Mark read").fetchSemanticsNodes().size == 1 }

        // The ayah is taller than the test window and its check ends up beneath the floating pill,
        // so it is activated the way an accessibility service would.
        compose.onNodeWithContentDescription("Mark read").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithContentDescription("1 today", substring = true).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Back").performClick()
        tab("Today").assertIsSelected()
    }

    @Test
    fun `a tab route passed to onNavigate switches tab instead of stacking`() {
        launch()
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText("Open bookmarks").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Open bookmarks").performScrollTo().performClick()
        tab("Bookmarks").assertIsSelected()
        compose.runOnUiThread { check(navController.previousBackStackEntry != null) { "Today stays underneath" } }
    }

    @Test
    fun `finishing onboarding lands on Today and cannot be returned to`() {
        compose.setContent {
            navController = rememberNavController()
            QintarTheme { QintarAppShell(navController, startDestination = OnboardingRoute) }
        }
        compose.onNodeWithText("Welcome").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Today").assertIsSelected()
        compose.runOnUiThread { check(!navController.popBackStack()) { "onboarding is still on the back stack" } }
    }

    @Test
    @Config(qualifiers = "fa")
    fun `the tabs are translated in Persian`() {
        launch()
        tab("امروز").assertIsSelected()
        tab("نشان‌ها").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the tabs are translated in Arabic`() {
        launch()
        tab("اليوم").assertIsSelected()
        tab("الإحصاءات").assertIsDisplayed()
    }

    private companion object {
        /** Real threads load the index and the databases; generous for a cold CI machine. */
        const val WAIT_MILLIS = 30_000L
    }
}
