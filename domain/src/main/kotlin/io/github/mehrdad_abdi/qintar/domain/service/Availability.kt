package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkAyahCount
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart

/** Where a schedule stands at one moment of one reading day (§4.7). None of these forbids reading. */
enum class ScheduleStatus {
    /** No effective schedule: never listed as available, found under its group or under All. */
    UNSCHEDULED,

    /** Scheduled, but not for this reading day. */
    NOT_TODAY,
    AVAILABLE_NOW,

    /** Scheduled today, window not yet started. */
    LATER_TODAY,

    /** Scheduled today, window passed. */
    EARLIER_TODAY,
}

/** Today's progress of one bookmark toward its repeat target (R-BMK-8). */
data class BookmarkProgress(
    /** Σ over its ayahs of today's count, each capped at the target. */
    val readings: Int,
    /** Range size × repeat target. */
    val required: Int,
    /** Complete readings today: the minimum count over its ayahs, not capped ("37 / 100"). */
    val fullReadings: Int,
    val repeatTarget: Int,
    /** Where the reader opens and where "First unread" jumps; null when done. */
    val firstBelowTarget: AyahId?,
) {
    val fraction: Float get() = readings.toFloat() / required

    /** Every ayah has reached the target. */
    val done: Boolean get() = readings == required

    val started: Boolean get() = readings > 0
}

/** A group's bookmarks combined the same way. A group is done when all its bookmarks are. */
data class GroupProgress(
    val readings: Int,
    val required: Int,
    val bookmarksDone: Int,
    val bookmarkCount: Int,
) {
    val fraction: Float get() = if (required == 0) 0f else readings.toFloat() / required

    /** An empty group has nothing to be done with, so it never shows the done state. */
    val done: Boolean get() = bookmarkCount > 0 && bookmarksDone == bookmarkCount

    val started: Boolean get() = readings > 0
}

data class BookmarkState(
    val bookmark: Bookmark,
    val group: BookmarkGroup?,
    /** Its own schedule if set, otherwise its group's, otherwise null. */
    val schedule: Schedule?,
    val status: ScheduleStatus,
    val progress: BookmarkProgress,
) {
    val segment: Segment get() = Segment(ReadContext.Bookmark(bookmark.id), bookmark.range)
}

data class GroupState(
    val group: BookmarkGroup,
    /** In the current sort mode. */
    val bookmarks: List<BookmarkState>,
    /** Status of the group's own schedule (its header chip). */
    val status: ScheduleStatus,
    val progress: GroupProgress,
) {
    /** One segment per bookmark, in group order: what "Read" on the group opens (§5.4). */
    val segments: List<Segment> get() = bookmarks.map { it.segment }

    /** The reader opens a group at its first bookmark that is not done. */
    val firstNotDone: BookmarkState? get() = bookmarks.firstOrNull { !it.progress.done }
}

/** The bookmarks available now that share a header: a group, or Ungrouped when [group] is null. */
data class NowSection(val group: GroupState?, val items: List<BookmarkState>)

/** Every bookmark and group with its state at one moment; the source of Now, Groups, All and Today. */
data class BookmarkBoard(
    val day: LocalDate,
    val time: LocalTime,
    val sort: BookmarkSort,
    /** Groups in their custom order. */
    val groups: List<GroupState>,
    val ungrouped: List<BookmarkState>,
    /** Every bookmark in the current sort mode. */
    val all: List<BookmarkState>,
) {
    /** Every bookmark in the order of the Bookmarks list: each group's in group order, then Ungrouped. */
    val listed: List<BookmarkState> get() = groups.flatMap { it.bookmarks } + ungrouped

    /** Available at this moment and not done, under their group's header, then Ungrouped. */
    val now: List<NowSection>
        get() {
            fun due(items: List<BookmarkState>) =
                items.filter { it.status == ScheduleStatus.AVAILABLE_NOW && !it.progress.done }
            val grouped = groups.map { NowSection(it, due(it.bookmarks)) }
            return (grouped + NowSection(null, due(ungrouped))).filter { it.items.isNotEmpty() }
        }

    /** "2 left" on Today. */
    val remainingNow: Int get() = now.sumOf { it.items.size }

    val laterToday: List<BookmarkState>
        get() = all.filter { it.status == ScheduleStatus.LATER_TODAY && !it.progress.done }

    val earlierToday: List<BookmarkState>
        get() = all.filter { it.status == ScheduleStatus.EARLIER_TODAY && !it.progress.done }

    /** Scheduled for today and read to the target, whatever its window says. */
    val doneToday: List<BookmarkState>
        get() = all.filter { it.progress.done && it.status in SCHEDULED_TODAY }

    fun state(bookmarkId: Long): BookmarkState? = all.firstOrNull { it.bookmark.id == bookmarkId }

    private companion object {
        val SCHEDULED_TODAY =
            setOf(ScheduleStatus.AVAILABLE_NOW, ScheduleStatus.LATER_TODAY, ScheduleStatus.EARLIER_TODAY)
    }
}

