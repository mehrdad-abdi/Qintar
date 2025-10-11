package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportOptions
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportResult

/**
 * Repository for backup and restore operations.
 */
interface BackupRepository {

    /**
     * Export all app data as backup.
     */
    suspend fun exportAllData(): Result<BackupData>

    /**
     * Export only bookmarks.
     */
    suspend fun exportBookmarksOnly(): Result<BackupData>

    /**
     * Export only reading activity.
     */
    suspend fun exportReadingActivityOnly(): Result<BackupData>

    /**
     * Import backup data with specified options.
     */
    suspend fun importData(backupData: BackupData, options: ImportOptions): ImportResult

    /**
     * Validate backup data without importing.
     */
    suspend fun validateBackup(backupData: BackupData): Result<Unit>

    /**
     * Parse JSON string to BackupData.
     */
    suspend fun parseBackupJson(json: String): Result<BackupData>

    /**
     * Serialize BackupData to JSON string.
     */
    suspend fun serializeBackup(backupData: BackupData): Result<String>
}
