package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.notification

import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.mehrdad_abdi.quranbookmarks.data.notification.DailyReminderWorker
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ScheduleDailyNotificationUseCase @Inject constructor(
    private val workManager: WorkManager
) {

    operator fun invoke(profile: BookmarkGroup) {
        if (!profile.notificationSettings.enabled) {
            cancelNotification(profile.id)
            return
        }

        val inputData = Data.Builder()
            .putLong(DailyReminderWorker.PROFILE_ID_KEY, profile.id)
            .build()

        val initialDelay = calculateInitialDelay(profile.notificationSettings.time)

        val workRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        val workName = DailyReminderWorker.getWorkName(profile.id)

        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelNotification(profileId: Long) {
        val workName = DailyReminderWorker.getWorkName(profileId)
        workManager.cancelUniqueWork(workName)
    }

    private fun calculateInitialDelay(timeString: String): Long {
        val time = LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time is before now, schedule for tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}