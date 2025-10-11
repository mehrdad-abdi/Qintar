package io.github.mehrdad_abdi.quranbookmarks.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mehrdad_abdi.quranbookmarks.data.backup.BackupSerializer
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupValidationResult
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportOptions
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportResult
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportStrategy
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BackupRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookmarkRepository: BookmarkRepository,
    private val readingActivityRepository: ReadingActivityRepository,
    private val settingsRepository: SettingsRepository,
    private val backupSerializer: BackupSerializer
) : BackupRepository {

    override suspend fun exportAllData(): Result<BackupData> {
        return try {
            val bookmarks = bookmarkRepository.getAllBookmarks().first()
            val readingActivity = readingActivityRepository.getAllActivities()
            val settings = settingsRepository.getSettings().first()

            val backupData = createBackupData(
                bookmarks = bookmarks,
                readingActivity = readingActivity,
                settings = settings
            )

            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportBookmarksOnly(): Result<BackupData> {
        return try {
            val bookmarks = bookmarkRepository.getAllBookmarks().first()
            val settings = settingsRepository.getSettings().first()

            val backupData = createBackupData(
                bookmarks = bookmarks,
                readingActivity = emptyList(),
                settings = settings
            )

            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportReadingActivityOnly(): Result<BackupData> {
        return try {
            val readingActivity = readingActivityRepository.getAllActivities()
            val settings = settingsRepository.getSettings().first()

            val backupData = createBackupData(
                bookmarks = emptyList(),
                readingActivity = readingActivity,
                settings = settings
            )

            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importData(
        backupData: BackupData,
        options: ImportOptions
    ): ImportResult {
        return try {
            // Validate first
            val validationResult = backupData.validate()
            if (validationResult is BackupValidationResult.Error) {
                return ImportResult.Error("Validation failed: ${validationResult.errors.joinToString()}")
            }

            var bookmarksImported = 0
            var readingActivityImported = 0
            var settingsImported = false
            val warnings = mutableListOf<String>()

            when (options.strategy) {
                ImportStrategy.REPLACE_ALL -> {
                    // Clear existing data
                    clearAllData()

                    // Import all data
                    bookmarksImported = importBookmarks(backupData.bookmarks)
                    readingActivityImported = importReadingActivity(backupData.readingActivity)
                    settingsImported = importSettings(backupData.settings)
                }

                ImportStrategy.MERGE -> {
                    // Import bookmarks (merge)
                    if (options.preserveExistingBookmarks) {
                        bookmarksImported = mergeBookmarks(backupData.bookmarks)
                    } else {
                        bookmarksImported = importBookmarks(backupData.bookmarks)
                    }

                    // Import reading activity (merge by date)
                    if (options.mergeReadingActivity) {
                        readingActivityImported = mergeReadingActivity(backupData.readingActivity)
                    }

                    // Import settings if requested
                    if (options.importSettings) {
                        settingsImported = importSettings(backupData.settings)
                    }
                }

                ImportStrategy.BOOKMARKS_ONLY -> {
                    bookmarksImported = importBookmarks(backupData.bookmarks)
                }

                ImportStrategy.READING_ACTIVITY_ONLY -> {
                    readingActivityImported = importReadingActivity(backupData.readingActivity)
                }
            }

            if (warnings.isEmpty()) {
                ImportResult.Success(
                    bookmarksImported = bookmarksImported,
                    readingActivityImported = readingActivityImported,
                    settingsImported = settingsImported
                )
            } else {
                ImportResult.PartialSuccess(
                    bookmarksImported = bookmarksImported,
                    readingActivityImported = readingActivityImported,
                    settingsImported = settingsImported,
                    warnings = warnings
                )
            }
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}", e)
        }
    }

    override suspend fun validateBackup(backupData: BackupData): Result<Unit> {
        return try {
            val validationResult = backupData.validate()
            when (validationResult) {
                is BackupValidationResult.Success -> Result.success(Unit)
                is BackupValidationResult.Error -> Result.failure(
                    IllegalArgumentException("Validation failed: ${validationResult.errors.joinToString()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun parseBackupJson(json: String): Result<BackupData> {
        return try {
            val backupData = backupSerializer.deserialize(json)
            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun serializeBackup(backupData: BackupData): Result<String> {
        return try {
            val json = backupSerializer.serialize(backupData)
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Private helper methods

    private fun createBackupData(
        bookmarks: List<Bookmark>,
        readingActivity: List<ReadingActivity>,
        settings: AppSettings
    ): BackupData {
        val metadata = BackupMetadata(
            version = BackupData.CURRENT_VERSION,
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            appVersion = getAppVersion(),
            deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        )

        return BackupData(
            metadata = metadata,
            bookmarks = bookmarks,
            readingActivity = readingActivity,
            settings = settings
        )
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private suspend fun clearAllData() {
        val allBookmarks = bookmarkRepository.getAllBookmarks().first()
        allBookmarks.forEach { bookmarkRepository.deleteBookmark(it) }

        readingActivityRepository.deleteAllActivities()
    }

    private suspend fun importBookmarks(bookmarks: List<Bookmark>): Int {
        bookmarks.forEach { bookmark ->
            // Reset ID to let database assign new ones
            val newBookmark = bookmark.copy(id = 0)
            bookmarkRepository.insertBookmark(newBookmark)
        }
        return bookmarks.size
    }

    private suspend fun mergeBookmarks(bookmarks: List<Bookmark>): Int {
        var imported = 0
        bookmarks.forEach { bookmark ->
            // Always insert as new (reset ID)
            val newBookmark = bookmark.copy(id = 0)
            bookmarkRepository.insertBookmark(newBookmark)
            imported++
        }
        return imported
    }

    private suspend fun importReadingActivity(activities: List<ReadingActivity>): Int {
        activities.forEach { activity ->
            readingActivityRepository.saveActivity(activity)
        }
        return activities.size
    }

    private suspend fun mergeReadingActivity(activities: List<ReadingActivity>): Int {
        var imported = 0
        activities.forEach { newActivity ->
            val existing = readingActivityRepository.getActivityByDate(newActivity.date)

            if (existing != null) {
                // Merge tracked ayahs
                val mergedIds = existing.trackedAyahIds + newActivity.trackedAyahIds
                val merged = ReadingActivity.create(
                    date = newActivity.date,
                    totalAyahsRead = mergedIds.size,
                    trackedAyahIds = mergedIds
                )
                readingActivityRepository.saveActivity(merged)
            } else {
                readingActivityRepository.saveActivity(newActivity)
            }
            imported++
        }
        return imported
    }

    private suspend fun importSettings(settings: AppSettings): Boolean {
        return try {
            settingsRepository.updateReciter(settings.reciterEdition, settings.reciterBitrate)
            settingsRepository.updateNotificationEnabled(settings.notificationSettings.enabled)
            settingsRepository.updateNotificationTime(settings.notificationSettings.time)
            settingsRepository.updateTheme(settings.theme)
            settingsRepository.updatePrimaryColor(settings.primaryColorHex)
            settingsRepository.updatePlaybackSpeed(settings.playbackSpeed)
            true
        } catch (e: Exception) {
            false
        }
    }
}
