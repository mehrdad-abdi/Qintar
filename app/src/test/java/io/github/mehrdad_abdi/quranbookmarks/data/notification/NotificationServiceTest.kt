package io.github.mehrdad_abdi.quranbookmarks.data.notification

import android.app.NotificationManager
import android.content.Context
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NotificationServiceTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var notificationManager: NotificationManager

    private lateinit var notificationService: NotificationService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
        notificationService = NotificationService(context)
    }

    @Test
    fun `showDailyReminder should create and show notification`() {
        // Given
        val profile = BookmarkGroup(
            id = 1L,
            name = "Test Profile",
            description = "Test Description",
            color = 0xFF000000.toInt(),
            notificationSettings = NotificationSettings()
        )

        // When
        notificationService.showDailyReminder(profile)

        // Then
        verify(notificationManager).notify(eq(1001), any())
    }

    @Test
    fun `cancelNotification should cancel notification`() {
        // Given
        val profileId = 2L

        // When
        notificationService.cancelNotification(profileId)

        // Then
        verify(notificationManager).cancel(1002)
    }
}