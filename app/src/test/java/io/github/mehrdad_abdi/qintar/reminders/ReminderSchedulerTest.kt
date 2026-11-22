package io.github.mehrdad_abdi.qintar.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.service.ReminderDecision
import io.github.mehrdad_abdi.qintar.domain.testing.FakePrefsStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The alarms behind the reminders and the words they use. */
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("UTC")
    private val now: Instant = LocalDateTime.of(2026, 11, 15, 9, 0).atZone(zone).toInstant()

    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private val alarms get() = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms

    @Test
    fun `no reminder on means no alarm`() = runTest {
        ReminderScheduler(context, FakePrefsStore()).reschedule(now, zone)
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun `an enabled reminder sets one inexact alarm at the next occurrence of its time`() = runTest {
        val prefs = FakePrefsStore(UserPrefs(reminderEnabled = true, reminderTime = LocalTime.of(20, 0)))
        ReminderScheduler(context, prefs).reschedule(now, zone)

        val alarm = alarms.single()
        assertEquals(at(15, 20), alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        // Inexact: no window of zero length, no exact-alarm call.
        assertEquals(0L, alarm.interval)
    }

    @Test
    fun `a time that has passed today is set for tomorrow, and both reminders have their own alarm`() = runTest {
        val prefs = FakePrefsStore(
            UserPrefs(reminderEnabled = true, reminderTime = LocalTime.of(8, 0), streakReminderEnabled = true, streakReminderTime = LocalTime.of(21, 30)),
        )
        ReminderScheduler(context, prefs).reschedule(now, zone)

        assertEquals(setOf(at(16, 8), at(15, 21, 30)), alarms.map { it.triggerAtMs }.toSet())
    }

    @Test
    fun `changing the time replaces the alarm and turning it off cancels it`() = runTest {
        val prefs = FakePrefsStore(UserPrefs(reminderEnabled = true, reminderTime = LocalTime.of(20, 0)))
        val scheduler = ReminderScheduler(context, prefs)
        scheduler.reschedule(now, zone)

        prefs.update { it.copy(reminderTime = LocalTime.of(22, 15)) }
        scheduler.reschedule(now, zone)
        assertEquals(listOf(at(15, 22, 15)), alarms.map { it.triggerAtMs })

        prefs.update { it.copy(reminderEnabled = false) }
        scheduler.reschedule(now, zone)
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun `the notification words follow the decision, the goal and the language`() {
        val english = ReminderNotifier.text(context, ReminderDecision.NothingRead(Rank.NOT_HEEDLESS), UserPrefs())
        assertEquals("Ten ayahs keep you from among the heedless.", english)

        assertEquals(
            "47 ayahs today — 3 more to Dhakir.",
            ReminderNotifier.text(context, ReminderDecision.InProgress(47, 3, Rank.DHAKIR), UserPrefs(latinNumerals = true)),
        )
        assertEquals("4 more ayahs keep your streak today.", ReminderNotifier.text(context, ReminderDecision.StreakAtRisk(6, 4), UserPrefs()))
        assertNull(ReminderNotifier.text(context, ReminderDecision.Skip, UserPrefs()))

        val persian = ReminderNotifier.localized(context, AppLanguage.PERSIAN)
        val text = ReminderNotifier.text(persian, ReminderDecision.InProgress(47, 3, Rank.DHAKIR), UserPrefs(language = AppLanguage.PERSIAN))!!
        assertTrue(text, text.contains("۴۷") && text.contains("۳"))
    }
}
