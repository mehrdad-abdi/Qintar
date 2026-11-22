package io.github.mehrdad_abdi.qintar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.mehrdad_abdi.qintar.di.ApplicationScope
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.ReminderDecision
import io.github.mehrdad_abdi.qintar.domain.service.ReminderKind
import io.github.mehrdad_abdi.qintar.domain.service.ReminderPlanner
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the receivers take from the graph (a receiver is created by the system, not by Hilt). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun prefs(): PrefsStore

    fun totals(): DayTotals

    fun scheduler(): ReminderScheduler

    @ApplicationScope
    fun scope(): CoroutineScope
}

/** An alarm went off: decide from today's total whether to speak, then set the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        val prefs = graph.prefs()
        val totals = graph.totals()
        val scheduler = graph.scheduler()
        val scope = graph.scope()
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val kind = runCatching { ReminderKind.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_KIND).orEmpty()) }.getOrNull() ?: return
        val scheduledAt = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        val pending = goAsync()
        scope.launch {
            try {
                val p = prefs.prefs.first()
                val now = Instant.now()
                val enabled = if (kind == ReminderKind.DAILY) p.reminderEnabled else p.streakReminderEnabled
                if (enabled) {
                    // Judged against the reading day the alarm was set for, not the day it happens to be delivered on.
                    val day = ReminderPlanner.readingDayAt(scheduledAt ?: now, ZoneId.systemDefault(), p.dayStartMinutes)
                    val decision = ReminderPlanner.decide(kind, totals.total(day), p.goalRank, now, scheduledAt)
                    if (decision != ReminderDecision.Skip) {
                        val localized = ReminderNotifier.localized(context.applicationContext, p.language)
                        ReminderNotifier.text(localized, decision, p)?.let { ReminderNotifier.post(localized, kind, it) }
                    }
                }
                scheduler.reschedule(now)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Boot, an app update, or a change of clock or zone: alarms are gone or wrong, so set them again. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        val scheduler = graph.scheduler()
        val scope = graph.scope()
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
