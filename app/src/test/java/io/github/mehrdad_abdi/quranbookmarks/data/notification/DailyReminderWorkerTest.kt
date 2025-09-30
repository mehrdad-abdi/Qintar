package io.github.mehrdad_abdi.quranbookmarks.data.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class DailyReminderWorkerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var workerParams: WorkerParameters

    @Mock
    private lateinit var bookmarkRepository: BookmarkRepository

    @Mock
    private lateinit var notificationService: NotificationService

    private lateinit var worker: DailyReminderWorker

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        worker = DailyReminderWorker(context, workerParams, bookmarkRepository, notificationService)
    }

    @Test
    fun `doWork should show notification when profile exists and notifications enabled`() = runTest {
        // Given
        val profileId = 1L
        val inputData = Data.Builder()
            .putLong(DailyReminderWorker.PROFILE_ID_KEY, profileId)
            .build()
        whenever(workerParams.inputData).thenReturn(inputData)

        val profile = BookmarkGroup(
            id = profileId,
            name = "Test Profile",
            description = "Test Description",
            color = 0xFF000000.toInt(),
            notificationSettings = NotificationSettings(enabled = true)
        )
        whenever(bookmarkRepository.getGroupById(profileId)).thenReturn(profile)

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verify(notificationService).showDailyReminder(profile)
    }

    @Test
    fun `doWork should not show notification when profile has notifications disabled`() = runTest {
        // Given
        val profileId = 1L
        val inputData = Data.Builder()
            .putLong(DailyReminderWorker.PROFILE_ID_KEY, profileId)
            .build()
        whenever(workerParams.inputData).thenReturn(inputData)

        val profile = BookmarkGroup(
            id = profileId,
            name = "Test Profile",
            description = "Test Description",
            color = 0xFF000000.toInt(),
            notificationSettings = NotificationSettings(enabled = false)
        )
        whenever(bookmarkRepository.getGroupById(profileId)).thenReturn(profile)

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verify(notificationService, never()).showDailyReminder(any())
    }

    @Test
    fun `doWork should not show notification when profile does not exist`() = runTest {
        // Given
        val profileId = 1L
        val inputData = Data.Builder()
            .putLong(DailyReminderWorker.PROFILE_ID_KEY, profileId)
            .build()
        whenever(workerParams.inputData).thenReturn(inputData)
        whenever(bookmarkRepository.getGroupById(profileId)).thenReturn(null)

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verify(notificationService, never()).showDailyReminder(any())
    }

    @Test
    fun `doWork should return failure when profileId is invalid`() = runTest {
        // Given
        val inputData = Data.Builder()
            .putLong(DailyReminderWorker.PROFILE_ID_KEY, -1L)
            .build()
        whenever(workerParams.inputData).thenReturn(inputData)

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.failure(), result)
        verify(bookmarkRepository, never()).getGroupById(any())
        verify(notificationService, never()).showDailyReminder(any())
    }

    @Test
    fun `getWorkName should return correct work name`() {
        val profileId = 123L
        val workName = DailyReminderWorker.getWorkName(profileId)
        assertEquals("daily_reminder_123", workName)
    }
}