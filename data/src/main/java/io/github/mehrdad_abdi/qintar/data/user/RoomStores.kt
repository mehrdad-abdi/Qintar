package io.github.mehrdad_abdi.qintar.data.user

import androidx.room.withTransaction
import io.github.mehrdad_abdi.qintar.domain.model.AyahDayCount
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRecency
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkAyahCount
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.SourceCount
import io.github.mehrdad_abdi.qintar.domain.model.Tally
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/*
 * Room implementations of the storage ports. They map rows to models and nothing else; the in-memory
 * fakes in :domain's test fixtures are the reference behaviour and the contract tests hold both to it.
 *
 * Room re-runs an observed query whenever its table changes, whether or not the result changed, so
 * every flow ends in distinctUntilChanged(): observers see one emission per real change, as the ports
 * promise and the fakes do.
 */

/**
 * Room's own suspending transaction. A nested call joins the transaction of the coroutine it is called
 * in, so a failure anywhere rolls back everything the outermost block wrote.
 */
@Singleton
class RoomTransactionRunner @Inject constructor(private val db: QintarDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}

@Singleton
class RoomReadLogStore @Inject constructor(private val dao: ReadLogDao) : ReadLogStore {

    // ---- writes ----

    override suspend fun insertEvents(events: List<NewReadEvent>): List<ReadEvent> {
        if (events.isEmpty()) return emptyList()
        val ids = dao.insertEvents(events.map { it.toEntity() })
        return events.mapIndexed { i, e -> ReadEvent(ids[i], e.ayahId, e.day, e.readAt.atMillis(), e.context, e.method, e.batchId) }
    }

    override suspend fun deleteEvents(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else dao.deleteEvents(ids)

    override suspend fun deleteBatch(batchId: String): Int = dao.deleteBatch(batchId)

    override suspend fun insertTally(tally: NewTally): Tally {
        val createdAt = tally.createdAt.atMillis()
        val id = dao.insertTally(ReadTallyEntity(0, tally.day.toEpochDay(), tally.count, tally.note, createdAt.toEpochMilli()))
        return Tally(id, tally.day, tally.count, tally.note, createdAt)
    }

    override suspend fun updateTally(tally: Tally) = dao.updateTally(tally.id, tally.day.toEpochDay(), tally.count, tally.note)

    override suspend fun deleteTally(id: Long): Int = dao.deleteTally(id)

    override suspend fun deleteAll() = dao.deleteAll()

    // ---- one-shot reads ----

    override suspend fun event(id: Long): ReadEvent? = dao.event(id)?.toModel()

    override suspend fun tally(id: Long): Tally? = dao.tally(id)?.toModel()

    override suspend fun eventsOfBatch(batchId: String): List<ReadEvent> = dao.eventsOfBatch(batchId).map { it.toModel() }

    override suspend fun events(context: ReadContext, day: LocalDate, range: AyahRange): List<ReadEvent> =
        dao.events(context.kind.name, context.id, day.toEpochDay(), range.start.value, range.end.value).map { it.toModel() }

    override suspend fun ayahCounts(context: ReadContext, day: LocalDate, range: AyahRange): Map<AyahId, Int> =
        dao.ayahCounts(context.kind.name, context.id, day.toEpochDay(), range.start.value, range.end.value).toCountMap()

    override suspend fun distinctAyahs(context: ReadContext, range: AyahRange): Set<AyahId> =
        dao.distinctAyahs(context.kind.name, context.id, range.start.value, range.end.value).toAyahSet()

    override suspend fun distinctAyahCount(context: ReadContext, range: AyahRange): Int =
        dao.distinctAyahCount(context.kind.name, context.id, range.start.value, range.end.value)

    override suspend fun ayahDayCounts(
        context: ReadContext,
        range: AyahRange,
        from: LocalDate?,
        to: LocalDate?,
    ): List<AyahDayCount> =
        dao.ayahDayCounts(context.kind.name, context.id, range.start.value, range.end.value, from?.toEpochDay(), to?.toEpochDay())
            .map { it.toModel() }

    override suspend fun bookmarkAyahCounts(day: LocalDate): List<BookmarkAyahCount> =
        dao.bookmarkAyahCounts(day.toEpochDay()).map { it.toModel() }

    override suspend fun allEvents(): List<ReadEvent> = dao.allEvents().map { it.toModel() }

    override suspend fun allTallies(): List<Tally> = dao.allTallies().map { it.toModel() }

    // ---- observed ----

    override fun observeEventCountsByDay(from: LocalDate?, to: LocalDate?): Flow<Map<LocalDate, Int>> =
        dao.observeEventCountsByDay(from?.toEpochDay(), to?.toEpochDay()).map { it.toDayMap() }.distinctUntilChanged()

    override fun observeTallySumsByDay(from: LocalDate?, to: LocalDate?): Flow<Map<LocalDate, Int>> =
        dao.observeTallySumsByDay(from?.toEpochDay(), to?.toEpochDay()).map { it.toDayMap() }.distinctUntilChanged()

    override fun observeSourceCounts(from: LocalDate?, to: LocalDate?): Flow<List<SourceCount>> =
        dao.observeSourceCounts(from?.toEpochDay(), to?.toEpochDay()).map { rows ->
            rows.map { SourceCount(ContextKind.valueOf(it.contextType), ReadMethod.valueOf(it.method), it.count) }
                .sortedWith(compareBy({ it.context }, { it.method }))
        }.distinctUntilChanged()

    override fun observeAyahCounts(context: ReadContext, day: LocalDate, range: AyahRange): Flow<Map<AyahId, Int>> =
        dao.observeAyahCounts(context.kind.name, context.id, day.toEpochDay(), range.start.value, range.end.value)
            .map { it.toCountMap() }.distinctUntilChanged()

    override fun observeDistinctAyahs(context: ReadContext, range: AyahRange): Flow<Set<AyahId>> =
        dao.observeDistinctAyahs(context.kind.name, context.id, range.start.value, range.end.value)
            .map { it.toAyahSet() }.distinctUntilChanged()

    override fun observeNewestEvent(context: ReadContext): Flow<ReadEvent?> =
        dao.observeNewestEvent(context.kind.name, context.id).map { it?.toModel() }.distinctUntilChanged()

    override fun observeAyahDayCounts(
        context: ReadContext,
        range: AyahRange,
        from: LocalDate?,
        to: LocalDate?,
    ): Flow<List<AyahDayCount>> =
        dao.observeAyahDayCounts(context.kind.name, context.id, range.start.value, range.end.value, from?.toEpochDay(), to?.toEpochDay())
            .map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override fun observeBookmarkAyahCounts(day: LocalDate): Flow<List<BookmarkAyahCount>> =
        dao.observeBookmarkAyahCounts(day.toEpochDay()).map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override fun observeRecency(range: AyahRange): Flow<List<AyahRecency>> =
        dao.observeRecency(range.start.value, range.end.value).map { rows ->
            rows.map { AyahRecency(AyahId(it.ayahId), LocalDate.ofEpochDay(it.lastDay), it.count) }
        }.distinctUntilChanged()

    override fun observeEventsOfDay(day: LocalDate): Flow<List<ReadEvent>> =
        dao.observeEventsOfDay(day.toEpochDay()).map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override fun observeTalliesOfDay(day: LocalDate): Flow<List<Tally>> =
        dao.observeTalliesOfDay(day.toEpochDay()).map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()
}

@Singleton
class RoomKhatmStore @Inject constructor(private val dao: KhatmDao) : KhatmStore {
    override fun observeKhatms(): Flow<List<Khatm>> = dao.observeKhatms().map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override suspend fun khatms(): List<Khatm> = dao.khatms().map { it.toModel() }

