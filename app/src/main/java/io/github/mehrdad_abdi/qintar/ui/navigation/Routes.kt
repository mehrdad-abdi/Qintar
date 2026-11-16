package io.github.mehrdad_abdi.qintar.ui.navigation

import kotlinx.serialization.Serializable

/*
 * Type-safe routes (Navigation Compose + kotlinx.serialization). Routes carry ids and positions
 * only, never objects: each screen loads what it needs from its repositories.
 *
 * Ids are Long (Room row ids). Ayah ids are the global 1..6236 numbering of DESIGN.md §7.4.
 */

// ---- Bottom navigation (§5.1) ------------------------------------------------------------

@Serializable
data object TodayRoute

@Serializable
data object ReadRoute

@Serializable
data object BookmarksRoute

@Serializable
data object InsightsRoute

// ---- Full-screen destinations ------------------------------------------------------------

/** How the reader was entered (§5.4). Decides the counting context and the scope. */
@Serializable
enum class ReaderEntry {
    /** Context Khatm([ReaderRoute.id]); scope 1..6236; opens at the khatm's resume position. */
    KHATM,

    /** Context Bookmark([ReaderRoute.id]); scope is the bookmark's range. */
    BOOKMARK,

    /** One segment per bookmark of group [ReaderRoute.id], each counted under its own bookmark. */
    GROUP,

    /** Context Browse; scope 1..6236; opens at [ReaderRoute.startAyahId]. */
    BROWSE,

    /** Context Random; scope [ReaderRoute.scopeFirstAyahId]..[ReaderRoute.scopeLastAyahId]. */
    RANDOM,
}

/**
 * The one reader screen. The reader feature turns this into the `ReaderArgs(segments, startAt)`
 * of §5.4.
 *
 * @param id khatm, bookmark or group id for those entries; unused otherwise.
 * @param startAyahId ayah to open at; [UNSET] means the entry's default of the §5.4 table
 *   (resume position, first ayah below target, first bookmark not done, start of scope).
 * @param scopeFirstAyahId first ayah of an explicit scope (RANDOM: the ayah or the page's first).
 * @param scopeLastAyahId last ayah of that scope.
 */
@Serializable
data class ReaderRoute(
    val entry: ReaderEntry,
    val id: Long = UNSET_ID,
    val startAyahId: Int = UNSET,
    val scopeFirstAyahId: Int = UNSET,
    val scopeLastAyahId: Int = UNSET,
) {
    companion object {
        const val UNSET = 0
        const val UNSET_ID = 0L

        fun khatm(khatmId: Long, startAyahId: Int = UNSET) = ReaderRoute(ReaderEntry.KHATM, khatmId, startAyahId)
        fun bookmark(bookmarkId: Long, startAyahId: Int = UNSET) = ReaderRoute(ReaderEntry.BOOKMARK, bookmarkId, startAyahId)
        fun group(groupId: Long) = ReaderRoute(ReaderEntry.GROUP, groupId)
        fun browse(startAyahId: Int) = ReaderRoute(ReaderEntry.BROWSE, startAyahId = startAyahId)
        fun random(firstAyahId: Int, lastAyahId: Int = firstAyahId) = ReaderRoute(
            ReaderEntry.RANDOM,
            startAyahId = firstAyahId,
            scopeFirstAyahId = firstAyahId,
            scopeLastAyahId = lastAyahId,
        )
    }
}

@Serializable
data class KhatmDetailRoute(val khatmId: Long)

/** Every khatm: the ones in progress and the finished ones, with "New khatm". */
@Serializable
data object KhatmListRoute

/** The form that starts a khatm. */
@Serializable
data object NewKhatmRoute

/**
 * Add or edit a bookmark. With [bookmarkId] == [NEW] the editor starts from a draft: an optional
 * pre-filled passage ([draftFirstAyahId]..[draftLastAyahId], e.g. from the ayah sheet) and an
 * optional target group.
 */
@Serializable
data class BookmarkEditorRoute(
    val bookmarkId: Long = NEW,
    val draftFirstAyahId: Int = 0,
    val draftLastAyahId: Int = 0,
    val draftGroupId: Long = NEW,
) {
    companion object {
        const val NEW = 0L
    }
}

/** Add ([groupId] == [NEW]) or edit a bookmark group. */
@Serializable
data class GroupEditorRoute(val groupId: Long = NEW) {
    companion object {
        const val NEW = 0L
    }
}

/** One day of the calendar. [isoDate] is `yyyy-MM-dd` (storage is always ISO, §6.6). */
@Serializable
data class DayDetailRoute(val isoDate: String)

@Serializable
data object SettingsRoute

@Serializable
data object OnboardingRoute
