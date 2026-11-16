package io.github.mehrdad_abdi.qintar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigration
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val prefs: UserPrefs = UserPrefs(),
    /** True while "Try again" of the v1 migration runs. */
    val retrying: Boolean = false,
)

/**
 * Settings (§5.9): every control writes straight to the preferences and the screen follows the
 * stored value, so what is shown is always what is saved.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PrefsStore,
    private val migration: LegacyMigration,
    private val reports: MigrationReports,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.prefs.collect { p -> _state.update { it.copy(loading = false, prefs = p) } }
        }
    }

    private fun edit(transform: (UserPrefs) -> UserPrefs) {
        viewModelScope.launch { prefs.update(transform) }
    }

    // ---- Appearance
    fun setLanguage(value: AppLanguage) = edit { it.copy(language = value) }
    fun setTheme(value: ThemeMode) = edit { it.copy(theme = value) }
    fun setPureBlack(value: Boolean) = edit { it.copy(pureBlack = value) }
    fun setLatinNumerals(value: Boolean) = edit { it.copy(latinNumerals = value) }

    // ---- Reading
    fun setFontSp(value: Int) = edit { it.copy(quranFontSp = value.coerceIn(UserPrefs.MIN_FONT_SP, UserPrefs.MAX_FONT_SP)) }
    fun setBreakAtPauseMarks(value: Boolean) = edit { it.copy(breakAtPauseMarks = value) }
    fun setKeepScreenOn(value: Boolean) = edit { it.copy(keepScreenOn = value) }

    // ---- Goal and days
    /** @param rank null = no goal. [Rank.NONE] is not a goal either. */
    fun setGoal(rank: Rank?) = edit { it.copy(goalRank = rank?.takeIf { r -> r != Rank.NONE }) }
    fun setDayStartMinutes(minutes: Int) = edit { it.copy(dayStartMinutes = minutes.coerceIn(0, UserPrefs.MAX_DAY_START_MINUTES)) }
    fun setWeekStart(value: WeekStart) = edit { it.copy(weekStart = value) }

    // ---- Reminders. The screen asks for the notification permission before it enables one.
    fun setReminderEnabled(value: Boolean) = edit { it.copy(reminderEnabled = value) }
    fun setReminderTime(value: LocalTime) = edit { it.copy(reminderTime = value) }
    fun setStreakReminderEnabled(value: Boolean) = edit { it.copy(streakReminderEnabled = value) }
    fun setStreakReminderTime(value: LocalTime) = edit { it.copy(streakReminderTime = value) }

    // ---- AUDIO SECTION (not built here): reciter, quality, speed, count-listened, Wi-Fi only and
    // ---- storage live in UserPrefs already; the audio feature adds its setters and its section.

    // ---- Data
    fun retryMigration(khatmName: String) {
        if (_state.value.retrying) return
        viewModelScope.launch {
            _state.update { it.copy(retrying = true) }
            runCatching { migration.migrate(khatmName, retryFailed = true) }.onSuccess(reports::record)
            _state.update { it.copy(retrying = false) }
        }
    }
}
