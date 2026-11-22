package io.github.mehrdad_abdi.qintar.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import java.io.File
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [UserPrefs] over a Preferences DataStore, one key per field.
 *
 * Reading never fails: a key that is missing, holds a value of another type, or holds a value the
 * model refuses (an unknown enum name, a font size out of range) reads as that field's default, and an
 * unreadable file reads as all defaults. Writing stores every field, so one successful [update] also
 * repairs whatever was wrong.
 */
class DataStorePrefsStore(private val dataStore: DataStore<Preferences>) : PrefsStore {

    override val prefs: Flow<UserPrefs> = dataStore.data
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map(::decode)
        .distinctUntilChanged()

    override suspend fun update(transform: (UserPrefs) -> UserPrefs): UserPrefs {
        val written = dataStore.updateData { stored ->
            stored.toMutablePreferences().also { encode(transform(decode(stored)), it) }
        }
        return decode(written)
    }

    companion object {
        const val FILE_NAME = "user_prefs"

        /** The production DataStore: `files/datastore/user_prefs.preferences_pb`, reset if the file is corrupt. */
        fun createDataStore(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): DataStore<Preferences> = createDataStore(scope) { context.applicationContext.preferencesDataStoreFile(FILE_NAME) }

        /** A DataStore over any `*.preferences_pb` file. There must be only one per file in a process. */
        fun createDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = produceFile,
            )

        private val LANGUAGE = stringPreferencesKey("language")
        private val THEME = stringPreferencesKey("theme")
        private val PURE_BLACK = booleanPreferencesKey("pure_black")
        private val QURAN_FONT_SP = intPreferencesKey("quran_font_sp")
        private val BREAK_AT_PAUSE_MARKS = booleanPreferencesKey("break_at_pause_marks")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val GOAL_RANK = stringPreferencesKey("goal_rank")
        private val DAY_START_MINUTES = intPreferencesKey("day_start_minutes")
        private val WEEK_START = stringPreferencesKey("week_start")
        private val CALENDAR_SYSTEM = stringPreferencesKey("calendar_system")
        private val LATIN_NUMERALS = booleanPreferencesKey("latin_numerals")
        private val RECITER_ID = stringPreferencesKey("reciter_id")
        private val AUDIO_QUALITY = intPreferencesKey("audio_quality")
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val COUNT_LISTENED = booleanPreferencesKey("count_listened")
        private val STOP_BEFORE_SAJDA = booleanPreferencesKey("stop_before_sajda")
        private val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        private val STREAK_REMINDER_ENABLED = booleanPreferencesKey("streak_reminder_enabled")
        private val STREAK_REMINDER_MINUTES = intPreferencesKey("streak_reminder_minutes")
        private val BOOKMARK_SORT = stringPreferencesKey("bookmark_sort")
        private val LAST_BROWSE_AYAH_ID = intPreferencesKey("last_browse_ayah_id")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val LEGACY_MIGRATION_STATE = stringPreferencesKey("legacy_migration_state")

        private const val MINUTES_PER_DAY = 24 * 60

        /** The stored value, or null when the key is absent or was written with another type. */
        // Keys are equal by name alone and get() casts unchecked, so the type is checked here.
        private inline fun <reified T : Any> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T

