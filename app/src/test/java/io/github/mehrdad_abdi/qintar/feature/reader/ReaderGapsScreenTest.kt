package io.github.mehrdad_abdi.qintar.feature.reader

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reader's position sheet, Mark page, bookmark header, first-unread chip and Aa panel, by taps. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ReaderGapsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()

    private fun open(route: ReaderRoute) {
        val viewModel = app.readerViewModel(route, Dispatchers.Unconfined)
        compose.setContent { QintarTheme { ReaderScreen(route, onBack = {}, onNavigate = {}, viewModel = viewModel) } }
    }

    @Test
    fun `the position bar opens a sheet whose go-to field explains what it cannot do`() {
        open(ReaderRoute.browse(app.ayah(1, 1).value))

        compose.onNodeWithContentDescription("Change position").performClick()
        compose.onNodeWithText("Jump to").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("2:999")
        compose.onNodeWithText("Go").performClick()
        compose.onNodeWithText("That surah has no such ayah.").assertIsDisplayed()

        compose.onAllNodesWithText("Jump to").assertCountEquals(1)
    }

    @Test
    fun `Mark page on a divider reads the page above and offers Undo`() {
        open(ReaderRoute.browse(app.ayah(2, 1).value))
        val page = app.index.pageOf(app.ayah(2, 1))

        // Scrolled to the top of the list, clear of the floating pill.
        val model = ReaderRowModel.build(listOf(Segment(ReadContext.Browse, AyahRange.ALL)), app.index)
        compose.onNodeWithTag(READER_LIST_TAG).performScrollToIndex(model.rows.indexOfFirst { it is ReaderRow.PageDivider } - 1)
        compose.onNodeWithText("I finished page $page").performClick()
        compose.onNodeWithText("${app.index.pageRange(page).size} ayahs marked").assertIsDisplayed()
        assertEquals(app.index.pageRange(page).size, app.log.events.size)

        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertTrue(app.log.events.isEmpty())
    }

    @Test
    fun `a bookmark opens under a header with its title only, and is marked read by I read it at its end`() {
        // Owner, 2026-11-27: the header's "Log a full reading" repeated the "I read it" at the end.
        val bookmark = runBlocking {
            app.library.create(
                BookmarkDrafts.surah(112, app.index).copy(label = "Ikhlas", description = "A third of the Quran", repeatTarget = 3),
            )
        }
        open(ReaderRoute.bookmark(bookmark.id))

        // Owner, 2026-11-27: the header is the title only; the description is on the Bookmarks list,
        // and "0 / 1 today" took room for little.
        compose.onAllNodesWithText("Ikhlas").assertCountEquals(2) // the top bar, and the header
        compose.onNodeWithText("A third of the Quran").assertDoesNotExist()
        compose.onNodeWithContentDescription("0 / 3 today, 0%").assertDoesNotExist()
        compose.onNodeWithText("Log a full reading").assertDoesNotExist()
        compose.onNodeWithTag(READER_LIST_TAG).performScrollToNode(androidx.compose.ui.test.hasText("I read it"))
        // Its own click: scrolled to the bottom edge, it sits under the floating pill.
        compose.onNodeWithText("I read it").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)

        assertEquals(4, app.log.events.size)
        assertTrue(app.log.events.all { it.context == ReadContext.Bookmark(bookmark.id) })
        compose.onNodeWithText("4 ayahs marked").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertTrue(app.log.events.isEmpty())
    }

    @Test
    fun `the khatm reader shows a First unread chip only while it is elsewhere`() {
        val khatm = runBlocking { app.khatms.start("K") }
        runBlocking { app.tracker.mark(AyahId(30), ReadContext.Khatm(khatm.id), ReadMethod.TAP) }
        open(ReaderRoute.khatm(khatm.id))

        compose.onNodeWithText("First unread").assertIsDisplayed()
        compose.onNodeWithText("First unread").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `no chip in a khatm that has nothing out of order`() {
        val khatm = runBlocking {
            app.khatms.start("K").also { app.tracker.markRange(AyahRange.of(1, 5), ReadContext.Khatm(it.id), ReadMethod.BULK, RangeMode.FILL) }
        }
        open(ReaderRoute.khatm(khatm.id))
        compose.onAllNodesWithText("First unread").assertCountEquals(0)
    }

    @Test
    fun `the Aa panel changes the text settings and they are stored`() {
        open(ReaderRoute.browse(app.ayah(1, 1).value))

        compose.onNodeWithContentDescription("Text settings").performClick()
        compose.onNodeWithText("Quran font size").assertIsDisplayed()
        compose.onNodeWithContentDescription("Break lines at pause marks").performClick()
        compose.onNodeWithContentDescription("Keep screen on").performClick()

        assertFalse(app.prefs.current.breakAtPauseMarks)
        assertFalse(app.prefs.current.keepScreenOn)
    }
}
