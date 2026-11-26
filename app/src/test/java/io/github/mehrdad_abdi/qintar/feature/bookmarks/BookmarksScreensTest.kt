package io.github.mehrdad_abdi.qintar.feature.bookmarks

import io.github.mehrdad_abdi.qintar.testing.arrangeViewModel
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.bookmarkEditorViewModel
import io.github.mehrdad_abdi.qintar.testing.bookmarksViewModel
import io.github.mehrdad_abdi.qintar.testing.groupEditorViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.GroupEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real bookmark screens, driven by taps and typing, over the in-memory domain. */
@RunWith(RobolectricTestRunner::class)
class BookmarksScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private val navigated = ArrayList<Any>()
    private var backs = 0

    private fun openTab() {
        val vm = app.bookmarksViewModel(Dispatchers.Unconfined)
        compose.setContent { QintarTheme { BookmarksScreen(onNavigate = { navigated += it }, viewModel = vm) } }
    }

    private fun seedMorning() = runBlocking {
        val group = app.library.createGroup("Morning", schedule = Schedule.daily(TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0))))
        app.library.create(
            BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = group.id, description = "Read after every prayer"),
        )
    }

    @Test
    fun `the order menu picks Quran order, My order or Newest first, and opens Arrange`() {
        seedMorning()
        openTab()
        compose.onNodeWithTag(BOOKMARKS_ORDER_TAG).performClick()
        compose.onNodeWithText("Newest first").performClick()
        compose.waitForIdle()
        assertEquals(io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.RECENT, app.prefs.current.bookmarkSort)

        compose.onNodeWithTag(BOOKMARKS_ORDER_TAG).performClick()
        compose.onNodeWithText("Arrange…").performClick()
        assertEquals(io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute(), navigated.single())
    }

    @Test
    fun `Arrange lists the titles, moves one with Move up, and a group opens its own Arrange`() {
        val (group, loose) = runBlocking {
            val g = app.library.createGroup("Night")
            app.library.create(BookmarkDrafts.ayah(app.ayah(36, 1)).copy(groupId = g.id))
            g to app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        }
        val vm = app.arrangeViewModel(io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute(), Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { ArrangeScreen(io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute(), onBack = {}, onNavigate = { navigated += it }, viewModel = vm) }
        }
        // Quran order: 2:255 first, then the group at 36:1.
        compose.onNodeWithText("1 bookmark").assertIsDisplayed()
        val node = compose.onNodeWithText("Night", substring = true).fetchSemanticsNode()
        compose.runOnIdle { node.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single { it.label == "Move up" }.action() }
        compose.waitForIdle()
        val top = runBlocking { app.availability.board().items }
        assertEquals(io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.CUSTOM, app.prefs.current.bookmarkSort)
        assertEquals(group.id, (top.first() as io.github.mehrdad_abdi.qintar.domain.service.BoardItem.Group).state.group.id)
        assertEquals(loose.id, (top.last() as io.github.mehrdad_abdi.qintar.domain.service.BoardItem.Loose).state.bookmark.id)

        compose.onNodeWithText("Night", substring = true).performClick()
        assertEquals(io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute(group.id), navigated.single())
    }

    @Test
    fun `the empty tab offers to add a bookmark`() {
        openTab()
        compose.onNodeWithText("No bookmarks yet").assertIsDisplayed()
        compose.onNodeWithTag(BOOKMARKS_READ_ALL_TAG).assertDoesNotExist()
        compose.onNodeWithText("Add bookmark").performClick()
        assertEquals(BookmarkEditorRoute(), navigated.single())
    }

    @Test
    fun `the list shows the group and its bookmark as cards, and a tap opens the reader`() {
        // Notes items 42 and 47 (owner, 2026-11-26): an index; reading happens in the reader.
        seedMorning()
        val group = runBlocking { app.bookmarkStore.groups().single() }
        val bookmark = runBlocking { app.bookmarkStore.bookmarks().single() }
        openTab()
        compose.onNodeWithText("Morning").assertIsDisplayed()
        compose.onNodeWithText("0 of 1 done").assertIsDisplayed()
        compose.onNodeWithText("All · 1").assertIsDisplayed()
        compose.onNodeWithText("Read after every prayer").assertIsDisplayed()
        compose.onNodeWithText("I read it").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Play", substring = true).assertCountEquals(0)

        compose.onNodeWithText("1 ayah").performClick()
        compose.onNodeWithText("Morning").performClick()
        compose.onNodeWithTag(BOOKMARKS_READ_ALL_TAG).performClick()
        assertEquals(listOf(ReaderRoute.bookmark(bookmark.id), ReaderRoute.group(group.id), ReaderRoute.bookmarks()), navigated)
        assertTrue("nothing is read in the list", runBlocking { app.log.allEvents().isEmpty() })
    }

    /**
     * The owner's phone (2026-11-27): 384dp wide, font 1.1×, Persian, 107 bookmarks. "Read all" sat
     * beside the filter chips and broke into three lines.
     */
    @Test
    @org.robolectric.annotation.Config(qualifiers = "fa-w384dp-h832dp")
    fun `Read all stays on one line on a phone in Persian with the font a little larger`() {
        runBlocking { repeat(107) { app.library.create(BookmarkDrafts.ayah(app.ayah(2, it + 1))) } }
        val vm = app.bookmarksViewModel(Dispatchers.Unconfined)
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 1.1f),
            ) { QintarTheme { BookmarksScreen(onNavigate = { navigated += it }, viewModel = vm) } }
        }
        val label = compose.onNodeWithText("خواندن همه", useUnmergedTree = true)
        val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
        label.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("one line", 1, layouts.single().lineCount)
    }

    @Test
    fun `a group's arrow shows or hides its bookmarks`() {
        val groupNote = "What the group is for. ".repeat(12).trim()
        runBlocking {
            val group = app.library.createGroup("Night", description = groupNote)
            app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = group.id))
        }
        openTab()
        compose.onNodeWithText("1 ayah").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide its bookmarks").assert(androidx.compose.ui.test.hasStateDescription("Expanded")).performClick()
        compose.onNodeWithText("1 ayah").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show its bookmarks").performClick()
        compose.onNodeWithText("1 ayah").assertIsDisplayed()
    }

    @Test
    fun `a bookmark's card shows its text, a long one by its start and end`() {
        val (short, long) = runBlocking {
            app.library.create(BookmarkDrafts.ayah(app.ayah(112, 1))) to app.library.create(BookmarkDrafts.surah(2, app.index))
        }
        openTab()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(GLIMPSE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
        val texts: List<List<String>> = compose.onAllNodesWithTag(GLIMPSE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { node ->
            node.children.flatMap { child ->
                child.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { line -> line.text }
            }
        }
        // Quran order: al-Baqara, then al-Ikhlas.
        val (baqara, ikhlas) = texts
        assertEquals("a short passage is shown whole, in one piece", 1, ikhlas.size)
        assertEquals("start …, then end", 2, baqara.size)
        assertTrue(baqara[0], baqara[0].endsWith("…"))
        assertTrue("short ${short.id}, long ${long.id}", baqara[1].isNotBlank())
    }

    @Test
    fun `Edit opens the editor and Delete offers Undo`() {
        seedMorning()
        openTab()
        compose.onAllNodesWithContentDescription("Bookmark actions")[0].performClick()
        compose.onNodeWithText("Edit").performClick()
        assertTrue(navigated.single() is BookmarkEditorRoute)

        compose.onAllNodesWithContentDescription("Bookmark actions")[0].performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Bookmark deleted").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(1, runBlocking { app.bookmarkStore.bookmarks().size })
    }

    @Test
    fun `Unread hides what is read today`() {
        seedMorning()
        val bookmark = runBlocking { app.bookmarkStore.bookmarks().single() }
        runBlocking {
            app.tracker.markRange(bookmark.range, io.github.mehrdad_abdi.qintar.domain.model.ReadContext.Bookmark(bookmark.id), io.github.mehrdad_abdi.qintar.domain.model.ReadMethod.BULK, io.github.mehrdad_abdi.qintar.domain.model.RangeMode.FILL)
        }
        openTab()
        compose.onNodeWithText("Unread · 0").assertIsDisplayed()
        compose.onNodeWithText("Unread ·", substring = true).performClick()
        compose.onNodeWithText("Every bookmark is read today.").assertIsDisplayed()
        compose.onNodeWithTag(BOOKMARKS_READ_ALL_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the editor previews the text and saves`() {
        val vm = app.bookmarkEditorViewModel(BookmarkEditorRoute.forAyah(app.ayah(2, 255).value), Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { BookmarkEditorScreen(BookmarkEditorRoute(), onBack = { backs++ }, onNavigate = { navigated += it }, viewModel = vm) }
        }
        compose.onNodeWithTag(EDITOR_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithText("ayah 2:255", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Description").performTextInput("Why I read it")
        compose.onNodeWithTag(EDITOR_SAVE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals("Why I read it", runBlocking { app.bookmarkStore.bookmarks().single().description })
    }

    @Test
    fun `the editor shows an error on the ayah field`() {
        val vm = app.bookmarkEditorViewModel(BookmarkEditorRoute(), Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { BookmarkEditorScreen(BookmarkEditorRoute(), onBack = { backs++ }, onNavigate = {}, viewModel = vm) }
        }
        vm.onAyahText("9")
        compose.waitForIdle()
        compose.onNodeWithText("That surah has no such ayah.").assertIsDisplayed()
        compose.onNodeWithTag(EDITOR_SAVE_TAG).performClick()
        assertEquals(0, backs)
    }

    @Test
    fun `the group editor saves a named group`() {
        val vm = app.groupEditorViewModel(GroupEditorRoute(), Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { GroupEditorScreen(GroupEditorRoute(), onBack = { backs++ }, onNavigate = {}, viewModel = vm) }
        }
        compose.onNodeWithTag(GROUP_SAVE_TAG).performClick()
        compose.onNodeWithText("Give the group a name.").assertIsDisplayed()
        compose.onNodeWithText("Name").performTextInput("Friday")
        compose.onNodeWithTag(GROUP_SAVE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals("Friday", runBlocking { app.bookmarkStore.groups().single().name })
    }

    @Test
    fun `the group editor's bulk box marks a wrong line, then creates the bookmarks`() {
        val vm = app.groupEditorViewModel(GroupEditorRoute(), Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { GroupEditorScreen(GroupEditorRoute(), onBack = { backs++ }, onNavigate = {}, viewModel = vm) }
        }
        compose.onNodeWithText("Name").performTextInput("Night")
        compose.onNodeWithTag(GROUP_BULK_TAG).performScrollTo().performTextInput("s67\nj12")
        compose.onNodeWithTag(GROUP_SAVE_TAG).performClick()
        compose.onNodeWithText("Not a bookmark: line 2").performScrollTo().assertIsDisplayed()
        assertEquals(0, backs)

        compose.onNodeWithTag(GROUP_BULK_TAG).performTextClearance()
        compose.onNodeWithTag(GROUP_BULK_TAG).performTextInput("s67\n2:255")
        compose.onNodeWithTag(GROUP_SAVE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(2, runBlocking { app.bookmarkStore.bookmarks() }.count { it.groupId != null })
    }
}
