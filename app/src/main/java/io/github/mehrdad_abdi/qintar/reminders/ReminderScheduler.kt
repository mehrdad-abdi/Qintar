package io.github.mehrdad_abdi.qintar.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.service.ReminderKind
import io.github.mehrdad_abdi.qintar.domain.service.ReminderPlanner
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Keeps one inexact alarm per enabled reminder (§7.9): `setAndAllowWhileIdle`, which needs no
 * permission, never the exact-alarm APIs. An alarm is set for the next occurrence only and set again
 * after it fires, after boot, after a time or zone change, after an update and whenever the
 * reminder settings change; setting the same alarm twice replaces it.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PrefsStore,
) {
    private val alarms get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Sets or cancels both alarms from the stored preferences. */
    suspend fun reschedule(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val p = prefs.prefs.first()
        ReminderNotifier.ensureChannels(context)
        apply(ReminderKind.DAILY, p.reminderEnabled, p.reminderTime, p, now, zone)
        apply(ReminderKind.STREAK, p.streakReminderEnabled, p.streakReminderTime, p, now, zone)
    }

    private fun apply(kind: ReminderKind, enabled: Boolean, time: LocalTime, p: UserPrefs, now: Instant, zone: ZoneId) {
        if (!enabled) {
            alarms.cancel(pendingIntent(kind, scheduledAt = 0L))
            return
        }
        val trigger = ReminderPlanner.next(now, zone, time, p.dayStartMinutes)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.at.toEpochMilli(), pendingIntent(kind, trigger.at.toEpochMilli()))
    }

    private fun pendingIntent(kind: ReminderKind, scheduledAt: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_KIND, kind.name)
            .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
        return PendingIntent.getBroadcast(context, kind.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_REMIND = "io.github.mehrdad_abdi.qintar.REMIND"
        const val EXTRA_KIND = "kind"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
    }
}
