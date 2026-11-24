package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun `the list keeps the screen on, as the reader does, when the setting is on`() {
        seedMorning()
        var view: android.view.View? = null
        val vm = app.bookmarksViewModel(Dispatchers.Unconfined)
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            QintarTheme { BookmarksScreen(onNavigate = {}, viewModel = vm) }
        }
        compose.waitForIdle()
        org.junit.Assert.assertTrue(view!!.keepScreenOn)

        runBlocking { app.prefs.update { it.copy(keepScreenOn = false) } }
        compose.waitForIdle()
        org.junit.Assert.assertFalse(view!!.keepScreenOn)
    }

    @Test
    fun `the empty tab offers to add a bookmark`() {
        openTab()
        compose.onNodeWithText("No bookmarks yet").assertIsDisplayed()
        compose.onNodeWithText("Add bookmark").performClick()
        assertEquals(BookmarkEditorRoute(), navigated.single())
    }

    @Test
    fun `the list shows the group, its bookmark and description, a tap opens its ayahs with Mark read`() {
        seedMorning()
        openTab()
        compose.onNodeWithText("Morning").assertIsDisplayed()
        compose.onNodeWithText("0 of 1 done").assertIsDisplayed()
        compose.onNodeWithText("All · 1").assertIsDisplayed()
        compose.onNodeWithText("Read after every prayer").assertIsDisplayed()
        compose.onNodeWithText("Mark read").assertDoesNotExist()
        compose.onNodeWithText("1 ayah").performClick()
        compose.onNodeWithText("Mark read").performClick()
        compose.waitForIdle()
        assertEquals(1, runBlocking { app.log.allEvents().size })
        assertTrue("nothing navigates: the bookmark is read in place", navigated.isEmpty())
    }

    @Test
    fun `a tap on a long description opens the bookmark or folds the group, it does not just expand the text`() {
        val long = "A long note about why this is read. ".repeat(12).trim()
        val groupNote = "What the group is for. ".repeat(12).trim()
        runBlocking {
            val group = app.library.createGroup("Night", description = groupNote)
            app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = group.id, description = long))
        }
        openTab()

        // The bookmark: a tap on its description opens its ayahs (and shows the whole description).
        val card = compose.onNodeWithText("1 ayah", substring = true)
        card.performScrollTo().assert(androidx.compose.ui.test.hasStateDescription("Collapsed"))
        compose.onNodeWithText(long, substring = true).performScrollTo().performClick()
        card.assert(androidx.compose.ui.test.hasStateDescription("Expanded"))

        // The group: a tap on its description folds it.
        compose.onNodeWithText(groupNote).performScrollTo().performClick()
        compose.onNodeWithText("1 ayah", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Open in reader is in the bookmark's menu`() {
        seedMorning()
        val bookmark = runBlocking { app.bookmarkStore.bookmarks().single() }
        openTab()
        compose.onAllNodesWithContentDescription("Bookmark actions")[0].performClick()
        compose.onNodeWithText("Open in reader").performClick()
        assertEquals(ReaderRoute.bookmark(bookmark.id), navigated.single())
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
        openTab()
        compose.onNodeWithText("1 ayah").performClick()
        compose.onNodeWithText("Mark read").performClick()
        compose.onNodeWithText("Unread · 0").assertIsDisplayed()
        compose.onNodeWithText("Unread ·", substring = true).performClick()
        compose.onNodeWithText("Every bookmark is read today.").assertIsDisplayed()
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
