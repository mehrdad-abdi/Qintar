package io.github.mehrdad_abdi.qintar.data.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tables of qintar.db (DESIGN.md §7.5). Columns hold primitives only: days are epoch days, moments are
 * epoch milliseconds, enums are their names. Nothing derived (totals, ranks, streaks, coverage) is stored.
 */

/** One counted reading of one ayah. `context_id` deliberately has no foreign key (§4.2). */
@Entity(
    tableName = "read_event",
    indices = [
        Index(value = ["day"], name = "read_event_day"),
        Index(value = ["ayah_id", "day"], name = "read_event_ayah"),
        Index(value = ["context_type", "context_id", "ayah_id"], name = "read_event_context"),
        Index(value = ["batch_id"], name = "read_event_batch"),
    ],
)
data class ReadEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "ayah_id") val ayahId: Int,
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "read_at") val readAt: Long,
    @ColumnInfo(name = "context_type") val contextType: String,
    @ColumnInfo(name = "context_id") val contextId: Long?,
    @ColumnInfo(name = "method") val method: String,
    @ColumnInfo(name = "batch_id") val batchId: String?,
)

@Entity(tableName = "read_tally", indices = [Index(value = ["day"], name = "read_tally_day")])
data class ReadTallyEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "khatm")
data class KhatmEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "started_day") val startedDay: Long,
    @ColumnInfo(name = "daily_pages") val dailyPages: Float?,
    @ColumnInfo(name = "assumed_covered_up_to", defaultValue = "0") val assumedCoveredUpTo: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "bookmark_group")
data class BookmarkGroupEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    /** Bit 0 = Monday … bit 6 = Sunday; 0 = no schedule. */
    @ColumnInfo(name = "schedule_days", defaultValue = "0") val scheduleDays: Int,
    /** Minutes of day; both null = any time. */
    @ColumnInfo(name = "window_from") val windowFrom: Int?,
    @ColumnInfo(name = "window_to") val windowTo: Int?,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "bookmark",
    foreignKeys = [
        ForeignKey(
            entity = BookmarkGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["group_id"], name = "bookmark_group_id")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int,
    @ColumnInfo(name = "end_ayah_id") val endAyahId: Int,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "repeat_target", defaultValue = "1") val repeatTarget: Int,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    /** 0 = no own schedule (inherit the group's). */
    @ColumnInfo(name = "schedule_days", defaultValue = "0") val scheduleDays: Int,
    @ColumnInfo(name = "window_from") val windowFrom: Int?,
    @ColumnInfo(name = "window_to") val windowTo: Int?,
    @ColumnInfo(name = "schedule_once", defaultValue = "0") val scheduleOnce: Boolean,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

// ---- query result rows ----

data class AyahCountRow(@ColumnInfo(name = "ayah_id") val ayahId: Int, @ColumnInfo(name = "n") val count: Int)

data class AyahDayCountRow(
    @ColumnInfo(name = "ayah_id") val ayahId: Int,
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "n") val count: Int,
)

data class BookmarkAyahCountRow(
    @ColumnInfo(name = "context_id") val bookmarkId: Long,
    @ColumnInfo(name = "ayah_id") val ayahId: Int,
    @ColumnInfo(name = "n") val count: Int,
)

data class DayCountRow(@ColumnInfo(name = "day") val day: Long, @ColumnInfo(name = "n") val count: Int)

data class SourceCountRow(
    @ColumnInfo(name = "context_type") val contextType: String,
    @ColumnInfo(name = "method") val method: String,
    @ColumnInfo(name = "n") val count: Int,
)

data class BookmarkLastReadRow(
    @ColumnInfo(name = "context_id") val bookmarkId: Long,
    @ColumnInfo(name = "last_day") val lastDay: Long,
)

data class RecencyRow(
    @ColumnInfo(name = "ayah_id") val ayahId: Int,
    @ColumnInfo(name = "last_day") val lastDay: Long,
    @ColumnInfo(name = "n") val count: Int,
)
