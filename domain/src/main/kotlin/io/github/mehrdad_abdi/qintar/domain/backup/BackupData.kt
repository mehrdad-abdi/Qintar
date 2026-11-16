package io.github.mehrdad_abdi.qintar.domain.backup

import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import java.time.Instant
import java.time.LocalDate

/**
 * Everything a backup file holds, in memory (§7.10). The JSON codec in `:data` maps the file to and
 * from this; the rules for validating and applying it are in [BackupManager].
 *
 * Ids are the ids of the exporting device. Event contexts refer to [khatms] and [bookmarks] by those
 * ids, and may refer to ids that are in neither list: readings outlive a deleted bookmark or khatm.
 *
 * The file format has no timestamps for bookmarks, groups and tallies; the codec fills them with
 * [exportedAt] when decoding.
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Instant,
    val appVersionName: String? = null,
    val appVersionCode: Int? = null,
    /** Null when the file carries no preferences (a converted v1 file may). */
    val prefs: UserPrefs? = null,
    val khatms: List<Khatm> = emptyList(),
    val groups: List<BookmarkGroup> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val events: List<NewReadEvent> = emptyList(),
    val tallies: List<NewTally> = emptyList(),
) {
    companion object {
        const val FORMAT = "qintar-backup"
        const val CURRENT_VERSION = 2
    }
}

/** What the import preview shows before the user chooses Replace or Merge. */
data class BackupSummary(
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    val events: Int,
    val tallies: Int,
    /** Events plus the sum of tallies: the ayahs this file would account for. */
    val totalAyahs: Int,
    val bookmarks: Int,
    val groups: Int,
    val khatms: Int,
)

/** Why a backup cannot be imported. Any one of these refuses the whole file. */
sealed interface BackupProblem {
    /** Written by a newer app than this one understands. */
    data class UnsupportedVersion(val version: Int) : BackupProblem

    /** Readings dated after today. */
    data class FutureDays(val records: Int, val latest: LocalDate) : BackupProblem

    /** A bookmark names a group the file does not contain. */
    data class UnknownGroup(val bookmarkId: Long, val groupId: Long) : BackupProblem

    /** Two khatms, groups or bookmarks share an id, so references to it are ambiguous. */
    data class DuplicateId(val kind: Kind, val id: Long) : BackupProblem {
        enum class Kind { KHATM, GROUP, BOOKMARK }
    }

    /** A tally whose count is not positive. */
    data class InvalidTally(val day: LocalDate, val count: Int) : BackupProblem
}

data class BackupPreview(val summary: BackupSummary, val problems: List<BackupProblem>) {
    val importable: Boolean get() = problems.isEmpty()
}

enum class ImportMode {
    /** Delete everything on the device, then restore the file with its own ids. */
    REPLACE,

    /** Keep everything on the device and add the file's contents under new ids. */
    MERGE,
}

data class ImportResult(
    val eventsAdded: Int,
    /** Events left out by a merge because the device already had them. */
    val eventsSkipped: Int,
    val talliesAdded: Int,
    val talliesSkipped: Int,
    val bookmarksAdded: Int,
    val groupsAdded: Int,
    val khatmsAdded: Int,
)