    override suspend fun khatm(id: Long): Khatm? = dao.khatm(id)?.toModel()

    override suspend fun insert(khatm: Khatm, createdAt: Instant): Long = dao.insert(
        KhatmEntity(
            id = khatm.id,
            name = khatm.name,
            startedDay = khatm.startedDay.toEpochDay(),
            dailyPages = khatm.dailyPages,
            assumedCoveredUpTo = khatm.assumedCoveredUpTo,
            completedAt = khatm.completedAt?.toEpochMilli(),
            createdAt = createdAt.toEpochMilli(),
        ),
    )

    override suspend fun update(khatm: Khatm) = dao.update(
        id = khatm.id,
        name = khatm.name,
        startedDay = khatm.startedDay.toEpochDay(),
        dailyPages = khatm.dailyPages,
        assumedCoveredUpTo = khatm.assumedCoveredUpTo,
        completedAt = khatm.completedAt?.toEpochMilli(),
    )

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}

@Singleton
class RoomBookmarkStore @Inject constructor(private val dao: BookmarkDao) : BookmarkStore {
    override fun observeBookmarks(): Flow<List<Bookmark>> =
        dao.observeBookmarks().map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override suspend fun bookmarks(): List<Bookmark> = dao.bookmarks().map { it.toModel() }

    override suspend fun bookmark(id: Long): Bookmark? = dao.bookmark(id)?.toModel()

    override suspend fun insertBookmark(bookmark: Bookmark): Long = dao.insertBookmark(bookmark.toEntity())

    override suspend fun updateBookmark(bookmark: Bookmark) = dao.updateBookmark(bookmark.toEntity())

    override suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    override fun observeGroups(): Flow<List<BookmarkGroup>> =
        dao.observeGroups().map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override suspend fun groups(): List<BookmarkGroup> = dao.groups().map { it.toModel() }

    override suspend fun group(id: Long): BookmarkGroup? = dao.group(id)?.toModel()

    override suspend fun insertGroup(group: BookmarkGroup): Long = dao.insertGroup(group.toEntity())

