package io.github.mehrdad_abdi.qintar.feature.bookmarks

import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.bookmarksViewModel
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderPhase
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val morning = Schedule.daily(TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0)))

    private fun open() = app.bookmarksViewModel(main.dispatcher, ticks)

    private suspend fun kahf(schedule: Schedule? = morning, target: Int = 1, groupId: Long? = null) =
        app.library.create(BookmarkDrafts.surah(112, app.index).copy(schedule = schedule, repeatTarget = target, groupId = groupId))

    private fun BookmarksUiState.nowIds() = now.flatMap { s -> s.items.map { it.id } }

    // ---- availability ------------------------------------------------------------------------------

    @Test
    fun `a window bookmark is in Now at 09, Later at 07, Earlier at 13 and always openable`() = runTest {
        val b = kahf()
        val vm = open()
        assertEquals(listOf(b.id), vm.state.value.nowIds())

        app.clock.setTime(7)
        ticks.tryEmit(Unit)
        assertEquals(emptyList<Long>(), vm.state.value.nowIds())
        assertEquals(listOf(b.id), vm.state.value.laterToday.map { it.id })

        app.clock.setTime(13)
        ticks.tryEmit(Unit)
        assertEquals(listOf(b.id), vm.state.value.earlierToday.map { it.id })

        // Nothing locks: the reader opens it at 13:00 all the same.
        val reader = app.readerViewModel(ReaderRoute.bookmark(b.id), main.dispatcher)
        assertEquals(ReaderPhase.READY, reader.state.value.phase)
    }

    @Test
    fun `a weekday schedule lists the bookmark only on those days`() = runTest {
        val b = kahf(Schedule(setOf(DayOfWeek.MONDAY), null))
        val vm = open()
        assertTrue("Sunday: not scheduled", vm.state.value.nowIds().isEmpty())
        assertTrue(vm.state.value.laterToday.isEmpty() && vm.state.value.earlierToday.isEmpty())
        assertEquals("still found under All", listOf(b.id), vm.state.value.all.map { it.id })

        app.clock.advanceDays(1)
        assertEquals(listOf(b.id), vm.state.value.nowIds())
    }

    @Test
    fun `a bookmark inherits its group's schedule`() = runTest {
        val group = app.library.createGroup("Morning", schedule = morning)
        val b = kahf(schedule = null, groupId = group.id)
        val vm = open()
        val section = vm.state.value.now.single()
        assertEquals(group.id, section.group?.id)
        assertEquals(listOf(b.id), section.items.map { it.id })
        assertEquals(morning, section.items.single().state.schedule)

        app.clock.setTime(14)
        ticks.tryEmit(Unit)
        assertEquals(listOf(b.id), vm.state.value.earlierToday.map { it.id })
    }

    @Test
    fun `reading it to the target moves it to Done`() = runTest {
        val b = kahf()
        val vm = open()
        vm.onLogFullReading(b.id)
        assertTrue(vm.state.value.nowIds().isEmpty())
        assertEquals(listOf(b.id), vm.state.value.doneToday.map { it.id })
    }

    @Test
    fun `a repeat target of 3 shows 1 of 3 and then 3 of 3`() = runTest {
        val b = kahf(target = 3)
        val vm = open()
        vm.onLogFullReading(b.id)
        var progress = vm.state.value.all.single().state.progress
        assertEquals(1, progress.fullReadings)
        assertEquals(3, progress.repeatTarget)
        assertEquals(listOf(b.id), vm.state.value.nowIds())

        vm.onLogFullReading(b.id)
        vm.onLogFullReading(b.id)
        progress = vm.state.value.all.single().state.progress
        assertEquals(3, progress.fullReadings)
        assertTrue(progress.done)
        assertEquals(listOf(b.id), vm.state.value.doneToday.map { it.id })
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
    fun `set count gives every ayah that many readings and can be undone`() = runTest {
        val b = kahf(target = 5)
        val vm = open()
        vm.onSetCountRequested(b.id)
        assertEquals(0, (vm.state.value.dialog as BookmarksDialog.SetCount).current)
        vm.onSetCount(4)
        assertNull(vm.state.value.dialog)
        assertEquals(4, vm.state.value.all.single().state.progress.fullReadings)
        assertEquals(4 * b.range.size, app.log.allEvents().size)

        vm.onUndo()
        assertEquals(0, app.log.allEvents().size)
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

    // ---- sort and titles ---------------------------------------------------------------------------

    @Test
    fun `the sort mode is persisted and orders the lists`() = runTest {
        val later = app.library.create(BookmarkDrafts.surah(112, app.index))
        app.clock.advanceMinutes(5)
        val earlier = app.library.create(BookmarkDrafts.ayah(app.ayah(1, 1)))
        val vm = open()
        assertEquals(BookmarkSort.MUSHAF, vm.state.value.sort)
        assertEquals(listOf(earlier.id, later.id), vm.state.value.all.map { it.id })

        vm.onSort(BookmarkSort.RECENT)
        assertEquals(BookmarkSort.RECENT, app.prefs.current.bookmarkSort)
        assertEquals(BookmarkSort.RECENT, vm.state.value.sort)
        assertEquals(listOf(earlier.id, later.id), vm.state.value.all.map { it.id })

        vm.onSort(BookmarkSort.CUSTOM)
        assertEquals(listOf(later.id, earlier.id), vm.state.value.all.map { it.id })

        // A new view model starts from the saved mode.
        assertEquals(BookmarkSort.CUSTOM, open().state.value.sort)
    }

    @Test
    fun `items carry their title as data and the description`() = runTest {
        app.library.create(BookmarkDraft(app.index.surahRange(18), io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind.SURAH, description = "Read on Fridays"))
        val item = open().state.value.all.single()
        assertNotNull(item.title)
        assertEquals("Read on Fridays", item.state.bookmark.description)
    }
}
