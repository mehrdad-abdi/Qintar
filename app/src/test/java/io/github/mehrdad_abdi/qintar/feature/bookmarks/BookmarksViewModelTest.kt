package io.github.mehrdad_abdi.qintar.feature.bookmarks

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.bookmarksViewModel
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Bookmarks tab's use cases through its view model over the in-memory domain. */
class BookmarksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture() // Sunday 15 Nov 2026, 09:00
    private val morning = Schedule.daily(TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0)))

    private fun open() = app.bookmarksViewModel(main.dispatcher)

    private suspend fun kahf(schedule: Schedule? = morning, target: Int = 1, groupId: Long? = null) =
        app.library.create(BookmarkDrafts.surah(112, app.index).copy(schedule = schedule, repeatTarget = target, groupId = groupId))

    // ---- the list (§5.6) ---------------------------------------------------------------------------

    private fun BookmarksUiState.sections() = rows.filterIsInstance<BookmarksRow.Section>()
    private fun BookmarksUiState.entries() = rows.filterIsInstance<BookmarksRow.Entry>().map { it.item.id }

    /** Counts the ayahs read from the Quran text, to show what a screen loads. */
    private class CountingText(private val inner: QuranTextSource) : QuranTextSource {
        var ayahsRead = 0
        override suspend fun ayahs(range: AyahRange) = inner.ayahs(range).also { ayahsRead += it.size }
        override suspend fun page(page: Int) = inner.page(page).also { ayahsRead += it.size }
    }

    @Test
    fun `bookmarks without a group stand among the groups, with no Ungrouped section, in the chosen order`() = runTest {
        // Owner, 2026-11-25. Quran order is the default: 2:255 comes before the group at surah 112.
        val morning = app.library.createGroup("Morning")
        val inGroup = kahf(groupId = morning.id)
        app.clock.advanceMinutes(1)
        val loose = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        val vm = open()
        val state = vm.state.value
        assertEquals(listOf(morning.id), state.sections().map { it.sectionKey })
        assertTrue(state.sections().all { it.expanded })
        assertEquals(BookmarksRow.Entry(state.all.first { it.id == loose.id }, inGroup = false), state.rows[0])
        assertEquals(listOf(loose.id, inGroup.id), state.entries())
        assertTrue("the group's bookmark is indented under it", (state.rows.last() as BookmarksRow.Entry).inGroup)

        vm.onSort(io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.RECENT)
        assertEquals("newest first: the loose bookmark was added last", listOf(loose.id, inGroup.id), vm.state.value.entries())
        vm.onSort(io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.CUSTOM)
        assertEquals(io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.CUSTOM, app.prefs.current.bookmarkSort)
        assertEquals(io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.CUSTOM, vm.state.value.sort)
    }

    @Test
    fun `the list is an index of groups and bookmarks, and a group folds its bookmarks`() = runTest {
        // Notes item 47 (owner, 2026-11-26): ayahs are read in the reader, not in the list.
        val g = app.library.createGroup("G")
        val b = kahf(groupId = g.id)
        val vm = open()
        assertEquals(listOf("section-${g.id}", "bookmark-${b.id}"), vm.state.value.rows.map { it.key })

        vm.onToggleSection(g.id)
        assertEquals(listOf(g.id), vm.state.value.sections().map { it.sectionKey })
        assertTrue(vm.state.value.entries().isEmpty())
        assertEquals("a folded group's bookmarks are still read by Read all", listOf(b.id), vm.state.value.listed.map { it.id })

        vm.onToggleSection(g.id)
        assertEquals(listOf(b.id), vm.state.value.entries())
    }

    // ---- the glimpse of the text (owner, 2026-11-26) --------------------------------------------

    @Test
    fun `a short passage's glimpse is all of it`() = runTest {
        val kursi = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        val vm = open()
        vm.onGlimpseNeeded(kursi.id)
        val glimpse = vm.state.value.glimpses.getValue(kursi.range)
        assertNull("nothing left out", glimpse.tail)
        assertTrue(glimpse.head.startsWith(app.text.ayahs(kursi.range).single().text.take(10)))
    }

    @Test
    fun `a short surah is read whole, so its card can show all of it`() = runTest {
        val ikhlas = kahf()
        val vm = open()
        vm.onGlimpseNeeded(ikhlas.id)
        val glimpse = vm.state.value.glimpses.getValue(ikhlas.range)
        assertNull(glimpse.tail)
        assertTrue("all four ayahs", app.text.ayahs(AyahRange.single(ikhlas.range.end)).single().text.take(5) in glimpse.head)
    }

    @Test
    fun `a long passage's glimpse is its start and its last ayah, and nothing between is read`() = runTest {
        val baqara = app.library.create(BookmarkDrafts.surah(2, app.index))
        val text = CountingText(app.text)
        val vm = app.bookmarksViewModel(main.dispatcher, text)
        vm.onGlimpseNeeded(baqara.id)
        val glimpse = vm.state.value.glimpses.getValue(baqara.range)
        val last = app.text.ayahs(AyahRange.single(baqara.range.end)).single().text
        assertTrue("ends with 2:286", glimpse.tail!!.startsWith(last.take(10)))
        assertEquals(BookmarksViewModel.GLIMPSE_AYAHS + 1, text.ayahsRead)
    }

    @Test
    fun `a thousand bookmarks of al-Baqara load its text once`() = runTest {
        // Notes item 43 (owner, 2026-11-26): the list must stay light however many bookmarks there are.
        val ids = (1..1000).map { app.library.create(BookmarkDrafts.surah(2, app.index)).id }
        val text = CountingText(app.text)
        val vm = app.bookmarksViewModel(main.dispatcher, text)
        ids.forEach(vm::onGlimpseNeeded)
        assertEquals(1000, vm.state.value.entries().size)
        assertEquals("the start and the end of one passage", BookmarksViewModel.GLIMPSE_AYAHS + 1, text.ayahsRead)
        assertEquals(1, vm.state.value.glimpses.size)
    }

    // ---- Unread and Read all ------------------------------------------------------------------

    @Test
    fun `Unread hides read bookmarks, but one read meanwhile stays until the screen is left`() = runTest {
        val read = kahf()
        val unread = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        app.tracker.markRange(read.range, io.github.mehrdad_abdi.qintar.domain.model.ReadContext.Bookmark(read.id), io.github.mehrdad_abdi.qintar.domain.model.ReadMethod.BULK, io.github.mehrdad_abdi.qintar.domain.model.RangeMode.FILL)
        val vm = open()
        vm.onFilter(BookmarksFilter.UNREAD)
        assertEquals(listOf(unread.id), vm.state.value.entries())

        vm.onLogFullReading(unread.id)
        assertEquals("stays where it is", listOf(unread.id), vm.state.value.entries())

        vm.onScreenLeft()
        assertTrue(vm.state.value.entries().isEmpty())
        assertTrue(vm.state.value.rows.isEmpty())
    }

    @Test
    fun `Read all opens the reader on what the list shows`() = runTest {
        val vm = open()
        assertEquals(ReaderRoute.bookmarks(unreadOnly = false), vm.readAllRoute())
        vm.onFilter(BookmarksFilter.UNREAD)
        assertEquals(ReaderRoute.bookmarks(unreadOnly = true), vm.readAllRoute())
    }

    // ---- actions -----------------------------------------------------------------------------------

    @Test
    fun `log a full reading and undo it`() = runTest {
        val b = kahf(target = 2)
        val vm = open()
        vm.onLogFullReading(b.id)
        assertEquals(b.range.size, app.log.allEvents().size)
        assertEquals(UndoKind.LOGGED, vm.state.value.notice?.kind)

        vm.onUndo()
        assertEquals(0, app.log.allEvents().size)
        assertNull(vm.state.value.notice)
        assertEquals(0, vm.state.value.all.single().state.progress.readings)
    }

    @Test
    fun `delete and undo keeps the day's readings`() = runTest {
        val b = kahf()
        val vm = open()
        vm.onLogFullReading(b.id)
        val before = app.log.allEvents().size

        vm.onDelete(b.id)
        assertTrue(vm.state.value.all.isEmpty())
        assertEquals("readings outlive the bookmark", before, app.log.allEvents().size)
        assertEquals(UndoKind.DELETED, vm.state.value.notice?.kind)

        vm.onUndo()
        val back = vm.state.value.all.single()
        assertEquals(b.id, back.id)
        assertTrue("its readings attach to it again", back.state.progress.done)
        assertEquals(before, app.log.allEvents().size)
    }

    @Test
    fun `move and duplicate to a group`() = runTest {
        val a = app.library.createGroup("A")
        val c = app.library.createGroup("C")
        val b = kahf(schedule = null, target = 2, groupId = a.id)
        val vm = open()

        vm.onMoveRequested(b.id)
        val pick = vm.state.value.dialog as BookmarksDialog.PickGroup
        assertEquals("its own group is not offered", listOf(c.id), pick.choices.map { it.id })
        vm.onGroupChosen(c.id)
        assertEquals(c.id, vm.state.value.all.single().state.bookmark.groupId)

        vm.onDuplicateRequested(b.id)
        vm.onGroupChosen(a.id)
        val all = vm.state.value.all
        assertEquals(2, all.size)
        val copy = all.first { it.id != b.id }.state.bookmark
        assertEquals(a.id, copy.groupId)
        assertEquals(b.range, copy.range)
        assertEquals(2, copy.repeatTarget)
        assertEquals("the copy starts unread", 0, all.first { it.id == copy.id }.state.progress.readings)

        vm.onMoveRequested(b.id)
        vm.onGroupChosen(null)
        assertNull(vm.state.value.all.first { it.id == b.id }.state.bookmark.groupId)
    }

    @Test
    fun `deleting a group keeps its bookmarks or deletes them`() = runTest {
        val keep = app.library.createGroup("Keep")
        val drop = app.library.createGroup("Drop")
        val k = kahf(null, groupId = keep.id)
        kahf(null, groupId = drop.id)
        val vm = open()

        vm.onDeleteGroupRequested(keep.id)
        assertEquals(1, (vm.state.value.dialog as BookmarksDialog.DeleteGroup).bookmarkCount)
        vm.onDeleteGroupConfirmed(deleteBookmarks = false)
        assertEquals(listOf(drop.id), vm.state.value.groups.map { it.id })
        assertEquals("kept, now ungrouped", listOf(k.id), vm.state.value.ungrouped.map { it.id })

        vm.onDeleteGroupRequested(drop.id)
        vm.onDeleteGroupConfirmed(deleteBookmarks = true)
        assertTrue(vm.state.value.groups.isEmpty())
        assertEquals(listOf(k.id), vm.state.value.all.map { it.id })
    }

    @Test
    fun `log a full reading of a group is one action with one undo`() = runTest {
        val group = app.library.createGroup("G")
        val one = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = group.id))
        val two = app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = group.id))
        val vm = open()
        vm.onLogGroupReading(group.id)
        assertEquals(1 + two.range.size, app.log.allEvents().size)
        assertTrue(vm.state.value.groups.single().state.progress.done)
        assertEquals(one.id, vm.state.value.groups.single().items.first().id)

        vm.onUndo()
        assertEquals(0, app.log.allEvents().size)
    }

    // ---- titles ---------------------------------------------------------------------------

    @Test
    fun `items carry their title as data and the description`() = runTest {
        app.library.create(BookmarkDraft(app.index.surahRange(18), io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind.SURAH, description = "Read on Fridays"))
        val item = open().state.value.all.single()
        assertNotNull(item.title)
        assertEquals("Read on Fridays", item.state.bookmark.description)
    }
}
