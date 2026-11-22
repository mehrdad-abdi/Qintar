package io.github.mehrdad_abdi.qintar.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val IN_CONTEXT = "context_type = :type AND context_id IS :contextId"
private const val IN_RANGE = "ayah_id BETWEEN :start AND :end"
private const val IN_DAYS = "(:from IS NULL OR day >= :from) AND (:to IS NULL OR day <= :to)"

private const val AYAH_COUNTS =
    "SELECT ayah_id, COUNT(*) AS n FROM read_event WHERE $IN_CONTEXT AND day = :day AND $IN_RANGE " +
        "GROUP BY ayah_id ORDER BY ayah_id"
private const val DISTINCT_AYAHS =
    "SELECT DISTINCT ayah_id FROM read_event WHERE $IN_CONTEXT AND $IN_RANGE ORDER BY ayah_id"
private const val AYAH_DAY_COUNTS =
    "SELECT ayah_id, day, COUNT(*) AS n FROM read_event WHERE $IN_CONTEXT AND $IN_RANGE AND $IN_DAYS " +
        "GROUP BY day, ayah_id ORDER BY day, ayah_id"
private const val BOOKMARK_AYAH_COUNTS =
    "SELECT context_id, ayah_id, COUNT(*) AS n FROM read_event " +
        "WHERE day = :day AND context_type = 'BOOKMARK' AND context_id IS NOT NULL " +
        "GROUP BY context_id, ayah_id ORDER BY context_id, ayah_id"

/** `read_event` and `read_tally`. Every query is a plain filter or aggregate; rules live in `:domain`. */
@Dao
interface ReadLogDao {
    // ---- writes ----

    /** Row ids come back in list order and ascend in that order. */
    @Insert
    suspend fun insertEvents(events: List<ReadEventEntity>): List<Long>

    @Query("DELETE FROM read_event WHERE id IN (:ids)")
    suspend fun deleteEventsChunk(ids: List<Long>): Int

    /** Chunked so that any number of ids stays under SQLite's bound-variable limit. */
    @Transaction
    suspend fun deleteEvents(ids: Collection<Long>): Int =
        ids.distinct().chunked(MAX_VARIABLES).sumOf { deleteEventsChunk(it) }

    @Query("DELETE FROM read_event WHERE batch_id = :batchId")
    suspend fun deleteBatch(batchId: String): Int

    @Insert
    suspend fun insertTally(tally: ReadTallyEntity): Long

    @Query("UPDATE read_tally SET day = :day, count = :count, note = :note WHERE id = :id")
    suspend fun updateTally(id: Long, day: Long, count: Int, note: String?)

    @Query("DELETE FROM read_tally WHERE id = :id")
    suspend fun deleteTally(id: Long): Int

    @Query("DELETE FROM read_event")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM read_tally")
    suspend fun deleteAllTallies()

    @Transaction
    suspend fun deleteAll() {
        deleteAllEvents()
        deleteAllTallies()
    }

    // ---- one-shot reads ----

    @Query("SELECT * FROM read_event WHERE id = :id")
    suspend fun event(id: Long): ReadEventEntity?

    @Query("SELECT * FROM read_tally WHERE id = :id")
    suspend fun tally(id: Long): ReadTallyEntity?

    @Query("SELECT * FROM read_event WHERE batch_id = :batchId ORDER BY id")
    suspend fun eventsOfBatch(batchId: String): List<ReadEventEntity>

    @Query("SELECT * FROM read_event WHERE $IN_CONTEXT AND day = :day AND $IN_RANGE ORDER BY read_at, id")
    suspend fun events(type: String, contextId: Long?, day: Long, start: Int, end: Int): List<ReadEventEntity>

    @Query(AYAH_COUNTS)
    suspend fun ayahCounts(type: String, contextId: Long?, day: Long, start: Int, end: Int): List<AyahCountRow>

    @Query(DISTINCT_AYAHS)
    suspend fun distinctAyahs(type: String, contextId: Long?, start: Int, end: Int): List<Int>

    @Query("SELECT COUNT(DISTINCT ayah_id) FROM read_event WHERE $IN_CONTEXT AND $IN_RANGE")
    suspend fun distinctAyahCount(type: String, contextId: Long?, start: Int, end: Int): Int

    @Query(AYAH_DAY_COUNTS)
    suspend fun ayahDayCounts(
        type: String,
        contextId: Long?,
        start: Int,
        end: Int,
        from: Long?,
        to: Long?,
    ): List<AyahDayCountRow>

    @Query(BOOKMARK_AYAH_COUNTS)
    suspend fun bookmarkAyahCounts(day: Long): List<BookmarkAyahCountRow>

    @Query("SELECT * FROM read_event ORDER BY id")
    suspend fun allEvents(): List<ReadEventEntity>

    @Query("SELECT * FROM read_tally ORDER BY id")
    suspend fun allTallies(): List<ReadTallyEntity>

    // ---- observed ----

