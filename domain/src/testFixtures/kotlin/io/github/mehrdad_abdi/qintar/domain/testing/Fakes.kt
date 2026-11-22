package io.github.mehrdad_abdi.qintar.domain.testing

import io.github.mehrdad_abdi.qintar.domain.model.AyahDayCount
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRecency
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkAyahCount
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.SourceCount
import io.github.mehrdad_abdi.qintar.domain.model.Tally
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.AyahText
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * In-memory implementations of the storage ports. They are the reference behaviour of each port: a
 * Room-backed implementation is correct when it answers every call the way these do.
 */

/** A fake whose whole state can be captured and put back, so that [FakeTransactionRunner] can roll it back. */
interface Restorable {
    fun snapshot(): Any

    fun restore(snapshot: Any)
}

/**
 * Runs blocks one at a time and, if a block throws, restores every registered store to its state from
 * before the block: the atomicity a database transaction gives. Nested calls join the outer transaction.
 */
class FakeTransactionRunner(private vararg val stores: Restorable) : TransactionRunner {
    private val mutex = Mutex()

    /** Number of outermost transactions that committed; lets a test assert "this was one transaction". */
    var committed: Int = 0
        private set

    private class InTransaction(val owner: FakeTransactionRunner) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<InTransaction>
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        if (coroutineContext[InTransaction]?.owner === this) return block()
        return mutex.withLock {
            val before = stores.map { it.snapshot() }
            try {
                withContext(InTransaction(this)) { block() }.also { committed++ }
            } catch (failure: Throwable) {
                stores.forEachIndexed { i, store -> store.restore(before[i]) }
                throw failure
            }
        }
    }
}

class FakeReadLogStore : ReadLogStore, Restorable {
    private data class State(
        val events: List<ReadEvent> = emptyList(),
        val tallies: List<Tally> = emptyList(),
        // Ids are never reused, like AUTOINCREMENT.
        val nextEventId: Long = 1,
        val nextTallyId: Long = 1,
    )

    private val state = MutableStateFlow(State())

    /** Direct access for assertions. */
    val events: List<ReadEvent> get() = state.value.events
    val tallies: List<Tally> get() = state.value.tallies

    override fun snapshot(): Any = state.value

    override fun restore(snapshot: Any) {
        state.value = snapshot as State
    }

    private fun <T> observe(query: (State) -> T): Flow<T> = state.map(query).distinctUntilChanged()

    private fun inDays(day: LocalDate, from: LocalDate?, to: LocalDate?) =
        (from == null || day >= from) && (to == null || day <= to)

    private val byRecording = compareBy<ReadEvent>({ it.readAt }, { it.id })

    // ---- writes ----

    override suspend fun insertEvents(events: List<NewReadEvent>): List<ReadEvent> {
        var inserted: List<ReadEvent> = emptyList()
        state.update { s ->
            inserted = events.mapIndexed { i, e ->
                ReadEvent(s.nextEventId + i, e.ayahId, e.day, e.readAt, e.context, e.method, e.batchId)
            }
            s.copy(events = s.events + inserted, nextEventId = s.nextEventId + events.size)
        }
        return inserted
    }

    override suspend fun deleteEvents(ids: Collection<Long>): Int {
        val doomed = ids.toSet()
        return removeEvents { it.id in doomed }
    }

    override suspend fun deleteBatch(batchId: String): Int = removeEvents { it.batchId == batchId }

    private fun removeEvents(predicate: (ReadEvent) -> Boolean): Int {
        var removed = 0
        state.update { s ->
            val kept = s.events.filterNot(predicate)
            removed = s.events.size - kept.size
            s.copy(events = kept)
        }
        return removed
    }

    override suspend fun insertTally(tally: NewTally): Tally {
        lateinit var inserted: Tally
        state.update { s ->
            inserted = Tally(s.nextTallyId, tally.day, tally.count, tally.note, tally.createdAt)
            s.copy(tallies = s.tallies + inserted, nextTallyId = s.nextTallyId + 1)
        }
        return inserted
    }

    override suspend fun updateTally(tally: Tally) {
        state.update { s -> s.copy(tallies = s.tallies.map { if (it.id == tally.id) tally else it }) }
    }

