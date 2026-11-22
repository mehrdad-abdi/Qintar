package io.github.mehrdad_abdi.qintar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.Lazy
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyFileStore
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigrator
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.feature.settings.MigrationReports
import io.github.mehrdad_abdi.qintar.reminders.ReminderScheduler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import io.github.mehrdad_abdi.qintar.di.ApplicationScope
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.time.SystemDayClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The work done once per process, none of it on the main thread:
 *  - load the Quran index, so that the first screen does not pay for it;
 *  - carry over v1 data (a no-op after the first run), and remove v1's caches once that is acknowledged;
 *  - keep the reminder alarms in step with the settings;
 *  - clear "once" schedules of earlier days now and at every day boundary.
 */
@Singleton
class AppStartup @Inject constructor(
    private val index: Lazy<QuranIndex>,
    private val migrator: LegacyMigrator,
    private val legacyFiles: LegacyFileStore,
    private val availability: Availability,
    private val clock: SystemDayClock,
    private val prefs: PrefsStore,
    private val reminders: ReminderScheduler,
    private val reports: MigrationReports,
    private val diagnostics: io.github.mehrdad_abdi.qintar.diagnostics.Diagnostics,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** @param migratedKhatmName the name given to the khatm that v1's reading position becomes. */
    fun start(migratedKhatmName: String): Job = scope.launch(io) {
        // The alarms follow the reminder settings: set now, and again whenever one of them changes.
        launch {
            prefs.prefs
                .map { listOf(it.reminderEnabled, it.reminderTime, it.streakReminderEnabled, it.streakReminderTime, it.dayStartMinutes) }
                .distinctUntilChanged()
                .collect { values ->
                    diagnostics.log("reminders", "reschedule for $values")
                    runCatching { reminders.reschedule() }.onFailure { diagnostics.log("reminders", "reschedule failed: ${it.javaClass.simpleName}") }
                }
        }
        // The canary records which settings change (names and values; settings hold no personal text).
        launch {
            var previous: Map<String, String>? = null
            prefs.prefs.collect { p ->
                val now = settingsOf(p)
                previous?.let { before ->
                    val changed = now.filter { (k, v) -> before[k] != v }
                    if (changed.isNotEmpty()) diagnostics.log("settings", changed.entries.joinToString { "${it.key}=${it.value}" })
                }
                previous = now
            }
        }
        // The migration goes first: the first screen waits for it, and it loads the index itself if it needs it.
        runCatching { migrator.migrate(migratedKhatmName) }.onSuccess(reports::record)
        index.get()
        // Once the carried-over data has been seen, v1's regenerable files go (§7.11). Its user
        // database stays until the user removes it in Settings.
        launch {
            prefs.prefs.map { it.legacyMigrationState == LegacyMigrationState.ACKNOWLEDGED }
                .distinctUntilChanged()
                .collect { acknowledged -> if (acknowledged) runCatching { legacyFiles.deleteRegenerable() } }
        }
        // The first value is today, so this runs at start and then once per new reading day.
        clock.todayFlow.collect { runCatching { availability.rollOverOnce() } }
    }

    /** The system changed its time, date or zone: the reading day may have changed with it (§4.3). */
    fun registerClockChanges(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        context.registerReceiver(ClockChangeReceiver(clock), filter)
    }
}

/** Registered at run time: these are protected system broadcasts and need no export flag. */
class ClockChangeReceiver(private val clock: SystemDayClock) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> clock.refresh()
        }
    }
}

/** A settings snapshot as name → value, for the canary's "settings" events. */
internal fun settingsOf(p: io.github.mehrdad_abdi.qintar.domain.model.UserPrefs): Map<String, String> =
    p.toString().removePrefix("UserPrefs(").removeSuffix(")").split(", ")
        .mapNotNull { part -> part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap()