    @Query("SELECT day, COUNT(*) AS n FROM read_event WHERE $IN_DAYS GROUP BY day ORDER BY day")
    fun observeEventCountsByDay(from: Long?, to: Long?): Flow<List<DayCountRow>>

    @Query("SELECT day, SUM(count) AS n FROM read_tally WHERE $IN_DAYS GROUP BY day ORDER BY day")
    fun observeTallySumsByDay(from: Long?, to: Long?): Flow<List<DayCountRow>>

    @Query("SELECT context_type, method, COUNT(*) AS n FROM read_event WHERE $IN_DAYS GROUP BY context_type, method")
    fun observeSourceCounts(from: Long?, to: Long?): Flow<List<SourceCountRow>>

    @Query(AYAH_COUNTS)
    fun observeAyahCounts(type: String, contextId: Long?, day: Long, start: Int, end: Int): Flow<List<AyahCountRow>>

    @Query(DISTINCT_AYAHS)
    fun observeDistinctAyahs(type: String, contextId: Long?, start: Int, end: Int): Flow<List<Int>>

    @Query("SELECT * FROM read_event WHERE $IN_CONTEXT ORDER BY read_at DESC, id DESC LIMIT 1")
    fun observeNewestEvent(type: String, contextId: Long?): Flow<ReadEventEntity?>

    @Query(AYAH_DAY_COUNTS)
    fun observeAyahDayCounts(
        type: String,
        contextId: Long?,
        start: Int,
        end: Int,
        from: Long?,
        to: Long?,
    ): Flow<List<AyahDayCountRow>>

    @Query(BOOKMARK_AYAH_COUNTS)
    fun observeBookmarkAyahCounts(day: Long): Flow<List<BookmarkAyahCountRow>>

    @Query(
        "SELECT context_id, MAX(day) AS last_day FROM read_event " +
            "WHERE context_type = 'BOOKMARK' AND context_id IS NOT NULL GROUP BY context_id",
    )
    fun observeBookmarkLastRead(): Flow<List<BookmarkLastReadRow>>

    @Query(
        "SELECT ayah_id, MAX(day) AS last_day, COUNT(*) AS n FROM read_event WHERE $IN_RANGE " +
            "GROUP BY ayah_id ORDER BY ayah_id",
    )
    fun observeRecency(start: Int, end: Int): Flow<List<RecencyRow>>

    @Query("SELECT * FROM read_event WHERE day = :day ORDER BY read_at, id")
    fun observeEventsOfDay(day: Long): Flow<List<ReadEventEntity>>

    @Query("SELECT * FROM read_tally WHERE day = :day ORDER BY id")
    fun observeTalliesOfDay(day: Long): Flow<List<ReadTallyEntity>>

    companion object {
        const val MAX_VARIABLES = 900
    }
}

@Dao
interface KhatmDao {
    @Query("SELECT * FROM khatm ORDER BY id")
    fun observeKhatms(): Flow<List<KhatmEntity>>

    @Query("SELECT * FROM khatm ORDER BY id")
    suspend fun khatms(): List<KhatmEntity>

    @Query("SELECT * FROM khatm WHERE id = :id")
    suspend fun khatm(id: Long): KhatmEntity?

    @Insert
    suspend fun insert(khatm: KhatmEntity): Long

    /** Everything the model carries; `created_at` is left alone. */
    @Query(
        "UPDATE khatm SET name = :name, started_day = :startedDay, daily_pages = :dailyPages, " +
            "assumed_covered_up_to = :assumedCoveredUpTo, completed_at = :completedAt WHERE id = :id",
    )
    suspend fun update(
        id: Long,
        name: String,
        startedDay: Long,
        dailyPages: Float?,
        assumedCoveredUpTo: Int,
        completedAt: Long?,
    )

    @Query("DELETE FROM khatm WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM khatm")
    suspend fun deleteAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark ORDER BY id")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark ORDER BY id")
    suspend fun bookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmark WHERE id = :id")
    suspend fun bookmark(id: Long): BookmarkEntity?

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT * FROM bookmark_group ORDER BY id")
    fun observeGroups(): Flow<List<BookmarkGroupEntity>>

    @Query("SELECT * FROM bookmark_group ORDER BY id")
    suspend fun groups(): List<BookmarkGroupEntity>

    @Query("SELECT * FROM bookmark_group WHERE id = :id")
    suspend fun group(id: Long): BookmarkGroupEntity?

    @Insert
    suspend fun insertGroup(group: BookmarkGroupEntity): Long

    @Update
    suspend fun updateGroup(group: BookmarkGroupEntity)

    /** The foreign key nulls `group_id` of the group's bookmarks. */
    @Query("DELETE FROM bookmark_group WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM bookmark")
    suspend fun deleteAllBookmarks()

    @Query("DELETE FROM bookmark_group")
    suspend fun deleteAllGroups()

    @Transaction
    suspend fun deleteAll() {
        deleteAllBookmarks()
        deleteAllGroups()
    }
}