    override suspend fun deleteTally(id: Long): Int {
        var removed = 0
        state.update { s ->
            val kept = s.tallies.filterNot { it.id == id }
            removed = s.tallies.size - kept.size
            s.copy(tallies = kept)
        }
        return removed
    }

    override suspend fun deleteAll() {
        state.update { it.copy(events = emptyList(), tallies = emptyList()) }
    }

    // ---- queries (shared by the one-shot and observed forms) ----

    private fun State.eventsIn(context: ReadContext, range: AyahRange) =
        events.filter { it.context == context && it.ayahId in range }

    private fun State.ayahCounts(context: ReadContext, day: LocalDate, range: AyahRange): Map<AyahId, Int> =
        eventsIn(context, range).filter { it.day == day }.groupingBy { it.ayahId }.eachCount()

    private fun State.distinctAyahs(context: ReadContext, range: AyahRange): Set<AyahId> =
        eventsIn(context, range).mapTo(sortedSetOf<AyahId>()) { it.ayahId }

    private fun State.ayahDayCounts(context: ReadContext, range: AyahRange, from: LocalDate?, to: LocalDate?) =
        eventsIn(context, range).filter { inDays(it.day, from, to) }
            .groupingBy { it.ayahId to it.day }.eachCount()
            .map { (key, count) -> AyahDayCount(key.first, key.second, count) }
            .sortedWith(compareBy({ it.day }, { it.ayahId }))

    private fun State.bookmarkAyahCounts(day: LocalDate): List<BookmarkAyahCount> =
        events.filter { it.day == day && it.context is ReadContext.Bookmark }
            .groupingBy { (it.context as ReadContext.Bookmark).id to it.ayahId }.eachCount()
            .map { (key, count) -> BookmarkAyahCount(key.first, key.second, count) }
            .sortedWith(compareBy({ it.bookmarkId }, { it.ayahId }))

    // ---- one-shot reads ----

    override suspend fun event(id: Long): ReadEvent? = events.firstOrNull { it.id == id }

    override suspend fun tally(id: Long): Tally? = tallies.firstOrNull { it.id == id }

    override suspend fun eventsOfBatch(batchId: String): List<ReadEvent> = events.filter { it.batchId == batchId }

    override suspend fun events(context: ReadContext, day: LocalDate, range: AyahRange): List<ReadEvent> =
        state.value.eventsIn(context, range).filter { it.day == day }.sortedWith(byRecording)

    override suspend fun ayahCounts(context: ReadContext, day: LocalDate, range: AyahRange): Map<AyahId, Int> =
        state.value.ayahCounts(context, day, range)

    override suspend fun distinctAyahs(context: ReadContext, range: AyahRange): Set<AyahId> =
        state.value.distinctAyahs(context, range)

    override suspend fun distinctAyahCount(context: ReadContext, range: AyahRange): Int =
        state.value.distinctAyahs(context, range).size

    override suspend fun ayahDayCounts(
        context: ReadContext,
        range: AyahRange,
        from: LocalDate?,
        to: LocalDate?,
    ): List<AyahDayCount> = state.value.ayahDayCounts(context, range, from, to)

    override suspend fun bookmarkAyahCounts(day: LocalDate): List<BookmarkAyahCount> = state.value.bookmarkAyahCounts(day)

    override suspend fun allEvents(): List<ReadEvent> = events

    override suspend fun allTallies(): List<Tally> = tallies

    // ---- observed ----

    override fun observeEventCountsByDay(from: LocalDate?, to: LocalDate?): Flow<Map<LocalDate, Int>> =
        observe { s -> s.events.filter { inDays(it.day, from, to) }.groupingBy { it.day }.eachCount() }

    override fun observeTallySumsByDay(from: LocalDate?, to: LocalDate?): Flow<Map<LocalDate, Int>> =
        observe { s ->
            s.tallies.filter { inDays(it.day, from, to) }.groupBy { it.day }.mapValues { (_, rows) -> rows.sumOf { it.count } }
        }

