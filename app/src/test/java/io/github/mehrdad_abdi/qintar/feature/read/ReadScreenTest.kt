package io.github.mehrdad_abdi.qintar.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.readViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmListRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.NewKhatmRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real Read tab, driven by taps and typing, over the in-memory domain. */
@RunWith(RobolectricTestRunner::class)
class ReadScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private val navigated = ArrayList<Any>()

    /** Lazy lists compose only what is near the viewport: bring [text] there first. */
    private fun scrollTo(text: String) {
        compose.onNodeWithTag(READ_LIST_TAG).performScrollToNode(hasText(text))
    }

    private fun open() {
        val viewModel = app.readViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { ReadScreen(onNavigate = { navigated += it }, viewModel = viewModel) } }
    }

    @Test
    fun `typing a place and pressing Go opens the reader there`() {
        open()
        compose.onNodeWithText("Go to…").performTextInput("2:255")
        compose.onNodeWithText("Go").performClick()

        assertEquals(listOf<Any>(ReaderRoute.browse(app.ayah(2, 255).value)), navigated)
    }

    @Test
    fun `a bare ayah number without a surah shows the reason and goes nowhere`() {
        open()
        compose.onNodeWithText("Go to…").performTextInput("255")
        compose.onNodeWithText("Go").performClick()

        compose.onNodeWithText("Choose a surah first, or type surah:ayah.").assertIsDisplayed()
        assertTrue(navigated.isEmpty())
    }

    @Test
    fun `a bare ayah number goes into the surah chosen beside the field`() {
        open()
        compose.onNodeWithText("No surah chosen").performClick()
        compose.onNodeWithText("2. Surah 2").performClick()
        compose.onNodeWithText("Go to…").performTextInput("255")
        compose.onNodeWithText("Go").performClick()

        assertEquals(listOf<Any>(ReaderRoute.browse(app.ayah(2, 255).value)), navigated)
    }

    @Test
    fun `the random buttons open the reader on a random ayah and on a whole random page`() {
        open()
        compose.onNodeWithText("Random ayah").performClick()
        compose.onNodeWithText("Random page").performClick()

        val (ayah, page) = navigated.map { it as ReaderRoute }
        assertEquals(ReaderEntry.RANDOM, ayah.entry)
        assertEquals(ayah.scopeFirstAyahId, ayah.scopeLastAyahId)
        assertEquals(ReaderEntry.RANDOM, page.entry)
        val pageNumber = app.index.pageOf(io.github.mehrdad_abdi.qintar.domain.model.AyahId(page.scopeFirstAyahId))
        assertEquals(app.index.pageRange(pageNumber).end.value, page.scopeLastAyahId)
    }

    @Test
    fun `the three indexes open the reader at their start`() {
        open()
        scrollTo("Surah 18")
        compose.onNodeWithText("Surah 18").performClick()
        scrollTo("Juz")
        compose.onNodeWithText("Juz").performClick()
        scrollTo("Juz 5")
        compose.onNodeWithText("Juz 5").performClick()
        scrollTo("Page")
        compose.onNodeWithText("Page").performClick()
        scrollTo("300")
        compose.onNodeWithText("300").performClick()

        assertEquals(
            listOf<Any>(
                ReaderRoute.browse(app.index.surahRange(18).start.value),
                ReaderRoute.browse(app.index.juzRange(5).start.value),
                ReaderRoute.browse(app.index.pageRange(300).start.value),
            ),
            navigated,
        )
    }

    @Test
    fun `continue browsing appears once there is a position, and khatm links lead to the khatm screens`() {
        val khatm = runBlocking {
            app.prefs.update { it.copy(lastBrowseAyahId = app.ayah(18, 10)) }
            app.khatms.start("Ramadan")
        }
        open()

        compose.onNodeWithText("Continue browsing").performClick()
        compose.onNodeWithText("All khatms").performClick()
        scrollTo("New khatm")
        compose.onNodeWithText("New khatm").performClick()
        scrollTo("Ramadan")
        compose.onNodeWithText("Ramadan").performClick()

        assertEquals(
            listOf(ReaderRoute.browse(app.ayah(18, 10).value), KhatmListRoute, NewKhatmRoute, KhatmDetailRoute(khatm.id)),
            navigated,
        )
    }
}