/** Schedules, availability and today's progress of bookmarks and groups (§4.7). */
class Availability @Inject constructor(
    private val bookmarks: BookmarkStore,
    private val log: ReadLogStore,
    private val prefs: PrefsStore,
    private val clock: DayClock,
    private val tx: TransactionRunner,
) {
    /**
     * The board, re-evaluated when bookmarks, groups, today's readings, the reading day or the sort mode
     * change, and on every emission of [ticks].
     *
     * A window opening or closing is the passage of time, not a data change, so nothing above announces
     * it: screens that show availability pass a ticker (once a minute is enough) as [ticks].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(ticks: Flow<Unit> = emptyFlow()): Flow<BookmarkBoard> = clock.todayFlow.flatMapLatest { day ->
        combine(
            bookmarks.observeBookmarks(),
            bookmarks.observeGroups(),
            log.observeBookmarkAyahCounts(day),
            prefs.prefs,
            ticks.onStart { emit(Unit) },
        ) { bookmarkList, groupList, counts, userPrefs, _ ->
            // The time is read at each evaluation; the day comes from todayFlow so that the counts and
            // the weekday always describe the same reading day.
            board(bookmarkList, groupList, counts, day, clock.localTime(), userPrefs.dayStartMinutes, userPrefs.bookmarkSort)
        }
    }

    suspend fun board(): BookmarkBoard = observe().first()

    /**
     * The `once` roll-over: a `once` bookmark that was done on a reading day before today loses its own
     * schedule; the bookmark is kept. Call at start-up and whenever the reading day changes.
     * Returns the ids of the bookmarks that were cleared.
     */
    suspend fun rollOverOnce(): List<Long> = tx.inTransaction {
        val today = clock.today()
        val now = clock.now()
        val cleared = ArrayList<Long>()
        for (bookmark in bookmarks.bookmarks()) {
            if (bookmark.schedule?.once != true) continue
            val pastDays = log.ayahDayCounts(ReadContext.Bookmark(bookmark.id), bookmark.range, null, today.minusDays(1))
            val doneOnce = pastDays.groupBy { it.day }.values.any { rows ->
                rows.count { it.count >= bookmark.repeatTarget } == bookmark.range.size
            }
            if (doneOnce) {
                bookmarks.updateBookmark(bookmark.copy(schedule = null, updatedAt = now))
                cleared += bookmark.id
            }
        }
        cleared
    }

    companion object {
        private const val DAY_SECONDS = 24 * 60 * 60

        fun effectiveSchedule(bookmark: Bookmark, group: BookmarkGroup?): Schedule? = bookmark.schedule ?: group?.schedule

        /**
         * Evaluates a schedule against the reading [day] and the device's local [time].
         *
         * A reading day runs from the "Day starts at" time to the same time the next calendar day, so
         * times are first measured from that start. A window that still crosses the day boundary after
         * that (22:00–02:00 with a midnight day start) belongs to the weekday on which it starts: its
         * part after the boundary is tested against the previous weekday.
         */
        fun status(schedule: Schedule?, day: LocalDate, time: LocalTime, dayStartMinutes: Int = 0): ScheduleStatus {
            if (schedule == null) return ScheduleStatus.UNSCHEDULED
            // `once` means due every day until done, whatever weekdays it carries.
            fun scheduledOn(date: LocalDate) = schedule.once || date.dayOfWeek in schedule.days
            val today = scheduledOn(day)
            val window = schedule.window
                ?: return if (today) ScheduleStatus.AVAILABLE_NOW else ScheduleStatus.NOT_TODAY

            fun sinceDayStart(t: LocalTime) = Math.floorMod(t.toSecondOfDay() - dayStartMinutes * 60, DAY_SECONDS)
            val now = sinceDayStart(time)
            val from = sinceDayStart(window.from)
            val to = sinceDayStart(window.to)

            if (from < to) {
                return when {
                    !today -> ScheduleStatus.NOT_TODAY
                    now < from -> ScheduleStatus.LATER_TODAY
                    now < to -> ScheduleStatus.AVAILABLE_NOW
                    else -> ScheduleStatus.EARLIER_TODAY
                }
            }
            // The window crosses the day boundary: [from, end of day) today, [start of day, to) tomorrow.
            val yesterday = scheduledOn(day.minusDays(1))
            return when {
                yesterday && now < to -> ScheduleStatus.AVAILABLE_NOW
                today && now >= from -> ScheduleStatus.AVAILABLE_NOW
                today -> ScheduleStatus.LATER_TODAY
                yesterday -> ScheduleStatus.EARLIER_TODAY
                else -> ScheduleStatus.NOT_TODAY
            }
        }

        /** @param counts today's count per ayah id in context `Bookmark(bookmark.id)`. */
        fun progress(bookmark: Bookmark, counts: Map<AyahId, Int>): BookmarkProgress {
            val target = bookmark.repeatTarget
            var readings = 0
            var fullReadings = Int.MAX_VALUE
            var firstBelow: AyahId? = null
            for (ayah in bookmark.range) {
                val count = counts[ayah] ?: 0
                readings += minOf(count, target)
                fullReadings = minOf(fullReadings, count)
                if (count < target && firstBelow == null) firstBelow = ayah
            }
            return BookmarkProgress(readings, bookmark.range.size * target, fullReadings, target, firstBelow)
        }

        fun groupProgress(bookmarks: List<BookmarkProgress>): GroupProgress = GroupProgress(
            readings = bookmarks.sumOf { it.readings },
            required = bookmarks.sumOf { it.required },
            bookmarksDone = bookmarks.count { it.done },
            bookmarkCount = bookmarks.size,
        )

        /** R-BMK-5. Ties fall back to id so the order is stable. */
        fun sorted(bookmarks: List<Bookmark>, sort: BookmarkSort): List<Bookmark> = when (sort) {
            BookmarkSort.MUSHAF -> bookmarks.sortedWith(compareBy<Bookmark> { it.range.start }.thenBy { it.range.end }.thenBy { it.id })
            BookmarkSort.CUSTOM -> bookmarks.sortedWith(compareBy<Bookmark> { it.sortIndex }.thenBy { it.id })
            BookmarkSort.RECENT -> bookmarks.sortedWith(compareByDescending<Bookmark> { it.createdAt }.thenByDescending { it.id })
        }

        /** The whole derivation as a pure function of stored rows and one moment. */
        fun board(
            bookmarks: List<Bookmark>,
            groups: List<BookmarkGroup>,
            counts: List<BookmarkAyahCount>,
            day: LocalDate,
            time: LocalTime,
            dayStartMinutes: Int,
            sort: BookmarkSort,
        ): BookmarkBoard {
            val groupsById = groups.associateBy { it.id }
            val countsByBookmark = counts.groupBy { it.bookmarkId }
            val states = sorted(bookmarks, sort).map { bookmark ->
                val group = bookmark.groupId?.let(groupsById::get)
                val schedule = effectiveSchedule(bookmark, group)
                // Counts outside the range are readings made before the passage was edited; they stay in
                // the day total but say nothing about today's progress through the current passage.
                val ayahCounts = countsByBookmark[bookmark.id].orEmpty()
                    .filter { it.ayahId in bookmark.range }
                    .associate { it.ayahId to it.count }
                BookmarkState(bookmark, group, schedule, status(schedule, day, time, dayStartMinutes), progress(bookmark, ayahCounts))
            }
            val groupStates = groups.sortedWith(compareBy<BookmarkGroup> { it.sortIndex }.thenBy { it.id }).map { group ->
                val members = states.filter { it.group?.id == group.id }
                GroupState(group, members, status(group.schedule, day, time, dayStartMinutes), groupProgress(members.map { it.progress }))
            }
            return BookmarkBoard(day, time, sort, groupStates, states.filter { it.group == null }, states)
        }
    }
}