    override fun observeSourceCounts(from: LocalDate?, to: LocalDate?): Flow<List<SourceCount>> =
        observe { s ->
            s.events.filter { inDays(it.day, from, to) }.groupingBy { it.context.kind to it.method }.eachCount()
                .map { (key, count) -> SourceCount(key.first, key.second, count) }
                .sortedWith(compareBy({ it.context }, { it.method }))
        }

    override fun observeAyahCounts(context: ReadContext, day: LocalDate, range: AyahRange): Flow<Map<AyahId, Int>> =
        observe { it.ayahCounts(context, day, range) }

    override fun observeDistinctAyahs(context: ReadContext, range: AyahRange): Flow<Set<AyahId>> =
        observe { it.distinctAyahs(context, range) }

    override fun observeNewestEvent(context: ReadContext): Flow<ReadEvent?> =
        observe { s -> s.events.filter { it.context == context }.maxWithOrNull(byRecording) }

    override fun observeAyahDayCounts(
        context: ReadContext,
        range: AyahRange,
        from: LocalDate?,
        to: LocalDate?,
    ): Flow<List<AyahDayCount>> = observe { it.ayahDayCounts(context, range, from, to) }

    override fun observeBookmarkAyahCounts(day: LocalDate): Flow<List<BookmarkAyahCount>> =
        observe { it.bookmarkAyahCounts(day) }

    override fun observeBookmarkLastRead(): Flow<Map<Long, LocalDate>> = observe { s ->
        s.events.mapNotNull { e -> (e.context as? ReadContext.Bookmark)?.let { it.id to e.day } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, days) -> days.max() }
    }

    override fun observeRecency(range: AyahRange): Flow<List<AyahRecency>> = observe { s ->
        s.events.filter { it.ayahId in range }.groupBy { it.ayahId }
            .map { (ayah, rows) -> AyahRecency(ayah, rows.maxOf { it.day }, rows.size) }
            .sortedBy { it.ayahId }
    }

    override fun observeEventsOfDay(day: LocalDate): Flow<List<ReadEvent>> =
        observe { s -> s.events.filter { it.day == day }.sortedWith(byRecording) }

    override fun observeTalliesOfDay(day: LocalDate): Flow<List<Tally>> =
        observe { s -> s.tallies.filter { it.day == day } }
}

class FakeKhatmStore : KhatmStore, Restorable {
    private data class State(val khatms: List<Khatm> = emptyList(), val nextId: Long = 1)

    private val state = MutableStateFlow(State())

    /** `created_at` per khatm id, as the real table would hold it. */
    val createdAt: MutableMap<Long, Instant> = HashMap()

    override fun snapshot(): Any = state.value

    override fun restore(snapshot: Any) {
        state.value = snapshot as State
    }

    override fun observeKhatms(): Flow<List<Khatm>> = state.map { it.khatms }.distinctUntilChanged()

    override suspend fun khatms(): List<Khatm> = state.value.khatms

    override suspend fun khatm(id: Long): Khatm? = state.value.khatms.firstOrNull { it.id == id }

    override suspend fun insert(khatm: Khatm, createdAt: Instant): Long {
        var id = 0L
        state.update { s ->
            id = if (khatm.id == 0L) s.nextId else khatm.id
            check(s.khatms.none { it.id == id }) { "Khatm id $id already exists" }
            s.copy(khatms = (s.khatms + khatm.copy(id = id)).sortedBy { it.id }, nextId = maxOf(s.nextId, id + 1))
        }
        this.createdAt[id] = createdAt
        return id
    }

    override suspend fun update(khatm: Khatm) {
        state.update { s -> s.copy(khatms = s.khatms.map { if (it.id == khatm.id) khatm else it }) }
    }

    override suspend fun delete(id: Long) {
        state.update { s -> s.copy(khatms = s.khatms.filterNot { it.id == id }) }
    }

    override suspend fun deleteAll() {
        state.update { it.copy(khatms = emptyList()) }
    }
}

class FakeBookmarkStore : BookmarkStore, Restorable {
    private data class State(
        val bookmarks: List<Bookmark> = emptyList(),
        val groups: List<BookmarkGroup> = emptyList(),
        val nextBookmarkId: Long = 1,
        val nextGroupId: Long = 1,
    )

