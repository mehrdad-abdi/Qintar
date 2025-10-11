package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup

import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BackupRepository
import javax.inject.Inject

/**
 * Use case for exporting app data to backup format.
 */
class ExportDataUseCase @Inject constructor(
    private val backupRepository: BackupRepository
) {
    /**
     * Export all data (bookmarks, reading activity, settings).
     */
    suspend fun exportAll(): Result<String> {
        return try {
            val backupData = backupRepository.exportAllData().getOrThrow()
            backupRepository.serializeBackup(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export only bookmarks.
     */
    suspend fun exportBookmarks(): Result<String> {
        return try {
            val backupData = backupRepository.exportBookmarksOnly().getOrThrow()
            backupRepository.serializeBackup(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export only reading activity.
     */
    suspend fun exportReadingActivity(): Result<String> {
        return try {
            val backupData = backupRepository.exportReadingActivityOnly().getOrThrow()
            backupRepository.serializeBackup(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get backup data without serialization (for preview).
     */
    suspend fun getBackupData(): Result<BackupData> {
        return backupRepository.exportAllData()
    }
}
