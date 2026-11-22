package io.github.mehrdad_abdi.qintar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyFileStore
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigration
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.playback.AudioStorage
import io.github.mehrdad_abdi.qintar.playback.PlaybackService
import io.github.mehrdad_abdi.qintar.playback.Reciters
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
    /** Bytes of stored recitation; null until measured. */
    val audioBytes: Long? = null,
    /** v1's own database is still on the device (it can be removed once its data was carried over). */
    val legacyDataPresent: Boolean = false,
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
    private val audio: AudioStorage,
    private val legacyFiles: LegacyFileStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.prefs.collect { p -> _state.update { it.copy(loading = false, prefs = p) } }
        }
        refreshAudioSize()
        refreshLegacyData()
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

    // ---- Audio (§7.8)
    /** A reciter of the curated list; the quality moves to the nearest one that reciter has. */
    fun setReciter(id: String) = edit {
        val reciter = Reciters.of(id)
        it.copy(reciterId = reciter.id, audioQuality = reciter.bitrateFor(it.audioQuality))
    }

    fun setAudioQuality(bitrate: Int) = edit { it.copy(audioQuality = Reciters.of(it.reciterId).bitrateFor(bitrate)) }
    fun setPlaybackSpeed(speed: Float) = edit { it.copy(playbackSpeed = speed.coerceIn(PlaybackService.MIN_SPEED, PlaybackService.MAX_SPEED)) }
    fun setCountListened(value: Boolean) = edit { it.copy(countListened = value) }

    fun setStopBeforeSajda(value: Boolean) = edit { it.copy(stopBeforeSajda = value) }
    fun setWifiOnlyDownloads(value: Boolean) = edit { it.copy(wifiOnlyDownloads = value) }

    fun clearAudio() {
        viewModelScope.launch {
            runCatching { audio.clear() }
            refreshAudioSize()
        }
    }

    private fun refreshAudioSize() {
        viewModelScope.launch {
            val bytes = runCatching { audio.sizeBytes() }.getOrNull()
            _state.update { it.copy(audioBytes = bytes) }
        }
    }

    // ---- Data
    /** Settings → Data → "Remove data of the previous version", after the user confirmed. */
    fun removeLegacyData() {
        val state = _state.value.prefs.legacyMigrationState
        if (state != LegacyMigrationState.MIGRATED && state != LegacyMigrationState.ACKNOWLEDGED) return
        viewModelScope.launch {
            runCatching { legacyFiles.deleteUserData() }
            refreshLegacyData()
        }
    }

    private fun refreshLegacyData() {
        viewModelScope.launch {
            val present = runCatching { legacyFiles.userDataPresent() }.getOrDefault(false)
            _state.update { it.copy(legacyDataPresent = present) }
        }
    }

    fun retryMigration(khatmName: String) {
        if (_state.value.retrying) return
        viewModelScope.launch {
            _state.update { it.copy(retrying = true) }
            runCatching { migration.migrate(khatmName, retryFailed = true) }.onSuccess(reports::record)
            _state.update { it.copy(retrying = false) }
        }
    }
}