        private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>): E? =
            read(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

        private fun Preferences.time(key: Preferences.Key<Int>): LocalTime? =
            read(key)?.takeIf { it in 0 until MINUTES_PER_DAY }?.let { LocalTime.of(it / 60, it % 60) }

        private fun LocalTime.minutes(): Int = hour * 60 + minute

        internal fun decode(p: Preferences): UserPrefs {
            val d = UserPrefs()
            return UserPrefs(
                language = p.enum<AppLanguage>(LANGUAGE) ?: d.language,
                theme = p.enum<ThemeMode>(THEME) ?: d.theme,
                pureBlack = p.read(PURE_BLACK) ?: d.pureBlack,
                quranFontSp = p.read(QURAN_FONT_SP)?.takeIf { it in UserPrefs.MIN_FONT_SP..UserPrefs.MAX_FONT_SP } ?: d.quranFontSp,
                breakAtPauseMarks = p.read(BREAK_AT_PAUSE_MARKS) ?: d.breakAtPauseMarks,
                keepScreenOn = p.read(KEEP_SCREEN_ON) ?: d.keepScreenOn,
                goalRank = p.enum<Rank>(GOAL_RANK)?.takeIf { it != Rank.NONE },
                dayStartMinutes = p.read(DAY_START_MINUTES)?.takeIf { it in 0..UserPrefs.MAX_DAY_START_MINUTES } ?: d.dayStartMinutes,
                weekStart = p.enum<WeekStart>(WEEK_START) ?: d.weekStart,
                calendarSystem = p.enum<CalendarSystem>(CALENDAR_SYSTEM) ?: d.calendarSystem,
                latinNumerals = p.read(LATIN_NUMERALS) ?: d.latinNumerals,
                reciterId = p.read(RECITER_ID)?.takeIf { it.isNotBlank() } ?: d.reciterId,
                audioQuality = p.read(AUDIO_QUALITY)?.takeIf { it > 0 } ?: d.audioQuality,
                playbackSpeed = p.read(PLAYBACK_SPEED)?.takeIf { it.isFinite() && it > 0f } ?: d.playbackSpeed,
                countListened = p.read(COUNT_LISTENED) ?: d.countListened,
                stopBeforeSajda = p.read(STOP_BEFORE_SAJDA) ?: d.stopBeforeSajda,
                wifiOnlyDownloads = p.read(WIFI_ONLY_DOWNLOADS) ?: d.wifiOnlyDownloads,
                reminderEnabled = p.read(REMINDER_ENABLED) ?: d.reminderEnabled,
                reminderTime = p.time(REMINDER_MINUTES) ?: d.reminderTime,
                streakReminderEnabled = p.read(STREAK_REMINDER_ENABLED) ?: d.streakReminderEnabled,
                streakReminderTime = p.time(STREAK_REMINDER_MINUTES) ?: d.streakReminderTime,
                bookmarkSort = p.enum<BookmarkSort>(BOOKMARK_SORT) ?: d.bookmarkSort,
                lastBrowseAyahId = p.read(LAST_BROWSE_AYAH_ID)?.let(AyahId::orNull),
                onboardingDone = p.read(ONBOARDING_DONE) ?: d.onboardingDone,
                legacyMigrationState = p.enum<LegacyMigrationState>(LEGACY_MIGRATION_STATE) ?: d.legacyMigrationState,
            )
        }

        private fun <T> MutablePreferences.put(key: Preferences.Key<T>, value: T?) {
            // remove() first: a key of the same name holding another type must not survive next to the new value.
            remove(key)
            if (value != null) this[key] = value
        }

        internal fun encode(u: UserPrefs, p: MutablePreferences) {
            p.put(LANGUAGE, u.language.name)
            p.put(THEME, u.theme.name)
            p.put(PURE_BLACK, u.pureBlack)
            p.put(QURAN_FONT_SP, u.quranFontSp)
            p.put(BREAK_AT_PAUSE_MARKS, u.breakAtPauseMarks)
            p.put(KEEP_SCREEN_ON, u.keepScreenOn)
            p.put(GOAL_RANK, u.goalRank?.name)
            p.put(DAY_START_MINUTES, u.dayStartMinutes)
            p.put(WEEK_START, u.weekStart.name)
            p.put(CALENDAR_SYSTEM, u.calendarSystem.name)
            p.put(LATIN_NUMERALS, u.latinNumerals)
            p.put(RECITER_ID, u.reciterId)
            p.put(AUDIO_QUALITY, u.audioQuality)
            p.put(PLAYBACK_SPEED, u.playbackSpeed)
            p.put(COUNT_LISTENED, u.countListened)
            p.put(STOP_BEFORE_SAJDA, u.stopBeforeSajda)
            p.put(WIFI_ONLY_DOWNLOADS, u.wifiOnlyDownloads)
            p.put(REMINDER_ENABLED, u.reminderEnabled)
            p.put(REMINDER_MINUTES, u.reminderTime.minutes())
            p.put(STREAK_REMINDER_ENABLED, u.streakReminderEnabled)
            p.put(STREAK_REMINDER_MINUTES, u.streakReminderTime.minutes())
            p.put(BOOKMARK_SORT, u.bookmarkSort.name)
            p.put(LAST_BROWSE_AYAH_ID, u.lastBrowseAyahId?.value)
            p.put(ONBOARDING_DONE, u.onboardingDone)
            p.put(LEGACY_MIGRATION_STATE, u.legacyMigrationState.name)
        }
    }
}
