package io.github.mehrdad_abdi.quranbookmarks.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.mehrdad_abdi.quranbookmarks.MainActivity
import io.github.mehrdad_abdi.quranbookmarks.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    private val context: Context
) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "daily_reading_reminders"
        const val CHANNEL_NAME = "Daily Reading Reminders"
        const val CHANNEL_DESCRIPTION = "Notifications to remind you to read your bookmarks"

        private const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * @deprecated Will be reimplemented for global notifications
     */
    @Deprecated("Profiles removed - will be reimplemented for global notifications")
    fun showDailyReminder() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to read")
            .setContentText("Time to read your bookmarks")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID_BASE)
    }
}