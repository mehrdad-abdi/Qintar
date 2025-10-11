package io.github.mehrdad_abdi.quranbookmarks.domain.model.backup

import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity

/**
 * Complete backup data structure for export/import.
 */
data class BackupData(
    val metadata: BackupMetadata,
    val bookmarks: List<Bookmark>,
    val readingActivity: List<ReadingActivity>,
    val settings: AppSettings
) {
    companion object {
        const val CURRENT_VERSION = "1.0"
    }

    /**
     * Validate backup data integrity.
     */
    fun validate(): BackupValidationResult {
        val errors = mutableListOf<String>()

        // Check version compatibility
        if (metadata.version != CURRENT_VERSION) {
            errors.add("Unsupported backup version: ${metadata.version}")
        }

        // Validate bookmarks
        bookmarks.forEachIndexed { index, bookmark ->
            if (bookmark.startSurah !in 1..114) {
                errors.add("Invalid surah number at bookmark $index: ${bookmark.startSurah}")
            }
            if (bookmark.startAyah < 1) {
                errors.add("Invalid ayah number at bookmark $index: ${bookmark.startAyah}")
            }
        }

        // Validate reading activity
        readingActivity.forEachIndexed { index, activity ->
            if (activity.totalAyahsRead < 0) {
                errors.add("Invalid ayah count at activity $index: ${activity.totalAyahsRead}")
            }
            if (activity.trackedAyahIds.size != activity.totalAyahsRead) {
                errors.add("Mismatch between total count and tracked IDs at activity $index")
            }
        }

        return if (errors.isEmpty()) {
            BackupValidationResult.Success
        } else {
            BackupValidationResult.Error(errors)
        }
    }

    /**
     * Get summary of backup contents.
     */
    fun getSummary(): BackupSummary {
        return BackupSummary(
            bookmarkCount = bookmarks.size,
            readingActivityCount = readingActivity.size,
            totalAyahsRead = readingActivity.sumOf { it.totalAyahsRead },
            timestamp = metadata.timestamp,
            appVersion = metadata.appVersion
        )
    }
}

/**
 * Metadata for backup file.
 */
data class BackupMetadata(
    val version: String,
    val timestamp: String, // ISO 8601 format
    val appVersion: String,
    val deviceInfo: String = ""
)

/**
 * Summary of backup contents for display.
 */
data class BackupSummary(
    val bookmarkCount: Int,
    val readingActivityCount: Int,
    val totalAyahsRead: Int,
    val timestamp: String,
    val appVersion: String
)

/**
 * Result of backup validation.
 */
sealed class BackupValidationResult {
    object Success : BackupValidationResult()
    data class Error(val errors: List<String>) : BackupValidationResult()
}
