package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.notification

import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.github.mehrdad_abdi.quranbookmarks.data.notification.DailyReminderWorker
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSchedule
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.never

class ScheduleDailyNotificationUseCaseTest {

    @Mock
    private lateinit var workManager: WorkManager

    private lateinit var useCase: ScheduleDailyNotificationUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        useCase = ScheduleDailyNotificationUseCase(workManager)
    }

    @Test
    fun `invoke with enabled notification should schedule work`() {
        // Given
        val profile = BookmarkGroup(
            id = 1L,
            name = "Test Profile",
            description = "Test Description",
            color = 0xFF000000.toInt(),
            notificationSettings = NotificationSettings(
                enabled = true,
                schedule = NotificationSchedule.DAILY,
                time = "08:00"
            )
        )

        // When
        useCase(profile)

        // Then
        verify(workManager).enqueueUniquePeriodicWork(
            eq("daily_reminder_1"),
            eq(ExistingPeriodicWorkPolicy.REPLACE),
            any<PeriodicWorkRequest>()
        )
    }

    @Test
    fun `invoke with disabled notification should cancel work`() {
        // Given
        val profile = BookmarkGroup(
            id = 1L,
            name = "Test Profile",
            description = "Test Description",
            color = 0xFF000000.toInt(),
            notificationSettings = NotificationSettings(
                enabled = false,
                schedule = NotificationSchedule.DAILY,
                time = "08:00"
            )
        )

        // When
        useCase(profile)

        // Then
        verify(workManager).cancelUniqueWork("daily_reminder_1")
        verify(workManager, never()).enqueueUniquePeriodicWork(
            any(),
            any(),
            any<PeriodicWorkRequest>()
        )
    }

    @Test
    fun `cancelNotification should cancel work for profile`() {
        // Given
        val profileId = 5L

        // When
        useCase.cancelNotification(profileId)

        // Then
        verify(workManager).cancelUniqueWork("daily_reminder_5")
    }
}