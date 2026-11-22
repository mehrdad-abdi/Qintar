package io.github.mehrdad_abdi.qintar.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** How a bookmark was created; drives its title. */
enum class BookmarkKind { AYAH, RANGE, SURAH, PAGE, JUZ }

data class Bookmark(
    val id: Long,
    val range: AyahRange,
    val kind: BookmarkKind,
    val label: String?,
    /** Free text, up to [MAX_DESCRIPTION] characters. */
    val description: String?,
    /**
     * Readings per day that make it done. Deprecated in the UI (owner, 2026-11-22): the editor no
     * longer shows it and new bookmarks get 1; kept in the model because it may come back.
     */
    val repeatTarget: Int,
    /** At most one group. */
    val groupId: Long?,
    /** Its own schedule; null = inherit the group's. */
    val schedule: Schedule?,
    val sortIndex: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(repeatTarget >= 1) { "repeatTarget must be at least 1" }
    }

    companion object {
        const val MAX_DESCRIPTION = 2000
    }
}

data class BookmarkGroup(
    val id: Long,
    val name: String,
    val description: String?,
    /** Never `once`: that is bookmark-level only. */
    val schedule: Schedule?,
    val sortIndex: Int,
    val createdAt: Instant,
)

data class Schedule(
    /** Non-empty; all seven = every day. */
    val days: Set<DayOfWeek>,
    /** null = any time of day. */
    val window: TimeWindow?,
    /** Bookmark-level only: due every day until done once, then cleared at the next day boundary. */
    val once: Boolean = false,
) {
    init {
        require(days.isNotEmpty()) { "A schedule needs at least one weekday" }
    }

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        fun daily(window: TimeWindow? = null) = Schedule(EVERY_DAY, window)

        fun once(window: TimeWindow? = null) = Schedule(EVERY_DAY, window, once = true)
    }
}

/** `to` earlier than `from` means the window crosses midnight. */
data class TimeWindow(val from: LocalTime, val to: LocalTime) {
    init {
        require(from != to) { "A window needs two different times" }
    }
}

/** Everything needed to open the editor pre-filled. */
data class BookmarkDraft(
    val range: AyahRange,
    val kind: BookmarkKind,
    val label: String? = null,
    val description: String? = null,
    val repeatTarget: Int = 1,
    val groupId: Long? = null,
    val schedule: Schedule? = null,
)
