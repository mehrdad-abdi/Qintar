package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup

import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupSummary
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportOptions
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportResult
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BackupRepository
import javax.inject.Inject

/**
 * Use case for importing backup data.
 */
class ImportDataUseCase @Inject constructor(
    private val backupRepository: BackupRepository
) {
    /**
     * Parse and validate backup JSON without importing.
     */
    suspend fun parseAndValidate(json: String): Result<BackupSummary> {
        return try {
            val backupData = backupRepository.parseBackupJson(json).getOrThrow()
            backupRepository.validateBackup(backupData).getOrThrow()
            Result.success(backupData.getSummary())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import backup data with specified options.
     */
    suspend fun import(json: String, options: ImportOptions): ImportResult {
        return try {
            val backupData = backupRepository.parseBackupJson(json).getOrThrow()
            backupRepository.importData(backupData, options)
        } catch (e: Exception) {
            ImportResult.Error("Failed to parse backup: ${e.message}", e)
        }
    }

    /**
     * Get backup summary from JSON string.
     */
    suspend fun getBackupSummary(json: String): Result<BackupSummary> {
        return try {
            val backupData = backupRepository.parseBackupJson(json).getOrThrow()
            Result.success(backupData.getSummary())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse backup data without validation (for preview).
     */
    suspend fun parseBackup(json: String): Result<BackupData> {
        return backupRepository.parseBackupJson(json)
    }
}
