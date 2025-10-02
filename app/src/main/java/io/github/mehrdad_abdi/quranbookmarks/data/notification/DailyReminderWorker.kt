package io.github.mehrdad_abdi.quranbookmarks.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository

/**
 * @deprecated Daily reminders will be re-implemented as global app feature
 * Temporarily disabled during profile removal refactoring
 */
@Deprecated("Will be reimplemented as global notification")
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkRepository: BookmarkRepository,
    private val notificationService: NotificationService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME_PREFIX = "daily_reminder_"

        fun getWorkName(): String = "${WORK_NAME_PREFIX}global"
    }

    override suspend fun doWork(): Result {
        return try {
            // TODO: Implement global daily reminder
            // For now, just show a generic reminder if there are bookmarks
            val bookmarksCount = bookmarkRepository.getBookmarkCount()
            if (bookmarksCount > 0) {
                // notificationService.showDailyReminder() // TODO: Update signature
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}