package io.github.mehrdad_abdi.quranbookmarks.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository

@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkRepository: BookmarkRepository,
    private val notificationService: NotificationService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val PROFILE_ID_KEY = "profile_id"
        const val WORK_NAME_PREFIX = "daily_reminder_"

        fun getWorkName(profileId: Long): String = "$WORK_NAME_PREFIX$profileId"
    }

    override suspend fun doWork(): Result {
        return try {
            val profileId = inputData.getLong(PROFILE_ID_KEY, -1L)
            if (profileId == -1L) {
                return Result.failure()
            }

            val profile = bookmarkRepository.getGroupById(profileId)
            if (profile != null && profile.notificationSettings.enabled) {
                notificationService.showDailyReminder(profile)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}