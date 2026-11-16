package io.github.mehrdad_abdi.qintar.domain.model

import java.time.Instant
import java.time.LocalDate

/** One counted reading of one ayah (§4.2). */
data class ReadEvent(
    val id: Long,
    val ayahId: AyahId,
    /** Reading day, fixed when written and never recomputed (§4.3). */
    val day: LocalDate,
    /** Wall-clock moment of recording. */
    val readAt: Instant,
    val context: ReadContext,
    val method: ReadMethod,
    /** Shared by events created in one bulk action. */
    val batchId: String?,
)

/** A read event that has not been given an id by the store yet. */
data class NewReadEvent(
    val ayahId: AyahId,
    val day: LocalDate,
    val readAt: Instant,
    val context: ReadContext,
    val method: ReadMethod,
    val batchId: String?,
)

/** The `context_type` column of `read_event` (§7.5). */
enum class ContextKind { KHATM, BOOKMARK, BROWSE, RANDOM, LOG }

sealed interface ReadContext {
    val kind: ContextKind

    /** khatm.id or bookmark.id; null for the contexts that have no owner row. */
    val id: Long?

    data class Khatm(override val id: Long) : ReadContext {
        override val kind get() = ContextKind.KHATM
    }

    data class Bookmark(override val id: Long) : ReadContext {
        override val kind get() = ContextKind.BOOKMARK
    }

    data object Browse : ReadContext {
        override val kind get() = ContextKind.BROWSE
        override val id: Long? get() = null
    }

    data object Random : ReadContext {
        override val kind get() = ContextKind.RANDOM
        override val id: Long? get() = null
    }

    /** Manual entry not attributed to a khatm. */
    data object Log : ReadContext {
        override val kind get() = ContextKind.LOG
        override val id: Long? get() = null
    }

    companion object {
        /** Rebuilds a context from its two stored columns; null when the pair is not a valid context. */
        fun of(kind: ContextKind, id: Long?): ReadContext? = when (kind) {
            ContextKind.KHATM -> id?.let(::Khatm)
            ContextKind.BOOKMARK -> id?.let(::Bookmark)
            ContextKind.BROWSE -> Browse
            ContextKind.RANDOM -> Random
            ContextKind.LOG -> Log
        }
    }
}

enum class ReadMethod { TAP, BULK, AUDIO, MANUAL, IMPORT }

/** A count-only manual entry with no ayah identity. */
data class Tally(val id: Long, val day: LocalDate, val count: Int, val note: String?, val createdAt: Instant)

data class NewTally(val day: LocalDate, val count: Int, val note: String?, val createdAt: Instant)

/** A contiguous passage counted under one context (§5.4). A group is one segment per bookmark. */
data class Segment(val context: ReadContext, val range: AyahRange)

enum class RangeMode {
    /** One reading for ayahs that have none in scope. */
    FILL,

    /** One more reading for every ayah, whatever its count. */
    ADD,
}

/**
 * The result of one bulk action, kept by the caller for as long as its Undo is offered.
 *
 * [removed] carries the events a lowering "Set count" deleted, so that Undo can put them back; the log
 * itself keeps no trace of deleted rows.
 */
data class Batch(
    val id: String,
    val day: LocalDate,
    val added: Int,
    val removed: List<ReadEvent> = emptyList(),
) {
    /** Change to the day total caused by this batch. */
    val net: Int get() = added - removed.size
}

/** Readings of one ayah on one day within one context. */
data class AyahDayCount(val ayahId: AyahId, val day: LocalDate, val count: Int)

/** Readings of one ayah on one day under `Bookmark(bookmarkId)`. */
data class BookmarkAyahCount(val bookmarkId: Long, val ayahId: AyahId, val count: Int)

/** §4.8: the latest reading day and number of readings of one ayah, over every context. */
data class AyahRecency(val ayahId: AyahId, val lastRead: LocalDate, val timesRead: Int)

/** Events in a period by where and how they were read (R-INS-4). */
data class SourceCount(val context: ContextKind, val method: ReadMethod, val count: Int)
