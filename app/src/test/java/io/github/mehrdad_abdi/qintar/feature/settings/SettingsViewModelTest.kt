package io.github.mehrdad_abdi.qintar.feature.settings

import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.FakeLegacyMigration
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.settingsViewModel
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings (§5.9): each control is saved, and what the screen observes is what is saved. */
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()
    private val vm by lazy { app.settingsViewModel() }

    @Test
    fun `the state follows the stored preferences from the start`() {
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(UserPrefs(), s.prefs)
    }

    @Test
    fun `appearance settings persist and change the observed state`() {
        vm.setLanguage(AppLanguage.PERSIAN)
        vm.setTheme(ThemeMode.DARK)
        vm.setPureBlack(true)
        vm.setLatinNumerals(true)

        for (prefs in listOf(vm.state.value.prefs, app.prefs.current)) {
            assertEquals(AppLanguage.PERSIAN, prefs.language)
            assertEquals(ThemeMode.DARK, prefs.theme)
            assertTrue(prefs.pureBlack)
            assertTrue(prefs.latinNumerals)
        }
    }

    @Test
    fun `reading settings persist and the font size stays inside its range`() {
        vm.setFontSp(36)
        vm.setBreakAtPauseMarks(false)
        vm.setKeepScreenOn(false)

        assertEquals(36, vm.state.value.prefs.quranFontSp)
        assertFalse(app.prefs.current.breakAtPauseMarks)
        assertFalse(app.prefs.current.keepScreenOn)

        vm.setFontSp(500)
        assertEquals(UserPrefs.MAX_FONT_SP, app.prefs.current.quranFontSp)
        vm.setFontSp(1)
        assertEquals(UserPrefs.MIN_FONT_SP, vm.state.value.prefs.quranFontSp)
    }

    @Test
    fun `a goal can be set, changed and cleared`() {
        assertNull(vm.state.value.prefs.goalRank)
        vm.setGoal(Rank.DHAKIR)
        assertEquals(Rank.DHAKIR, vm.state.value.prefs.goalRank)
        vm.setGoal(Rank.QINTAR)
        assertEquals(Rank.QINTAR, app.prefs.current.goalRank)
        vm.setGoal(null)
        assertNull(vm.state.value.prefs.goalRank)
        // "NONE" is the absence of a rank, never a goal.
        vm.setGoal(Rank.NONE)
        assertNull(app.prefs.current.goalRank)
    }

    @Test
    fun `day start and week start persist, and the day start stays within twelve hours`() {
        vm.setDayStartMinutes(3 * 60 + 30)
        vm.setWeekStart(WeekStart.MONDAY)
        assertEquals(210, vm.state.value.prefs.dayStartMinutes)
        assertEquals(WeekStart.MONDAY, app.prefs.current.weekStart)

        vm.setDayStartMinutes(23 * 60)
        assertEquals(UserPrefs.MAX_DAY_START_MINUTES, app.prefs.current.dayStartMinutes)
        vm.setDayStartMinutes(-5)
        assertEquals(0, app.prefs.current.dayStartMinutes)
    }

    @Test
    fun `reminders persist with their times`() {
        vm.setReminderEnabled(true)
        vm.setReminderTime(LocalTime.of(6, 45))
        vm.setStreakReminderEnabled(true)
        vm.setStreakReminderTime(LocalTime.of(22, 15))

        val p = vm.state.value.prefs
        assertTrue(p.reminderEnabled)
        assertEquals(LocalTime.of(6, 45), p.reminderTime)
        assertTrue(app.prefs.current.streakReminderEnabled)
        assertEquals(LocalTime.of(22, 15), app.prefs.current.streakReminderTime)

        vm.setReminderEnabled(false)
        assertFalse(vm.state.value.prefs.reminderEnabled)
        assertTrue("the other reminder is untouched", vm.state.value.prefs.streakReminderEnabled)
    }

    @Test
    fun `changing one setting leaves the others as they were`() {
        vm.setTheme(ThemeMode.LIGHT)
        assertEquals(UserPrefs().copy(theme = ThemeMode.LIGHT), app.prefs.current)
    }

    @Test
    fun `retrying a failed migration from settings records the report and clears the failure`() {
        runBlocking { app.prefs.update { it.copy(legacyMigrationState = LegacyMigrationState.FAILED) } }
        val migration = FakeLegacyMigration(app.prefs)
        val reports = MigrationReports()
        val withMigration = app.settingsViewModel(migration, reports)

        withMigration.retryMigration("Khatm")

        assertEquals(1, migration.retryCalls)
        assertEquals(LegacyMigrationState.MIGRATED, withMigration.state.value.prefs.legacyMigrationState)
        assertEquals(migration.report, reports.last.value)
        assertFalse(withMigration.state.value.retrying)
    }
}
