package io.github.mehrdad_abdi.qintar.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.playback.Reciters
import io.github.mehrdad_abdi.qintar.testing.FakeAudioStorage
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.backupViewModel
import io.github.mehrdad_abdi.qintar.testing.settingsViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings › Audio (§5.9): each control is saved, and the stored audio can be measured and cleared. */
class SettingsAudioViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()
    private val storage = FakeAudioStorage(bytes = 3_000_000)
    private val vm by lazy { app.settingsViewModel(audio = storage) }

    @Test
    fun `the audio settings start at the defaults`() {
        val prefs = vm.state.value.prefs
        assertEquals(UserPrefs.DEFAULT_RECITER, prefs.reciterId)
        assertEquals(64, prefs.audioQuality)
        assertEquals(1f, prefs.playbackSpeed)
        assertTrue(prefs.countListened)
        assertTrue(prefs.wifiOnlyDownloads)
    }

    @Test
    fun `speed, count listened and Wi-Fi only persist`() {
        vm.setPlaybackSpeed(1.5f)
        vm.setCountListened(false)
        vm.setWifiOnlyDownloads(false)

        for (prefs in listOf(vm.state.value.prefs, app.prefs.current)) {
            assertEquals(1.5f, prefs.playbackSpeed)
            assertFalse(prefs.countListened)
            assertFalse(prefs.wifiOnlyDownloads)
        }

        vm.setPlaybackSpeed(40f)
        assertEquals("the speed stays in the player's range", 2f, app.prefs.current.playbackSpeed)
    }

    @Test
    fun `choosing a reciter persists and moves the quality to one that reciter has`() {
        vm.setReciter("ar.husary")
        assertEquals("ar.husary", app.prefs.current.reciterId)
        assertEquals(64, app.prefs.current.audioQuality)

        vm.setReciter("ar.minshawi")
        assertEquals("only 128 exists", 128, vm.state.value.prefs.audioQuality)

        vm.setReciter("ar.parhizgar")
        assertEquals(48, app.prefs.current.audioQuality)
    }

    @Test
    fun `a reciter that is not in the list is not stored`() {
        vm.setReciter("xx.unknown")
        assertEquals(UserPrefs.DEFAULT_RECITER, app.prefs.current.reciterId)
    }

    @Test
    fun `the quality is one of the chosen reciter's bitrates`() {
        vm.setAudioQuality(128)
        assertEquals(128, app.prefs.current.audioQuality)
        vm.setAudioQuality(320)
        assertTrue(app.prefs.current.audioQuality in Reciters.of(UserPrefs.DEFAULT_RECITER).bitrates)
    }

    @Test
    fun `the stored audio is measured, and clearing it empties it and measures again`() {
        assertEquals(3_000_000L, vm.state.value.audioBytes)

        vm.clearAudio()

        assertEquals(1, storage.cleared)
        assertEquals(0L, vm.state.value.audioBytes)
    }
}

/** The Audio section on the real Settings screen. */
@RunWith(RobolectricTestRunner::class)
class SettingsAudioScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private val storage = FakeAudioStorage(bytes = 5L * 1024 * 1024)

    private fun open() {
        val backup = app.backupViewModel(Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme {
                SettingsScreen(onBack = {}, onNavigate = {}, viewModel = app.settingsViewModel(audio = storage), backupViewModel = backup)
            }
        }
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag).performScrollTo()

    @Test
    fun `the audio section shows the reciter by name and lets another be chosen`() {
        open()
        compose.onNodeWithText("Audio").performScrollTo().assertIsDisplayed()
        tag(SettingsTags.RECITER).assertIsDisplayed()
        compose.onNodeWithText("Mishary Alafasy").assertIsDisplayed()

        tag(SettingsTags.RECITER).performClick()
        compose.onNodeWithTag("${SettingsTags.RECITER}-option-ar.minshawi").performClick()

        assertEquals("ar.minshawi", app.prefs.current.reciterId)
        assertEquals(128, app.prefs.current.audioQuality)
        compose.onNodeWithText("128 kbps").assertExists()
    }

    @Test
    fun `count listened and Wi-Fi only are switches that are saved`() {
        open()
        tag(SettingsTags.COUNT_LISTENED).performClick()
        compose.onNodeWithTag(SettingsTags.COUNT_LISTENED).assertIsOff()
        assertFalse(app.prefs.current.countListened)

        tag(SettingsTags.WIFI_ONLY).performClick()
        assertFalse(app.prefs.current.wifiOnlyDownloads)
    }

    @Test
    fun `the speed is chosen from the list`() {
        open()
        tag(SettingsTags.PLAYBACK_SPEED).performClick()
        compose.onNodeWithTag("${SettingsTags.PLAYBACK_SPEED}-option-1.5").performClick()
        assertEquals(1.5f, app.prefs.current.playbackSpeed)
    }

    @Test
    fun `clear stored audio shows the size and empties the store`() {
        open()
        tag(SettingsTags.CLEAR_AUDIO).assertIsDisplayed()
        compose.onNodeWithText("stored on this device", substring = true).assertExists()

        tag(SettingsTags.CLEAR_AUDIO).performClick()

        assertEquals(1, storage.cleared)
        compose.onNodeWithText("Nothing stored").assertExists()
    }
}
