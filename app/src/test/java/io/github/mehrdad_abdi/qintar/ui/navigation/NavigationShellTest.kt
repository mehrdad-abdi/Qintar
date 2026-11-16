package io.github.mehrdad_abdi.qintar.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NavigationShellTest {

    @get:Rule
    val compose = createComposeRule()

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
        compose.onNodeWithText("KHATM id=7 start=1161").assertIsDisplayed()
        tab("Today").assertDoesNotExist()

        compose.onNodeWithContentDescription("Back").performClick()
        tab("Today").assertIsSelected()
    }

    @Test
    fun `every full-screen destination can be reached`() {
        launch()
        val routes: List<Pair<Any, String>> = listOf(
            KhatmDetailRoute(3) to "id=3",
            BookmarkEditorRoute() to "id=0",
            BookmarkEditorRoute(bookmarkId = 12) to "id=12",
            GroupEditorRoute(5) to "id=5",
            DayDetailRoute("2026-11-15") to "2026-11-15",
            ReaderRoute.random(firstAyahId = 262) to "RANDOM id=0 start=262",
            ReaderRoute.group(4) to "GROUP id=4 start=0",
        )
        for ((route, shown) in routes) {
            compose.runOnUiThread { navController.navigate(route) }
            compose.onNodeWithText(shown).assertIsDisplayed()
            compose.runOnUiThread { navController.popBackStack() }
        }
        compose.runOnUiThread { navController.navigate(SettingsRoute) }
        compose.onNodeWithText("Settings").assertIsDisplayed()
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
}
