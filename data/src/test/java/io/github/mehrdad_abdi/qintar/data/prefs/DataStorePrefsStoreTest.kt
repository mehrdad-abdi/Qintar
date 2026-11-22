package io.github.mehrdad_abdi.qintar.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [DataStorePrefsStore] on a real preferences file. */
class DataStorePrefsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "user_prefs.preferences_pb") }

    /** Opens the file, runs [block], then releases the file so that it can be opened again. */
    private suspend fun <T> withStore(block: suspend (DataStorePrefsStore, DataStore<Preferences>) -> T): T {
        val job = Job()
        val dataStore = DataStorePrefsStore.createDataStore(CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            return block(DataStorePrefsStore(dataStore), dataStore)
        } finally {
            job.cancelAndJoin()
        }
    }

    private val everythingChanged = UserPrefs(
        language = AppLanguage.PERSIAN,
        theme = ThemeMode.DARK,
        pureBlack = true,
        quranFontSp = 36,
        breakAtPauseMarks = false,
        keepScreenOn = false,
        goalRank = Rank.QANIT,
        dayStartMinutes = 240,
        weekStart = WeekStart.SATURDAY,
        calendarSystem = CalendarSystem.PERSIAN,
        latinNumerals = true,
        reciterId = "ar.husary",
        audioQuality = 128,
        playbackSpeed = 1.25f,
        countListened = false,
        stopBeforeSajda = true,
        sajdaDhikr = false,
        wifiOnlyDownloads = false,
        reminderEnabled = true,
        reminderTime = LocalTime.of(5, 45),
        streakReminderEnabled = true,
        streakReminderTime = LocalTime.of(23, 59),
        bookmarkSort = BookmarkSort.CUSTOM,
        lastBrowseAyahId = AyahId(6236),
        onboardingDone = true,
        legacyMigrationState = LegacyMigrationState.MIGRATED,
    )

    @Test
    fun aFreshInstallReadsAsTheDefaults() = runTest {
        withStore { store, _ -> assertEquals(UserPrefs(), store.prefs.first()) }
    }

    @Test
    fun everyPreferenceSurvivesAProcessRestart() = runTest {
        // Guards the test itself: each field must differ from its default, or it proves nothing.
        val defaults = UserPrefs().toString().removeSurrounding("UserPrefs(", ")").split(", ")
        val changed = everythingChanged.toString().removeSurrounding("UserPrefs(", ")").split(", ")
        assertEquals(emptyList<String>(), defaults.intersect(changed.toSet()).toList())

        val returned = withStore { store, _ -> store.update { everythingChanged } }
        assertEquals(everythingChanged, returned)

        withStore { store, _ -> assertEquals(everythingChanged, store.prefs.first()) }
    }

    @Test
    fun clearingAnOptionalPreferenceIsRemembered() = runTest {
        withStore { store, _ -> store.update { everythingChanged } }
        withStore { store, _ -> store.update { it.copy(goalRank = null, lastBrowseAyahId = null) } }

        withStore { store, _ ->
            assertEquals(everythingChanged.copy(goalRank = null, lastBrowseAyahId = null), store.prefs.first())
        }
    }

    @Test
    fun anUpdateSeesTheCurrentValueAndObserversSeeEachChangeOnce() = runTest {
        withStore { store, _ ->
            store.prefs.test {
                assertEquals(UserPrefs(), awaitItem())

                store.update { it.copy(quranFontSp = it.quranFontSp + 2) }
                assertEquals(30, awaitItem().quranFontSp)

                val result = store.update { it.copy(quranFontSp = it.quranFontSp + 2, theme = ThemeMode.LIGHT) }
                assertEquals(UserPrefs(quranFontSp = 32, theme = ThemeMode.LIGHT), result)
                assertEquals(result, awaitItem())

                // Writing the same value again is not a change.
                store.update { it }
                store.update { it.copy(pureBlack = true) }
                assertEquals(result.copy(pureBlack = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun aRefusedUpdateChangesNothing() = runTest {
        withStore { store, _ ->
            store.update { it.copy(dayStartMinutes = 120) }
            try {
                store.update { it.copy(dayStartMinutes = 5000) }
                fail("an impossible day start was stored")
            } catch (_: IllegalArgumentException) {
            }
            assertEquals(120, store.prefs.first().dayStartMinutes)
        }
    }

    @Test
    fun valuesTheModelRefusesReadAsTheirDefaultsWhileGoodOnesAreKept() = runTest {
        withStore { store, dataStore ->
            dataStore.edit {
                it[stringPreferencesKey("language")] = "KLINGON"
                it[stringPreferencesKey("theme")] = "dark"
                it[intPreferencesKey("quran_font_sp")] = 900
                it[stringPreferencesKey("goal_rank")] = "NONE"
                it[intPreferencesKey("day_start_minutes")] = -5
                it[stringPreferencesKey("reciter_id")] = "   "
                it[intPreferencesKey("audio_quality")] = 0
                it[floatPreferencesKey("playback_speed")] = Float.NaN
                it[intPreferencesKey("reminder_minutes")] = 24 * 60
                it[intPreferencesKey("streak_reminder_minutes")] = -1
                it[intPreferencesKey("last_browse_ayah_id")] = 6237
                it[stringPreferencesKey("legacy_migration_state")] = ""
                // Two good values among the bad ones.
                it[booleanPreferencesKey("pure_black")] = true
                it[stringPreferencesKey("week_start")] = "MONDAY"
            }

            assertEquals(UserPrefs(pureBlack = true, weekStart = WeekStart.MONDAY), store.prefs.first())
        }
    }

    @Test
    fun valuesOfTheWrongTypeReadAsDefaultsAndAreRepairedByTheNextUpdate() = runTest {
        withStore { store, dataStore ->
            dataStore.edit {
                it[stringPreferencesKey("quran_font_sp")] = "huge"
                it[intPreferencesKey("theme")] = 2
                it[stringPreferencesKey("pure_black")] = "yes"
                it[booleanPreferencesKey("playback_speed")] = true
                it[floatPreferencesKey("reminder_minutes")] = 1.5f
                it[intPreferencesKey("reciter_id")] = 7
                it[booleanPreferencesKey("goal_rank")] = true
                it[stringPreferencesKey("last_browse_ayah_id")] = "2:255"
                it[stringPreferencesKey("some_key_from_a_future_version")] = "kept"
            }
            assertEquals(UserPrefs(), store.prefs.first())

            assertEquals(UserPrefs(quranFontSp = 40), store.update { it.copy(quranFontSp = 40) })
        }
        withStore { store, dataStore ->
            assertEquals(UserPrefs(quranFontSp = 40), store.prefs.first())
            val stored = dataStore.data.first().asMap().mapKeys { it.key.name }
            assertEquals(40, stored["quran_font_sp"])
            assertEquals("SYSTEM", stored["theme"])
            assertEquals(false, stored["pure_black"])
            assertEquals("kept", stored["some_key_from_a_future_version"])
        }
    }

    @Test
    fun aCorruptFileReadsAsDefaultsAndCanBeWrittenAgain() = runTest {
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(64) { 0xFF.toByte() } + "not a protobuf".toByteArray())

        withStore { store, _ ->
            assertEquals(UserPrefs(), store.prefs.first())
            store.update { it.copy(onboardingDone = true) }
        }
        withStore { store, _ -> assertTrue(store.prefs.first().onboardingDone) }
    }
}
