package io.github.mehrdad_abdi.qintar.feature.bookmarks

import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.FakePlayerController
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.bookmarksViewModel
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private fun BookmarksUiState.ayahsOf(id: Long) = rows.filterIsInstance<BookmarksRow.Ayah>().filter { it.bookmarkId == id }

    @Test
    fun `groups come first, then Ungrouped, groups open and bookmarks closed`() = runTest {
        val morning = app.library.createGroup("Morning")
        val inGroup = kahf(groupId = morning.id)
        val loose = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        val vm = open()
        val state = vm.state.value
        assertEquals(listOf(morning.id, UNGROUPED), state.sections().map { it.sectionKey })
        assertTrue(state.sections().all { it.expanded })
        assertEquals(listOf(inGroup.id, loose.id), state.entries())
        assertTrue("no ayahs until a bookmark is opened", state.rows.none { it is BookmarksRow.Ayah })
    }

    @Test
    fun `a group folds its bookmarks and a bookmark folds its ayahs`() = runTest {
        val g = app.library.createGroup("G")
        val b = kahf(groupId = g.id)
        val vm = open()

        vm.onToggleBookmark(b.id)
        val ayahs = vm.state.value.ayahsOf(b.id)
        assertEquals(b.range.toList(), ayahs.map { it.id })
        assertTrue("the text is loaded for them", ayahs.all { it.id in vm.state.value.texts })
        assertEquals("Mark read ends the bookmark", BookmarksRow.End(b.id, done = false), vm.state.value.rows.last())

        vm.onToggleSection(g.id)
        assertEquals(listOf(g.id), vm.state.value.sections().map { it.sectionKey })
        assertTrue(vm.state.value.entries().isEmpty())

        vm.onToggleSection(g.id)
        assertEquals("the bookmark is still open", b.range.size, vm.state.value.ayahsOf(b.id).size)
        vm.onToggleBookmark(b.id)
        assertTrue(vm.state.value.ayahsOf(b.id).isEmpty())
    }

    @Test
    fun `a bookmark that starts a surah shows the Bismillah before ayah 1, but not at-Tawba or al-Fatiha`() = runTest {
        // Owner, 2026-11-22: a whole-surah bookmark had no Bismillah.
        val ikhlas = kahf()
        val tawba = app.library.create(BookmarkDrafts.surah(9, app.index))
        val fatiha = app.library.create(BookmarkDrafts.surah(1, app.index))
        val inside = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        val vm = open()
        listOf(ikhlas, tawba, fatiha, inside).forEach { vm.onToggleBookmark(it.id) }
        val rows = vm.state.value.rows

        val first = rows.indexOfFirst { it is BookmarksRow.Ayah && it.bookmarkId == ikhlas.id }
        assertEquals(BookmarksRow.Basmala(ikhlas.id, 112), rows[first - 1])
        assertTrue(rows.none { it is BookmarksRow.Basmala && it.bookmarkId == tawba.id })
        assertTrue("its ayah 1 is the Bismillah", rows.none { it is BookmarksRow.Basmala && it.bookmarkId == fatiha.id })
        assertTrue("a passage inside a surah has none", rows.none { it is BookmarksRow.Basmala && it.bookmarkId == inside.id })
    }

    @Test
    fun `ticking an ayah in the list counts it under its bookmark`() = runTest {
        val b = kahf()
        val vm = open()
        vm.onToggleBookmark(b.id)
        val first = vm.state.value.ayahsOf(b.id).first()
        vm.onCheck(first)
        assertEquals(1, vm.state.value.count(first))
        val event = app.log.allEvents().single()
        assertEquals(io.github.mehrdad_abdi.qintar.domain.model.ReadContext.Bookmark(b.id), event.context)
    }

    @Test
    fun `Mark read at the end fills only the unread ayahs and can be undone`() = runTest {
        val b = kahf()
        val vm = open()
        vm.onToggleBookmark(b.id)
        vm.onCheck(vm.state.value.ayahsOf(b.id).first())
        vm.onMarkBookmarkRead(b.id)
        assertEquals("one reading each", b.range.size, app.log.allEvents().size)
        assertEquals(BookmarksRow.End(b.id, done = true), vm.state.value.rows.last())
        vm.onUndo()
        assertEquals(1, app.log.allEvents().size)
    }

    @Test
    fun `Unread hides read bookmarks, but one read here stays until the screen is left`() = runTest {
        val read = kahf()
        val unread = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        app.tracker.markRange(read.range, io.github.mehrdad_abdi.qintar.domain.model.ReadContext.Bookmark(read.id), io.github.mehrdad_abdi.qintar.domain.model.ReadMethod.BULK, io.github.mehrdad_abdi.qintar.domain.model.RangeMode.FILL)
        val vm = open()
        vm.onFilter(BookmarksFilter.UNREAD)
        assertEquals(listOf(unread.id), vm.state.value.entries())

        vm.onMarkBookmarkRead(unread.id)
        assertEquals("stays where it is", listOf(unread.id), vm.state.value.entries())

        vm.onScreenLeft()
        assertTrue(vm.state.value.entries().isEmpty())
        assertTrue(vm.state.value.rows.isEmpty())
    }

    // ---- audio -------------------------------------------------------------------------------------

    @Test
    fun `play goes on through the following bookmarks of the list`() = runTest {
        val g = app.library.createGroup("G")
        val one = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = g.id))
        val two = kahf(groupId = g.id)
        val three = app.library.create(BookmarkDrafts.ayah(app.ayah(1, 1)))
        val player = FakePlayerController()
        val vm = app.bookmarksViewModel(main.dispatcher, player)

        vm.onPlayBookmark(two.id)
        val request = player.requests.single()
        assertEquals(BOOKMARKS_ORIGIN, request.origin)
        assertEquals("every listed bookmark, in list order", listOf(one.range, two.range, three.range), request.segments.map { it.range })
        assertEquals(1, request.startSegment)
        assertEquals(two.range.start, request.startAyah)
    }

    @Test
    fun `under Unread, play skips bookmarks already read`() = runTest {
        val read = app.library.create(BookmarkDrafts.ayah(app.ayah(1, 1)))
        val first = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)))
        val second = kahf()
        app.tracker.mark(read.range.start, io.github.mehrdad_abdi.qintar.domain.model.ReadContext.Bookmark(read.id), io.github.mehrdad_abdi.qintar.domain.model.ReadMethod.TAP)
        val player = FakePlayerController()
        val vm = app.bookmarksViewModel(main.dispatcher, player)
        vm.onFilter(BookmarksFilter.UNREAD)
        vm.onPlaySection(UNGROUPED)
        assertEquals(listOf(first.range, second.range), player.requests.single().segments.map { it.range })
    }

    @Test
    fun `the bookmark being played opens and its ayah is highlighted`() = runTest {
        val b = kahf()
        val player = FakePlayerController()
        val vm = app.bookmarksViewModel(main.dispatcher, player)
        val ayah = b.range.drop(1).first()
        player.nowAt(BOOKMARKS_ORIGIN, 0, ayah, io.github.mehrdad_abdi.qintar.domain.model.ReadContext.Bookmark(b.id))
        val state = vm.state.value
        assertEquals(ListAudio(b.id, ayah), state.audio)
        val row = state.ayahsOf(b.id).single { it.id == ayah }
        assertTrue(state.isAudioAt(row))
        assertEquals(state.rows.indexOf(row), state.scrollRequest?.row)

        vm.onPlayAyah(row)
        assertEquals("the playing ayah's button pauses", listOf("toggle"), player.calls)
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