    override suspend fun updateGroup(group: BookmarkGroup) = dao.updateGroup(group.toEntity())

    override suspend fun deleteGroup(id: Long) = dao.deleteGroup(id)

    override suspend fun deleteAll() = dao.deleteAll()
}

// ---- mapping ----

/** What the `read_at` / `created_at` columns can hold: the moment at millisecond precision. */
private fun Instant.atMillis(): Instant = Instant.ofEpochMilli(toEpochMilli())

private fun NewReadEvent.toEntity() = ReadEventEntity(
    ayahId = ayahId.value,
    day = day.toEpochDay(),
    readAt = readAt.toEpochMilli(),
    contextType = context.kind.name,
    contextId = context.id,
    method = method.name,
    batchId = batchId,
)

private fun ReadEventEntity.toModel(): ReadEvent {
    val kind = ContextKind.valueOf(contextType)
    return ReadEvent(
        id = id,
        ayahId = AyahId(ayahId),
        day = LocalDate.ofEpochDay(day),
        readAt = Instant.ofEpochMilli(readAt),
        context = ReadContext.of(kind, contextId) ?: error("read_event $id: $contextType without a context id"),
        method = ReadMethod.valueOf(method),
        batchId = batchId,
    )
}

private fun ReadTallyEntity.toModel() = Tally(id, LocalDate.ofEpochDay(day), count, note, Instant.ofEpochMilli(createdAt))

private fun List<AyahCountRow>.toCountMap(): Map<AyahId, Int> = associateTo(LinkedHashMap()) { AyahId(it.ayahId) to it.count }

private fun List<Int>.toAyahSet(): Set<AyahId> = mapTo(LinkedHashSet()) { AyahId(it) }

private fun List<DayCountRow>.toDayMap(): Map<LocalDate, Int> = associateTo(LinkedHashMap()) { LocalDate.ofEpochDay(it.day) to it.count }

private fun AyahDayCountRow.toModel() = AyahDayCount(AyahId(ayahId), LocalDate.ofEpochDay(day), count)

private fun BookmarkAyahCountRow.toModel() = BookmarkAyahCount(bookmarkId, AyahId(ayahId), count)

private fun KhatmEntity.toModel() = Khatm(
    id = id,
    name = name,
    startedDay = LocalDate.ofEpochDay(startedDay),
    dailyPages = dailyPages,
    assumedCoveredUpTo = assumedCoveredUpTo,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
)

/** Bit 0 = Monday … bit 6 = Sunday (§7.5). */
internal fun daysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

internal fun maskToDays(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filterTo(LinkedHashSet()) { mask and (1 shl (it.value - 1)) != 0 }

private fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

private fun minuteOfDay(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)

/** 0 days = no schedule; a window is stored only when both ends are. */
private fun scheduleOf(days: Int, from: Int?, to: Int?, once: Boolean): Schedule? {
    if (days == 0) return null
    val window = if (from != null && to != null && from != to) TimeWindow(minuteOfDay(from), minuteOfDay(to)) else null
    return Schedule(maskToDays(days), window, once)
}

private fun Bookmark.toEntity() = BookmarkEntity(
    id = id,
    startAyahId = range.start.value,
    endAyahId = range.end.value,
    kind = kind.name,
    label = label,
    description = description,
    repeatTarget = repeatTarget,
    groupId = groupId,
    scheduleDays = schedule?.days?.let(::daysToMask) ?: 0,
    windowFrom = schedule?.window?.from?.minuteOfDay(),
    windowTo = schedule?.window?.to?.minuteOfDay(),
    scheduleOnce = schedule?.once ?: false,
    sortIndex = sortIndex,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

private fun BookmarkEntity.toModel() = Bookmark(
    id = id,
    range = AyahRange.of(startAyahId, endAyahId),
    kind = BookmarkKind.valueOf(kind),
    label = label,
    description = description,
    repeatTarget = repeatTarget,
    groupId = groupId,
    schedule = scheduleOf(scheduleDays, windowFrom, windowTo, scheduleOnce),
    sortIndex = sortIndex,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

private fun BookmarkGroup.toEntity() = BookmarkGroupEntity(
    id = id,
    name = name,
    description = description,
    scheduleDays = schedule?.days?.let(::daysToMask) ?: 0,
    windowFrom = schedule?.window?.from?.minuteOfDay(),
    windowTo = schedule?.window?.to?.minuteOfDay(),
    sortIndex = sortIndex,
    createdAt = createdAt.toEpochMilli(),
)

private fun BookmarkGroupEntity.toModel() = BookmarkGroup(
    id = id,
    name = name,
    description = description,
    // A group is never "once" (§4.7), so the table has no such column.
    schedule = scheduleOf(scheduleDays, windowFrom, windowTo, once = false),
    sortIndex = sortIndex,
    createdAt = Instant.ofEpochMilli(createdAt),
)
