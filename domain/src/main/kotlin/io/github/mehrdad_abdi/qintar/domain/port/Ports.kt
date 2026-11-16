package io.github.mehrdad_abdi.qintar.domain.port

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
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/*
 * Storage ports. Implementations are thin: they store, filter and aggregate exactly as documented and
 * decide nothing. Every rule (what a day total is, what "covered" means, when a bookmark is done) lives
 * in the domain services that call them.
 *
 * Conventions shared by all ports:
 *  - `suspend` reads are one-shot and must see the writes of the transaction they are called in;
 *    domain services use them inside [TransactionRunner.inTransaction].
 *  - `observe…` flows emit the current value on collection and again after every change that affects it.
 *  - A null `from` / `to` day means unbounded on that side. Bounds are inclusive.
 *  - A context matches on both its kind and its id (`context_type = ? AND context_id IS ?`).
 */

/** Runs a block so that all its writes are applied together or not at all (§7.6). Calls may nest. */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/**
 * `read_event` and `read_tally` (§7.5). Only `ReadingTracker` may call the write methods.
 */
interface ReadLogStore {
    // ---- writes ----

    /** Inserts in list order, assigning ascending ids in that order; returns the stored rows in the same order. */
    suspend fun insertEvents(events: List<NewReadEvent>): List<ReadEvent>

    /** Returns the number of rows deleted. Unknown ids are ignored. */
    suspend fun deleteEvents(ids: Collection<Long>): Int

    /** Deletes every event with this batch id; returns the number deleted. */
    suspend fun deleteBatch(batchId: String): Int

    suspend fun insertTally(tally: NewTally): Tally

    /** Replaces day, count and note of the tally with this id; no-op if it does not exist. */
    suspend fun updateTally(tally: Tally)

    suspend fun deleteTally(id: Long): Int

    /** Deletes every event and tally (backup "Replace everything"). */
    suspend fun deleteAll()

    // ---- one-shot reads ----

    suspend fun event(id: Long): ReadEvent?

    suspend fun tally(id: Long): Tally?

    /** Ordered by id. */
    suspend fun eventsOfBatch(batchId: String): List<ReadEvent>

    /** Events of one context on one day whose ayah is in [range], ordered by (readAt, id) ascending. */
    suspend fun events(context: ReadContext, day: LocalDate, range: AyahRange): List<ReadEvent>

    /** Number of events per ayah for one context on one day within [range]. Ayahs with none are absent. */
    suspend fun ayahCounts(context: ReadContext, day: LocalDate, range: AyahRange): Map<AyahId, Int>

    /** The ayahs in [range] that have at least one event in [context], on any day. */
    suspend fun distinctAyahs(context: ReadContext, range: AyahRange): Set<AyahId>

    /** `COUNT(DISTINCT ayah_id)` for [context] within [range], on any day. */
    suspend fun distinctAyahCount(context: ReadContext, range: AyahRange): Int

    /** Number of events per (ayah, day) for one context within [range] and the day bounds. */
    suspend fun ayahDayCounts(
        context: ReadContext,
        range: AyahRange,
        from: LocalDate?,
        to: LocalDate?,
    ): List<AyahDayCount>

    /** Number of events per (bookmark id, ayah) over every Bookmark context on one day. */
    suspend fun bookmarkAyahCounts(day: LocalDate): List<BookmarkAyahCount>

    /** Everything, ordered by id (backup export, merge de-duplication). */
    suspend fun allEvents(): List<ReadEvent>

    suspend fun allTallies(): List<Tally>

    // ---- observed ----

    /** `COUNT(*)` of events per day. Days with none are absent. Tallies are NOT included. */
    fun observeEventCountsByDay(from: LocalDate?, to: LocalDate?): Flow<Map<LocalDate, Int>>

    /** `SUM(count)` of tallies per day. Days with none are absent. */
    fun observeTallySumsByDay(from: LocalDate?, to: LocalDate?): Flow<Map<LocalDate, Int>>

    /** `COUNT(*)` of events per (context kind, method) within the day bounds. */
    fun observeSourceCounts(from: LocalDate?, to: LocalDate?): Flow<List<SourceCount>>

