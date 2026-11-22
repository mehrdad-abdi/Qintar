package io.github.mehrdad_abdi.qintar.feature.reader

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real reader screen, driven by taps, over the in-memory domain. */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window: the surah header and basmala stand above the first ayah of a surah.
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private var backs = 0
    private val navigated = ArrayList<Any>()

    private fun open(route: ReaderRoute) {
        val viewModel = app.readerViewModel(route, Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { ReaderScreen(route, onBack = { backs++ }, onNavigate = { navigated += it }, viewModel = viewModel) }
        }
    }

    /** The check of the n-th ayah on screen. */
    private fun check(n: Int) = compose.onAllNodesWithContentDescription("Mark read")[n]
    private fun menu(n: Int) = compose.onAllNodesWithContentDescription("More actions")[n]

    @Test
    fun `the reader opens at the requested ayah with the surah in the title and the pill at zero`() {
        open(ReaderRoute.browse(app.ayah(2, 1).value))

        compose.onAllNodesWithText("Surah 2").assertCountEquals(2) // the title, and the surah header above ayah 1
        compose.onNodeWithContentDescription("Surah 2, ayah 1, not read today").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 today, 10 ayahs to the first rank").assertIsDisplayed()
        assertTrue("every ayah offers its recitation", compose.onAllNodesWithContentDescription("Play").fetchSemanticsNodes().isNotEmpty())

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `tapping the check counts a reading each time and moves the pill`() {
        open(ReaderRoute.browse(app.ayah(2, 1).value))

        check(0).performClick()
        compose.onNode(hasContentDescription("Mark read") and hasStateDescription("read today")).assertIsDisplayed()

        check(0).performClick()

        compose.onNode(hasContentDescription("Mark read") and hasStateDescription("read 2 times today")).assertIsDisplayed()
        compose.onNodeWithContentDescription("2 today, 8 ayahs to the first rank").assertIsDisplayed()
        assertEquals(listOf(app.ayah(2, 1), app.ayah(2, 1)), app.log.events.map { it.ayahId })
    }

    @Test
    fun `set count from the row menu`() {
        open(ReaderRoute.browse(app.ayah(2, 1).value))

        menu(0).performClick()
        compose.onNodeWithText("Set count…").performClick()
        compose.onNodeWithText("Surah 2 1").assertIsDisplayed()
        repeat(3) { compose.onNodeWithContentDescription("Increase").performClick() }
        compose.onNodeWithText("Save").performClick()

        compose.onNode(hasContentDescription("Mark read") and hasStateDescription("read 3 times today")).assertIsDisplayed()
        assertEquals(3, app.log.events.size)
    }

    @Test
    fun `mark up to here shows how many were marked and Undo removes them`() {
        open(ReaderRoute.browse(app.ayah(2, 1).value))

        menu(1).performClick()
        compose.onNodeWithText("Mark up to here").performClick()

        compose.onNodeWithText("2 ayahs marked").assertIsDisplayed()
        assertEquals(2, app.log.events.size)
        assertTrue(app.log.events.all { it.method == ReadMethod.BULK })

        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()

        assertTrue(app.log.events.isEmpty())
        compose.onNodeWithContentDescription("0 today, 10 ayahs to the first rank").assertIsDisplayed()
    }

    @Test
    fun `in a khatm a tick that skips ahead offers to mark the gap`() {
        val khatm = runBlocking { app.khatms.start("Ramadan khatm") }
        open(ReaderRoute.khatm(khatm.id))
        compose.onNodeWithText("Ramadan khatm").assertIsDisplayed()

        check(1).performClick()

        compose.onNodeWithText("Mark all 2 ayahs from Surah 1 1 to here as read?").assertIsDisplayed()
        assertTrue("nothing written yet", app.log.events.isEmpty())

        compose.onNodeWithText("Mark 2 ayahs").performClick()

        assertEquals(listOf(1, 2), app.log.events.map { it.ayahId.value })
        assertTrue(app.log.events.all { it.context == ReadContext.Khatm(khatm.id) })
    }

    @Test
    fun `only this ayah and cancel are the other two answers`() {
        val khatm = runBlocking { app.khatms.start("Khatm") }
        open(ReaderRoute.khatm(khatm.id))

        check(1).performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(app.log.events.isEmpty())

        check(1).performClick()
        compose.onNodeWithText("Only this ayah").performClick()

        assertEquals(listOf(2), app.log.events.map { it.ayahId.value })
    }

    @Test
    fun `the tenth reading of the day shows the rank in the pill`() {
        runBlocking { app.tracker.setCount(app.ayah(2, 1), ReadContext.Browse, app.clock.today(), 9) }
        open(ReaderRoute.browse(app.ayah(2, 1).value))

        check(0).performClick()

        compose.onNodeWithContentDescription("Not heedless reached, 10 today").assertIsDisplayed()
    }

    @Test
    fun `the star opens the bookmark editor for the ayah`() {
        open(ReaderRoute.browse(app.ayah(2, 1).value))

        compose.onAllNodesWithContentDescription("Bookmark")[0].performClick()

        assertEquals(io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute.forAyah(app.ayah(2, 1).value), navigated.last())
        assertTrue("nothing saved until the editor saves", runBlocking { app.bookmarkStore.bookmarks() }.isEmpty())
    }
}
