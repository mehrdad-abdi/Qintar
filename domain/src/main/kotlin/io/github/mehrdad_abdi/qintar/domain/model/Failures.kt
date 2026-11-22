package io.github.mehrdad_abdi.qintar.domain.model

import io.github.mehrdad_abdi.qintar.domain.backup.BackupProblem
import java.time.LocalDate

/**
 * Typed failures of domain operations (§7.3). View models map each subtype to a string resource; the
 * message is for logs only and must never be shown.
 */
sealed class DomainFailure(message: String) : RuntimeException(message) {
    /** A reading can be recorded for today or the past, never the future (§4.2.3). */
    class FutureDay(val day: LocalDate, val today: LocalDate) : DomainFailure("Day $day is after today $today")

    /** "Set count" accepts 0…9999; repeats and tallies must be positive. */
    class CountOutOfRange(val count: Int) : DomainFailure("Count out of range: $count")

    class KhatmNotFound(val id: Long) : DomainFailure("No khatm $id")

    class BookmarkNotFound(val id: Long) : DomainFailure("No bookmark $id")

    class GroupNotFound(val id: Long) : DomainFailure("No group $id")

    /** A field of a bookmark, group or khatm being saved breaks a rule of §4.6–§4.7. */
    class InvalidInput(val field: Field) : DomainFailure("Invalid $field") {
        enum class Field { NAME, DESCRIPTION, REPEAT_TARGET, SCHEDULE, DAILY_PAGES, START_POSITION }
    }

    /** A backup that failed validation; nothing was written (§7.10). */
    class InvalidBackup(val problems: List<BackupProblem>) : DomainFailure("Backup rejected: $problems")
}