    /** Observed form of [ayahCounts]: the reader's counts for its visible window. */
    fun observeAyahCounts(context: ReadContext, day: LocalDate, range: AyahRange): Flow<Map<AyahId, Int>>

    /** Observed form of [distinctAyahs]. */
    fun observeDistinctAyahs(context: ReadContext, range: AyahRange): Flow<Set<AyahId>>

    /** The event of [context] that is last by (readAt, id); null when it has none. */
    fun observeNewestEvent(context: ReadContext): Flow<ReadEvent?>

    /** Observed form of [ayahDayCounts]. */
    fun observeAyahDayCounts(
        context: ReadContext,
        range: AyahRange,
        from: LocalDate?,
        to: LocalDate?,
    ): Flow<List<AyahDayCount>>

    /** Observed form of [bookmarkAyahCounts]. */
    fun observeBookmarkAyahCounts(day: LocalDate): Flow<List<BookmarkAyahCount>>

    /** `MAX(day)` and `COUNT(*)` per ayah in [range] over every context. Never-read ayahs are absent. */
    fun observeRecency(range: AyahRange): Flow<List<AyahRecency>>

    /** Every event of a reading day, ordered by (readAt, id). */
    fun observeEventsOfDay(day: LocalDate): Flow<List<ReadEvent>>

    /** Every tally of a reading day, ordered by id. */
    fun observeTalliesOfDay(day: LocalDate): Flow<List<Tally>>
}

/** The `khatm` table. Deleting a khatm leaves its events in the log (§4.2). */
interface KhatmStore {
    /** Ordered by id. */
    fun observeKhatms(): Flow<List<Khatm>>

    suspend fun khatms(): List<Khatm>

    suspend fun khatm(id: Long): Khatm?

    /**
     * Inserts and returns the row id. `khatm.id == 0` asks for a generated id; any other id is kept
     * (backup restore). [createdAt] fills the `created_at` column, which the model does not carry.
     */
    suspend fun insert(khatm: Khatm, createdAt: Instant): Long

    suspend fun update(khatm: Khatm)

    suspend fun delete(id: Long)

    suspend fun deleteAll()
}

/** The `bookmark` and `bookmark_group` tables. Deleting either leaves events in the log. */
interface BookmarkStore {
    /** Ordered by id; sorting for display is a domain rule. */
    fun observeBookmarks(): Flow<List<Bookmark>>

    suspend fun bookmarks(): List<Bookmark>

    suspend fun bookmark(id: Long): Bookmark?

    /** `bookmark.id == 0` asks for a generated id; any other id is kept. Returns the row id. */
    suspend fun insertBookmark(bookmark: Bookmark): Long

    suspend fun updateBookmark(bookmark: Bookmark)

    suspend fun deleteBookmark(id: Long)

    /** Ordered by id. */
    fun observeGroups(): Flow<List<BookmarkGroup>>

    suspend fun groups(): List<BookmarkGroup>

    suspend fun group(id: Long): BookmarkGroup?

    /** `group.id == 0` asks for a generated id; any other id is kept. Returns the row id. */
    suspend fun insertGroup(group: BookmarkGroup): Long

    suspend fun updateGroup(group: BookmarkGroup)

    /** Deletes the group only; its bookmarks become ungrouped (`ON DELETE SET NULL`). */
    suspend fun deleteGroup(id: Long)

    /** Deletes every bookmark and group. */
    suspend fun deleteAll()
}

/** The one typed preferences object (DataStore). */
interface PrefsStore {
    val prefs: Flow<UserPrefs>

    /** Atomically replaces the preferences with `transform(current)` and returns the new value. */
    suspend fun update(transform: (UserPrefs) -> UserPrefs): UserPrefs
}

data class AyahText(val id: AyahId, val text: String)

/** Verbatim Quran text from `quran.db`, read on demand. The text never includes the basmala prefix. */
interface QuranTextSource {
    /** Every ayah of [range], in order. */
    suspend fun ayahs(range: AyahRange): List<AyahText>

    /** Every ayah of a mushaf page (1…604), in order. */
    suspend fun page(page: Int): List<AyahText>
}
