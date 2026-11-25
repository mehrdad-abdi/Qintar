package io.github.mehrdad_abdi.qintar.domain

import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.ArrangeKey
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BoardItem
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.service.ScheduleStatus
import io.github.mehrdad_abdi.qintar.domain.service.ScheduleStatus.AVAILABLE_NOW
import io.github.mehrdad_abdi.qintar.domain.service.ScheduleStatus.EARLIER_TODAY
import io.github.mehrdad_abdi.qintar.domain.service.ScheduleStatus.LATER_TODAY
import io.github.mehrdad_abdi.qintar.domain.service.ScheduleStatus.NOT_TODAY
import io.github.mehrdad_abdi.qintar.domain.service.ScheduleStatus.UNSCHEDULED
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SATURDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §4.7: schedules, time windows, groups, repeat targets, done state and the `once` roll-over. */
class AvailabilityTest {
    // 2026-11-13 is a Friday.
    private val friday: LocalDate = LocalDate.of(2026, 11, 13)
    private val saturday: LocalDate = friday.plusDays(1)
    private val app = DomainFixture(start = LocalDateTime.of(friday, LocalTime.of(9, 0)))

    private val morning = TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0))
    private val night = TimeWindow(LocalTime.of(22, 0), LocalTime.of(2, 0))

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = app.clock.set(day, LocalTime.of(hour, minute))

    private suspend fun bookmark(draft: BookmarkDraft): Bookmark = app.library.create(draft)

    private suspend fun ikhlas(schedule: Schedule? = null, groupId: Long? = null, target: Int = 1) =
        bookmark(BookmarkDrafts.surah(112, app.index).copy(schedule = schedule, groupId = groupId, repeatTarget = target))

    private suspend fun statusOf(bookmark: Bookmark): ScheduleStatus = app.availability.board().state(bookmark.id)!!.status

    private suspend fun progressOf(bookmark: Bookmark) = app.availability.board().state(bookmark.id)!!.progress

    private suspend fun fullReading(bookmark: Bookmark, times: Int = 1) =
        app.tracker.markRange(bookmark.range, ReadContext.Bookmark(bookmark.id), ReadMethod.BULK, RangeMode.ADD, times)

    // ---- weekdays ----

    @Test
    fun `a bookmark with no schedule of its own or its group's is never listed as available`() = runTest {
        val b = ikhlas()

        val board = app.availability.board()
        assertEquals(UNSCHEDULED, board.state(b.id)!!.status)
        assertTrue(board.now.isEmpty())
        assertEquals("it is still found under All", listOf(b.id), board.all.map { it.bookmark.id })
    }

    @Test
    fun `a daily bookmark is available on every day of the week`() = runTest {
        val b = ikhlas(Schedule.daily())
        for (offset in 0L..6L) {
            at(friday.plusDays(offset), 15)
            assertEquals(AVAILABLE_NOW, statusOf(b))
        }
    }

    @Test
    fun `a Friday bookmark is available on Friday and not listed on Saturday`() = runTest {
        val b = ikhlas(Schedule(setOf(FRIDAY), window = null))

        at(friday, 9)
        assertEquals(AVAILABLE_NOW, statusOf(b))

        at(saturday, 9)
        assertEquals(NOT_TODAY, statusOf(b))
        assertTrue(app.availability.board().now.isEmpty())
    }

    // ---- time windows ----

    @Test
    fun `a window makes a bookmark later today before it, available inside it, earlier today after it`() = runTest {
        val b = ikhlas(Schedule.daily(morning))

        at(friday, 7, 59)
        assertEquals(LATER_TODAY, statusOf(b))
        at(friday, 8, 0)
        assertEquals("the window opens at its start", AVAILABLE_NOW, statusOf(b))
        at(friday, 11, 59)
        assertEquals(AVAILABLE_NOW, statusOf(b))
        at(friday, 12, 0)
        assertEquals("and has closed at its end", EARLIER_TODAY, statusOf(b))
    }

    @Test
    fun `the board sorts scheduled bookmarks into now, later, earlier and done`() = runTest {
        val early = bookmark(BookmarkDrafts.surah(112, app.index).copy(schedule = Schedule.daily(TimeWindow(LocalTime.of(5, 0), LocalTime.of(7, 0)))))
        val now = bookmark(BookmarkDrafts.surah(113, app.index).copy(schedule = Schedule.daily(morning)))
        val late = bookmark(BookmarkDrafts.surah(114, app.index).copy(schedule = Schedule.daily(TimeWindow(LocalTime.of(20, 0), LocalTime.of(23, 0)))))
        val finished = bookmark(BookmarkDrafts.surah(110, app.index).copy(schedule = Schedule.daily(morning)))
        fullReading(finished)
        at(friday, 9)

        val board = app.availability.board()
        assertEquals(listOf(now.id), board.now.flatMap { it.items }.map { it.bookmark.id })
        assertEquals(1, board.remainingNow)
        assertEquals(listOf(late.id), board.laterToday.map { it.bookmark.id })
        assertEquals(listOf(early.id), board.earlierToday.map { it.bookmark.id })
        assertEquals(listOf(finished.id), board.doneToday.map { it.bookmark.id })
    }

    @Test
    fun `a bookmark read before its window opens is done, not waiting for later`() = runTest {
        val b = ikhlas(Schedule.daily(TimeWindow(LocalTime.of(20, 0), LocalTime.of(23, 0))))
        at(friday, 9)
        fullReading(b)

        val board = app.availability.board()
        assertTrue("the window decides what is shown first, not what is allowed", board.laterToday.isEmpty())
        assertEquals(listOf(b.id), board.doneToday.map { it.bookmark.id })
    }

    @Test
    fun `a 22_00–02_00 window is available at 01_00 on the following calendar day and not at 03_00`() = runTest {
        val b = ikhlas(Schedule(setOf(FRIDAY), night))

        at(friday, 21, 59)
        assertEquals(LATER_TODAY, statusOf(b))
        at(friday, 22, 0)
        assertEquals(AVAILABLE_NOW, statusOf(b))
        at(friday, 23, 59)
        assertEquals(AVAILABLE_NOW, statusOf(b))

        at(saturday, 1, 0)
        assertEquals("the after-midnight part belongs to Friday, where the window starts", AVAILABLE_NOW, statusOf(b))
        at(saturday, 1, 59)
        assertEquals(AVAILABLE_NOW, statusOf(b))
        at(saturday, 2, 0)
        assertEquals(EARLIER_TODAY, statusOf(b))
        at(saturday, 3, 0)
        assertEquals(EARLIER_TODAY, statusOf(b))
        assertTrue(app.availability.board().now.isEmpty())
        at(saturday, 22, 30)
        assertEquals("Saturday night is not scheduled: the window that closed at 02:00 stays 'earlier today'", EARLIER_TODAY, statusOf(b))
        at(saturday.plusDays(1), 1, 0)
        assertEquals("and on Sunday nothing of it remains", NOT_TODAY, statusOf(b))
    }

    @Test
    fun `a midnight-crossing window is not available early on the day it is scheduled if the day before was not`() = runTest {
        val b = ikhlas(Schedule(setOf(FRIDAY), night))

        at(friday, 1, 0)

        assertEquals("01:00 on Friday would belong to Thursday's window", LATER_TODAY, statusOf(b))
    }

    @Test
    fun `a nightly window on consecutive days is available both after midnight and again in the evening`() = runTest {
        val b = ikhlas(Schedule.daily(night))

        at(saturday, 1, 0)
        assertEquals(AVAILABLE_NOW, statusOf(b))
        at(saturday, 12, 0)
        assertEquals(LATER_TODAY, statusOf(b))
        at(saturday, 22, 0)
        assertEquals(AVAILABLE_NOW, statusOf(b))
    }

    @Test
    fun `with the day starting at 03_00 a late-night window stays inside the reading day it began in`() = runTest {
        app.setDayStart(180)
        val b = ikhlas(Schedule(setOf(FRIDAY), night), target = 1)

        at(saturday, 1, 0)
        assertEquals("the reading day is still Friday", friday, app.clock.today())
        assertEquals(AVAILABLE_NOW, statusOf(b))

        fullReading(b)
        assertTrue("and what is read then completes Friday's bookmark", progressOf(b).done)

        at(saturday, 2, 30)
        assertEquals(EARLIER_TODAY, statusOf(b))
        at(saturday, 3, 0)
        assertEquals("Saturday has begun", NOT_TODAY, statusOf(b))
    }

    @Test
    fun `availability is re-evaluated when the screen's ticker fires, since a window opening is not a data change`() = runTest {
        val b = ikhlas(Schedule.daily(morning))
        at(friday, 7, 0)
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        app.availability.observe(ticks).test {
            assertEquals(LATER_TODAY, expectMostRecentItem().state(b.id)!!.status)
            at(friday, 8, 30)
            ticks.emit(Unit)
            assertEquals(AVAILABLE_NOW, expectMostRecentItem().state(b.id)!!.status)
        }
    }

    // ---- groups ----

    @Test
    fun `a bookmark without its own schedule follows its group's, with its own, ignores the group's`() = runTest {
        val group = app.library.createGroup("Morning", schedule = Schedule.daily(morning))
        val follower = ikhlas(groupId = group.id)
        val independent = bookmark(
            BookmarkDrafts.surah(113, app.index).copy(groupId = group.id, schedule = Schedule(setOf(SATURDAY), window = null)),
        )

        at(friday, 9)
        assertEquals(AVAILABLE_NOW, statusOf(follower))
        assertEquals("its own Saturday schedule wins over the group's daily one", NOT_TODAY, statusOf(independent))

        at(friday, 13)
        assertEquals(EARLIER_TODAY, statusOf(follower))

        at(saturday, 20)
        assertEquals(EARLIER_TODAY, statusOf(follower))
        assertEquals("no window of its own, so any time on Saturday", AVAILABLE_NOW, statusOf(independent))
    }

    @Test
    fun `now lists available bookmarks under their group's header, then ungrouped`() = runTest {
        val group = app.library.createGroup("Morning", schedule = Schedule.daily(morning))
        val kursi = bookmark(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = group.id))
        val ikhlas = ikhlas(groupId = group.id)
        val kahf = bookmark(BookmarkDrafts.surah(18, app.index).copy(schedule = Schedule(setOf(FRIDAY), window = null)))
        at(friday, 9)

        val now = app.availability.board().now

        assertEquals(listOf("Morning", null), now.map { it.group?.group?.name })
        assertEquals(listOf(kursi.id, ikhlas.id), now[0].items.map { it.bookmark.id })
        assertEquals(listOf(kahf.id), now[1].items.map { it.bookmark.id })
        assertEquals("Read on the group opens one segment per bookmark, each under its own context",
            listOf(ReadContext.Bookmark(kursi.id), ReadContext.Bookmark(ikhlas.id)), now[0].group!!.segments.map { it.context })

        at(friday, 13)
        val after = app.availability.board()
        assertEquals("after the window only the ungrouped Friday bookmark is still due now", listOf(kahf.id), after.now.flatMap { it.items }.map { it.bookmark.id })
        assertEquals(setOf(kursi.id, ikhlas.id), after.earlierToday.map { it.bookmark.id }.toSet())

        at(saturday, 9)
        assertNull("the Friday bookmark is not listed on Saturday", app.availability.board().now.firstOrNull { it.group == null })
    }

    @Test
    fun `a group is done when all its bookmarks are, and its progress combines theirs`() = runTest {
        val group = app.library.createGroup("Morning", schedule = Schedule.daily())
        val ikhlas = ikhlas(groupId = group.id) // 4 ayahs
        val falaq = bookmark(BookmarkDrafts.surah(113, app.index).copy(groupId = group.id)) // 5 ayahs

        fullReading(ikhlas)
        var state = app.availability.board().groups.single()
        assertEquals(4, state.progress.readings)
        assertEquals(9, state.progress.required)
        assertEquals(1, state.progress.bookmarksDone)
        assertFalse(state.progress.done)
        assertEquals("the reader opens the group at its first unfinished bookmark", falaq.id, state.firstNotDone!!.bookmark.id)

        fullReading(falaq)
        state = app.availability.board().groups.single()
        assertTrue(state.progress.done)
        assertEquals(1f, state.progress.fraction)
        assertNull(state.firstNotDone)
    }

    @Test
    fun `an empty group is never shown as done`() = runTest {
        app.library.createGroup("Empty")
        assertFalse(app.availability.board().groups.single().progress.done)
    }

    // ---- progress and repeat targets ----

    @Test
    fun `a bookmark is done today when every one of its ayahs has been read today`() = runTest {
        val b = ikhlas(Schedule.daily())
        val ayahs = b.range.toList()

        ayahs.dropLast(1).forEach { app.tracker.mark(it, ReadContext.Bookmark(b.id), ReadMethod.TAP) }
        var progress = progressOf(b)
        assertFalse(progress.done)
        assertEquals(3, progress.readings)
        assertEquals(4, progress.required)
        assertEquals("the reader opens at the first ayah still unread", ayahs.last(), progress.firstBelowTarget)

        app.tracker.mark(ayahs.last(), ReadContext.Bookmark(b.id), ReadMethod.TAP)
        progress = progressOf(b)
        assertTrue(progress.done)
        assertNull(progress.firstBelowTarget)
    }

    @Test
    fun `a bookmark with repeat target 100 is done only when every ayah has 100 readings today`() = runTest {
        val b = ikhlas(Schedule.daily(), target = 100)
        val context = ReadContext.Bookmark(b.id)
        val ayahs = b.range.toList()

        fullReading(b, times = 99)
        var progress = progressOf(b)
        assertFalse(progress.done)
        assertEquals("shown as 99 / 100", 99, progress.fullReadings)

        // Three of its four ayahs reach 100; one is still at 99.
        ayahs.take(3).forEach { app.tracker.mark(it, context, ReadMethod.TAP) }
        progress = progressOf(b)
        assertFalse("one ayah short is not done", progress.done)
        assertEquals(99, progress.fullReadings)
        assertEquals(399, progress.readings)
        assertEquals(400, progress.required)
        assertEquals(ayahs[3], progress.firstBelowTarget)

        app.tracker.mark(ayahs[3], context, ReadMethod.TAP)
        progress = progressOf(b)
        assertTrue(progress.done)
        assertEquals(100, progress.fullReadings)
        assertEquals(400, app.totals.today())
    }

    @Test
    fun `reading one ayah far beyond the target does not make up for another ayah`() = runTest {
        val b = ikhlas(target = 3)
        val context = ReadContext.Bookmark(b.id)
        val ayahs = b.range.toList()

        app.tracker.setCount(ayahs[0], context, app.clock.today(), 50)

        val progress = progressOf(b)
        assertEquals("counts are capped at the target", 3, progress.readings)
        assertEquals(12, progress.required)
        assertEquals(0, progress.fullReadings)
        assertFalse(progress.done)
    }

    @Test
    fun `set count 100 on a single-ayah bookmark with target 100 makes it done and adds 100 to today`() = runTest {
        val b = bookmark(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(repeatTarget = 100, schedule = Schedule.daily()))

        app.tracker.setCount(app.ayah(2, 255), ReadContext.Bookmark(b.id), app.clock.today(), 100)

        assertTrue(progressOf(b).done)
        assertEquals(100, app.totals.today())
    }

    @Test
    fun `done is about today, a bookmark read yesterday starts again unread`() = runTest {
        val b = ikhlas(Schedule.daily())
        fullReading(b)
        assertTrue(progressOf(b).done)

        app.clock.advanceDays(1)

        val progress = progressOf(b)
        assertFalse(progress.done)
        assertFalse(progress.started)
        assertEquals(b.range.start, progress.firstBelowTarget)
    }

    @Test
    fun `reading a bookmark's ayahs in a khatm or while browsing does not make the bookmark read`() = runTest {
        val b = ikhlas(Schedule.daily())
        app.tracker.markRange(b.range, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)

        assertFalse(progressOf(b).started)
    }

    @Test
    fun `the board follows readings as they are made`() = runTest {
        val b = ikhlas(Schedule.daily())
        app.availability.observe().test {
            assertEquals(1, expectMostRecentItem().remainingNow)
            fullReading(b)
            val board = expectMostRecentItem()
            assertEquals(0, board.remainingNow)
            assertEquals(listOf(b.id), board.doneToday.map { it.bookmark.id })
        }
    }

    // ---- once ----

    @Test
    fun `a once bookmark is due every day until it is done once`() = runTest {
        val b = ikhlas(Schedule.once())

        for (offset in 0L..3L) {
            at(friday.plusDays(offset), 10)
            assertEquals(emptyList<Long>(), app.availability.rollOverOnce())
            assertEquals(AVAILABLE_NOW, statusOf(b))
        }
    }

    @Test
    fun `a once bookmark stays scheduled on the day it is done and loses its schedule at the next day boundary`() = runTest {
        val b = ikhlas(Schedule.once())
        fullReading(b)

        assertEquals("nothing rolls over on the day itself", emptyList<Long>(), app.availability.rollOverOnce())
        assertEquals(listOf(b.id), app.availability.board().doneToday.map { it.bookmark.id })

        app.clock.advanceDays(1)
        assertEquals(listOf(b.id), app.availability.rollOverOnce())

        val after = app.bookmarkStore.bookmark(b.id)!!
        assertNull("the schedule is cleared", after.schedule)
        assertEquals("the bookmark itself is kept", b.range, after.range)
        assertEquals(UNSCHEDULED, statusOf(b))
        assertEquals("a second roll-over finds nothing to do", emptyList<Long>(), app.availability.rollOverOnce())
    }

    @Test
    fun `a once bookmark that was only partly read is still due the next day`() = runTest {
        val b = ikhlas(Schedule.once(), target = 2)
        fullReading(b)

        app.clock.advanceDays(1)

        assertEquals(emptyList<Long>(), app.availability.rollOverOnce())
        assertEquals(AVAILABLE_NOW, statusOf(b))
    }

    @Test
    fun `a once bookmark with a window is due only inside that window`() = runTest {
        val b = ikhlas(Schedule.once(morning))

        at(saturday, 7)
        assertEquals(LATER_TODAY, statusOf(b))
        at(saturday, 9)
        assertEquals(AVAILABLE_NOW, statusOf(b))
    }

    @Test
    fun `the roll-over of one bookmark leaves ordinary and group schedules alone`() = runTest {
        val group = app.library.createGroup("Morning", schedule = Schedule.daily())
        val once = ikhlas(Schedule.once(), groupId = group.id)
        val daily = bookmark(BookmarkDrafts.surah(113, app.index).copy(schedule = Schedule.daily()))
        fullReading(once)
        fullReading(daily)
        app.clock.advanceDays(1)

        app.availability.rollOverOnce()

        assertEquals(Schedule.daily(), app.bookmarkStore.bookmark(daily.id)!!.schedule)
        assertEquals("having lost its own schedule it now follows its group", AVAILABLE_NOW, statusOf(once))
    }

    // ---- order ----

    @Test
    fun `bookmarks are listed in mushaf order, custom order or most recently added, as the user chose`() = runTest {
        val nas = bookmark(BookmarkDrafts.surah(114, app.index))
        app.clock.advanceMinutes(1)
        val fatiha = bookmark(BookmarkDrafts.surah(1, app.index))
        app.clock.advanceMinutes(1)
        val kahf = bookmark(BookmarkDrafts.surah(18, app.index))

        suspend fun order(sort: BookmarkSort): List<Long> {
            app.prefs.update { it.copy(bookmarkSort = sort) }
            return app.availability.board().all.map { it.bookmark.id }
        }

        assertEquals(listOf(fatiha.id, kahf.id, nas.id), order(BookmarkSort.MUSHAF))
        assertEquals("new items go to the end of the custom order", listOf(nas.id, fatiha.id, kahf.id), order(BookmarkSort.CUSTOM))
        assertEquals(listOf(kahf.id, fatiha.id, nas.id), order(BookmarkSort.RECENT))

        app.library.reorder(listOf(kahf.id, nas.id, fatiha.id))
        assertEquals(listOf(kahf.id, nas.id, fatiha.id), order(BookmarkSort.CUSTOM))
    }

    @Test
    fun `bookmarks without a group stand among the groups, in Quran order, newest first or as arranged`() = runTest {
        // Owner, 2026-11-25: no "Ungrouped" section; the user picks the order and can arrange it.
        val kahf = bookmark(BookmarkDrafts.surah(18, app.index))
        val night = app.library.createGroup("Night")
        app.clock.advanceMinutes(1)
        val mulk = bookmark(BookmarkDrafts.surah(67, app.index).copy(groupId = night.id))
        app.clock.advanceMinutes(1)
        val fatiha = bookmark(BookmarkDrafts.surah(1, app.index))
        app.clock.advanceMinutes(1)
        val yasin = bookmark(BookmarkDrafts.surah(36, app.index).copy(groupId = night.id))

        fun BoardItem.name(): String = when (this) {
            is BoardItem.Group -> state.group.name + state.bookmarks.joinToString(",", "[", "]") { it.bookmark.id.toString() }
            is BoardItem.Loose -> state.bookmark.id.toString()
        }
        suspend fun order(sort: BookmarkSort): List<String> {
            app.prefs.update { it.copy(bookmarkSort = sort) }
            return app.availability.board().items.map { it.name() }
        }

        // A group stands where its first ayah is, or by its newest bookmark.
        assertEquals(listOf("${fatiha.id}", "${kahf.id}", "Night[${yasin.id},${mulk.id}]"), order(BookmarkSort.MUSHAF))
        assertEquals(listOf("Night[${yasin.id},${mulk.id}]", "${fatiha.id}", "${kahf.id}"), order(BookmarkSort.RECENT))

        app.library.arrange(listOf(ArrangeKey.Bookmark(fatiha.id), ArrangeKey.Group(night.id), ArrangeKey.Bookmark(kahf.id)))
        app.library.reorder(listOf(yasin.id, mulk.id))
        assertEquals(listOf("${fatiha.id}", "Night[${yasin.id},${mulk.id}]", "${kahf.id}"), order(BookmarkSort.CUSTOM))

        val board = app.availability.board()
        assertEquals("the list, played in order", listOf(fatiha.id, yasin.id, mulk.id, kahf.id), board.listed.map { it.bookmark.id })
        assertEquals("Today's list, in the same order", listOf(null, night.id, null), board.unread.map { it.group?.group?.id })
    }

    @Test
    fun `groups keep their custom order`() = runTest {
        val a = app.library.createGroup("Morning")
        val b = app.library.createGroup("Friday")
        val c = app.library.createGroup("Night")
        assertEquals(listOf("Morning", "Friday", "Night"), app.availability.board().groups.map { it.group.name })

        app.library.reorderGroups(listOf(c.id, a.id, b.id))
        assertEquals(listOf("Night", "Morning", "Friday"), app.availability.board().groups.map { it.group.name })
    }

    // ---- the status rule as a table ----

    @Test
    fun `schedule status table`() {
        data class Case(val days: Set<DayOfWeek>, val window: TimeWindow?, val day: LocalDate, val time: String, val expected: ScheduleStatus)

        val everyDay = Schedule.EVERY_DAY
        val cases = listOf(
            Case(everyDay, null, friday, "00:00", AVAILABLE_NOW),
            Case(everyDay, null, friday, "23:59", AVAILABLE_NOW),
            Case(setOf(SATURDAY), null, friday, "12:00", NOT_TODAY),
            Case(setOf(SATURDAY), morning, friday, "09:00", NOT_TODAY),
            Case(setOf(FRIDAY), morning, friday, "00:00", LATER_TODAY),
            Case(setOf(FRIDAY), morning, friday, "23:59", EARLIER_TODAY),
            Case(setOf(SATURDAY), night, saturday, "00:30", LATER_TODAY),
            Case(setOf(FRIDAY, SATURDAY), night, saturday, "00:30", AVAILABLE_NOW),
            Case(setOf(FRIDAY, SATURDAY), night, saturday, "02:00", LATER_TODAY),
            Case(setOf(FRIDAY), night, saturday.plusDays(1), "01:00", NOT_TODAY),
        )
        for (case in cases) {
            val actual = Availability.status(Schedule(case.days, case.window), case.day, LocalTime.parse(case.time))
            assertEquals("$case", case.expected, actual)
        }
        assertEquals(UNSCHEDULED, Availability.status(null, friday, LocalTime.NOON))
    }
}
