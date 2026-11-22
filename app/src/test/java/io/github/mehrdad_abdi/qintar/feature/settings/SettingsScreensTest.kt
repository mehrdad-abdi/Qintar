package io.github.mehrdad_abdi.qintar.feature.settings

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyReport
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.backupViewModel
import io.github.mehrdad_abdi.qintar.testing.settingsViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.LicencesRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The real Settings, licences and backup dialogs, driven by taps, over the in-memory domain. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private val navigated = ArrayList<Any>()

    private fun open(fixture: DomainFixture = app): BackupViewModel {
        val backup = fixture.backupViewModel(Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme {
                SettingsScreen(onBack = {}, onNavigate = { navigated += it }, viewModel = fixture.settingsViewModel(), backupViewModel = backup)
            }
        }
        return backup
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag).performScrollTo()

    @Test
    fun `the sections are all there, and calendar system is not offered`() {
        open()
        for (title in listOf("Appearance", "Reading", "Goal and days", "Reminders", "Data", "About")) {
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Calendar system").assertDoesNotExist()
    }

    @Test
    fun `switches are saved and shown`() {
        open()
        tag(SettingsTags.PURE_BLACK).assertIsDisplayed().performClick()
        compose.onNodeWithTag(SettingsTags.PURE_BLACK).assertIsOn()
        assertTrue(app.prefs.current.pureBlack)

        tag(SettingsTags.LATIN_NUMERALS).performClick()
        assertTrue(app.prefs.current.latinNumerals)

        tag(SettingsTags.KEEP_SCREEN_ON).performClick()
        assertFalse(app.prefs.current.keepScreenOn)
        tag(SettingsTags.BREAK_AT_PAUSE).performClick()
        assertFalse(app.prefs.current.breakAtPauseMarks)
    }

    @Test
    fun `the language and the theme are chosen from a list`() {
        open()
        tag(SettingsTags.LANGUAGE).performClick()
        compose.onNodeWithTag("${SettingsTags.LANGUAGE}-option-PERSIAN").performClick()
        assertEquals(AppLanguage.PERSIAN, app.prefs.current.language)

        tag(SettingsTags.THEME).performClick()
        compose.onNodeWithTag("${SettingsTags.THEME}-option-DARK").performClick()
        assertEquals(ThemeMode.DARK, app.prefs.current.theme)
    }

    @Test
    fun `a goal is picked from the ladder and can be cleared`() {
        open()
        tag(SettingsTags.GOAL_PREFIX + Rank.DHAKIR.name).performClick()
        assertEquals(Rank.DHAKIR, app.prefs.current.goalRank)
        tag(SettingsTags.GOAL_PREFIX + Rank.QINTAR.name).performClick()
        assertEquals(Rank.QINTAR, app.prefs.current.goalRank)
        tag(SettingsTags.GOAL_NONE).performClick()
        assertEquals(null, app.prefs.current.goalRank)
    }

    @Test
    fun `a reminder is turned on once the permission is held, and shows its time`() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        open()
        tag(SettingsTags.REMINDER).performClick()
        assertTrue(app.prefs.current.reminderEnabled)
        compose.onNodeWithText("20:00").assertExists()
        assertFalse(app.prefs.current.streakReminderEnabled)

        tag(SettingsTags.STREAK_REMINDER).performClick()
        assertTrue(app.prefs.current.streakReminderEnabled)
        tag(SettingsTags.REMINDER).performClick()
        assertFalse(app.prefs.current.reminderEnabled)
    }

    @Test
    fun `about shows the version, the privacy note and the Tanzil attribution`() {
        open()
        compose.onNodeWithText("Version", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("no account and no analytics", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tanzil Quran Text", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("not modified", substring = true).assertExists()
        tag(SettingsTags.TANZIL_LINK).assertIsDisplayed().assertIsEnabled()

        tag(SettingsTags.LICENCES).performClick()
        assertEquals(listOf<Any>(LicencesRoute), navigated)
    }

    @Test
    fun `the licences screen reads the texts shipped with the app`() {
        compose.setContent { QintarTheme { LicencesScreen(onBack = {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("Vazirmatn-OFL") > 0 }
        compose.onNodeWithText("Vazirmatn-OFL").assertExists()
        compose.onNodeWithText("AmiriQuran-OFL").assertExists()
        compose.onNodeWithText("Tanzil Quran Text", substring = true).assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `a failed migration offers to try again from the data section`() {
        runBlocking { app.prefs.update { it.copy(legacyMigrationState = LegacyMigrationState.FAILED) } }
        open()
        compose.onNodeWithText("old data is untouched", substring = true).performScrollTo().assertIsDisplayed()
        tag(SettingsTags.MIGRATION_RETRY).performClick()
        assertEquals(LegacyMigrationState.MIGRATED, app.prefs.current.legacyMigrationState)
        compose.onNodeWithText("were carried over", substring = true).performScrollTo().assertIsDisplayed()
    }

    // ---- the import flow, as the user sees it ---------------------------------------------------------

    private val v1File = """
        {"metadata":{"version":"1.0"},
         "readingActivity":[{"date":"2025-09-30","totalAyahsRead":3,"trackedAyahIds":["-1:1:1","-1:1:2","-1:1:3"]}]}
    """.trimIndent()

    private fun seedAndExport(): String {
        runBlocking { app.tracker.markRange(AyahRange.of(1, 12), ReadContext.Browse, ReadMethod.TAP, RangeMode.ADD, day = app.clock.today()) }
        var text = ""
        app.backupViewModel(Dispatchers.Unconfined).export { text = it }
        return text
    }

    @Test
    fun `a chosen file is previewed first and Replace is warned about and confirmed twice`() {
        val text = seedAndExport()
        val other = DomainFixture()
        val backup = open(other)

        backup.chooseFile("Khatm") { text }
        compose.waitForIdle()

        compose.onNodeWithTag(BackupTags.PREVIEW).assertIsDisplayed()
        compose.onNodeWithText("12 ayahs counted").assertIsDisplayed()
        assertEquals(0, runBlocking { other.log.allEvents().size })

        compose.onNodeWithTag(BackupTags.REPLACE).performClick()
        compose.onNodeWithText("cannot be undone", substring = true).assertIsDisplayed()
        assertEquals("still nothing written", 0, runBlocking { other.log.allEvents().size })

        compose.onNodeWithTag(BackupTags.CONFIRM_REPLACE).performClick()
        compose.onNodeWithTag(BackupTags.RESULT).assertIsDisplayed()
        assertEquals(12, runBlocking { other.log.allEvents().size })
        compose.onNodeWithTag(BackupTags.OK).performClick()
        compose.onNodeWithTag(BackupTags.RESULT).assertDoesNotExist()
    }

    @Test
    fun `merge is one tap, and a v1 file offers only merge`() {
        val backup = open()

        backup.chooseFile("Khatm") { v1File }
        compose.waitForIdle()
        compose.onNodeWithText("converted and merged", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(BackupTags.REPLACE).assertDoesNotExist()
        compose.onNodeWithTag(BackupTags.MERGE).performClick()

        compose.onNodeWithTag(BackupTags.RESULT).assertIsDisplayed()
        assertEquals(3, runBlocking { app.log.allEvents().size })
    }

    @Test
    fun `an invalid file shows its reason`() {
        val backup = open()
        backup.chooseFile("Khatm") { "not json {" }
        compose.waitForIdle()
        compose.onNodeWithText("not valid JSON", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(BackupTags.OK).performClick()
        backup.chooseFile("Khatm") { """{"x":1}""" }
        compose.waitForIdle()
        compose.onNodeWithText("not a Qintar backup", substring = true).assertIsDisplayed()
    }

    // ---- the card on Today ---------------------------------------------------------------------------

    @Test
    fun `the carried over card shows its numbers and is dismissed`() {
        val dismissed = mutableStateOf(0)
        val report = LegacyReport(bookmarks = 4, groups = 2, events = 9, talliedAyahs = 60, days = 3, khatmCarried = true, skippedAyahIds = 2, skippedBookmarks = 0, skippedDays = 0)
        compose.setContent {
            QintarTheme { MigrationNoticeCard(MigrationNotice.Carried(report), onDismiss = { dismissed.value++ }, onRetry = {}) }
        }
        compose.onNodeWithText("were carried over", substring = true).assertIsDisplayed()
        compose.onNodeWithText("4 bookmarks, 3 days of reading, 69 ayahs.").assertIsDisplayed()
        compose.onNodeWithText("2 items could not be read", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(MIGRATION_DISMISS_TAG).performClick()
        assertEquals(1, dismissed.value)
    }

    @Test
    fun `the failure card says the old data is untouched and retries`() {
        val retried = ArrayList<String>()
        compose.setContent {
            QintarTheme { MigrationNoticeCard(MigrationNotice.Failed(retrying = false), onDismiss = {}, onRetry = { retried += it }) }
        }
        compose.onNodeWithText("untouched", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(MIGRATION_RETRY_TAG).performClick()
        assertEquals(1, retried.size)
    }

    @Test
    fun `nothing is drawn while there is nothing to say`() {
        compose.setContent { QintarTheme { MigrationNoticeCard(MigrationNotice.Hidden, onDismiss = {}, onRetry = {}) } }
        compose.onNodeWithTag(MIGRATION_CARD_TAG).assertDoesNotExist()
    }
}