    private val state = MutableStateFlow(State())

    override fun snapshot(): Any = state.value

    override fun restore(snapshot: Any) {
        state.value = snapshot as State
    }

    override fun observeBookmarks(): Flow<List<Bookmark>> = state.map { it.bookmarks }.distinctUntilChanged()

    override suspend fun bookmarks(): List<Bookmark> = state.value.bookmarks

    override suspend fun bookmark(id: Long): Bookmark? = state.value.bookmarks.firstOrNull { it.id == id }

    override suspend fun insertBookmark(bookmark: Bookmark): Long {
        var id = 0L
        state.update { s ->
            id = if (bookmark.id == 0L) s.nextBookmarkId else bookmark.id
            check(s.bookmarks.none { it.id == id }) { "Bookmark id $id already exists" }
            // The foreign key of the real table.
            check(bookmark.groupId == null || s.groups.any { it.id == bookmark.groupId }) { "No group ${bookmark.groupId}" }
            s.copy(
                bookmarks = (s.bookmarks + bookmark.copy(id = id)).sortedBy { it.id },
                nextBookmarkId = maxOf(s.nextBookmarkId, id + 1),
            )
        }
        return id
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        state.update { s ->
            check(bookmark.groupId == null || s.groups.any { it.id == bookmark.groupId }) { "No group ${bookmark.groupId}" }
            s.copy(bookmarks = s.bookmarks.map { if (it.id == bookmark.id) bookmark else it })
        }
    }

    override suspend fun deleteBookmark(id: Long) {
        state.update { s -> s.copy(bookmarks = s.bookmarks.filterNot { it.id == id }) }
    }

    override fun observeGroups(): Flow<List<BookmarkGroup>> = state.map { it.groups }.distinctUntilChanged()

    override suspend fun groups(): List<BookmarkGroup> = state.value.groups

    override suspend fun group(id: Long): BookmarkGroup? = state.value.groups.firstOrNull { it.id == id }

    override suspend fun insertGroup(group: BookmarkGroup): Long {
        var id = 0L
        state.update { s ->
            id = if (group.id == 0L) s.nextGroupId else group.id
            check(s.groups.none { it.id == id }) { "Group id $id already exists" }
            s.copy(groups = (s.groups + group.copy(id = id)).sortedBy { it.id }, nextGroupId = maxOf(s.nextGroupId, id + 1))
        }
        return id
    }

    override suspend fun updateGroup(group: BookmarkGroup) {
        state.update { s -> s.copy(groups = s.groups.map { if (it.id == group.id) group else it }) }
    }

    override suspend fun deleteGroup(id: Long) {
        state.update { s ->
            s.copy(
                groups = s.groups.filterNot { it.id == id },
                // ON DELETE SET NULL
                bookmarks = s.bookmarks.map { if (it.groupId == id) it.copy(groupId = null) else it },
            )
        }
    }

    override suspend fun deleteAll() {
        state.update { it.copy(bookmarks = emptyList(), groups = emptyList()) }
    }
}

class FakePrefsStore(initial: UserPrefs = UserPrefs()) : PrefsStore {
    private val state = MutableStateFlow(initial)

    override val prefs: Flow<UserPrefs> = state

    val current: UserPrefs get() = state.value

    override suspend fun update(transform: (UserPrefs) -> UserPrefs): UserPrefs {
        state.update(transform)
        return state.value
    }
}

/**
 * Deterministic stand-in text: `"2:255"` style words, optionally with caller-supplied text for chosen
 * ayahs. It never resembles Quran text; tests of formatting pass their own strings to the formatter.
 */
class FakeQuranTextSource(
    private val index: QuranIndex = QuranFixture.index,
    private val overrides: Map<AyahId, String> = emptyMap(),
) : QuranTextSource {
    private fun text(id: AyahId): AyahText {
        val ref = index.ref(id)
        return AyahText(id, overrides[id] ?: "ayah ${ref.surah}:${ref.ayah}")
    }

    override suspend fun ayahs(range: AyahRange): List<AyahText> = range.map(::text)

    override suspend fun page(page: Int): List<AyahText> = ayahs(index.pageRange(page))
}
