package io.github.mehrdad_abdi.qintar.feature.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.player.READER_PLAYER_TAG
import io.github.mehrdad_abdi.qintar.playback.DownloadState
import io.github.mehrdad_abdi.qintar.playback.PlaybackProblem
import io.github.mehrdad_abdi.qintar.playback.origin
import io.github.mehrdad_abdi.qintar.testing.FakeAudioDownloads
import io.github.mehrdad_abdi.qintar.testing.FakePlayerController
import io.github.mehrdad_abdi.qintar.testing.playerBarViewModel
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real reader screen with its player, driven by taps, over a player that plays nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ReaderAudioScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = DomainFixture()
    private val player = FakePlayerController()
    private val downloads = FakeAudioDownloads()
    private val route = ReaderRoute.browse(app.ayah(2, 1).value)
    private val navigated = ArrayList<Any>()

    private fun open() {
        val viewModel = app.readerViewModel(route, Dispatchers.Unconfined, player = player, downloads = downloads)
        val bar = app.playerBarViewModel(player, Dispatchers.Unconfined)
        compose.setContent {
            QintarTheme { ReaderScreen(route, onBack = {}, onNavigate = { navigated += it }, viewModel = viewModel, player = { bar }) }
        }
    }

    @Test
    fun `the play button of an ayah starts playback from it, and no player shows before that`() {
        open()
        compose.onNodeWithTag(READER_PLAYER_TAG).assertDoesNotExist()

        compose.onAllNodesWithContentDescription("Play")[1].performClick()

        assertEquals(app.ayah(2, 2), player.requests.single().startAyah)
        assertTrue("listening has not been counted by starting", app.log.events.isEmpty())
    }

    @Test
    fun `while an ayah plays the player shows what plays, and its controls reach the player`() {
        open()
        player.nowAt(route.origin(), 0, app.ayah(2, 2))
        player.state.value = player.state.value.copy(reciterId = "ar.alafasy")

        compose.onNodeWithTag(READER_PLAYER_TAG).assertIsDisplayed()
        compose.onNodeWithText("Surah 2 2 · Mishary Alafasy").assertIsDisplayed()
        // The row that plays offers pause.
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()

        compose.onNodeWithContentDescription("Next ayah").performClick()
        compose.onNodeWithContentDescription("Previous ayah").performClick()
        compose.onNodeWithContentDescription("Pause audio").performClick()
        assertEquals(listOf("next", "previous", "toggle"), player.calls)

        compose.onNodeWithContentDescription("Playback speed 1×").performClick()
        assertEquals(1.25f, app.prefs.current.playbackSpeed)
        compose.onNodeWithContentDescription("Playback speed 1.25×").assertIsDisplayed()

        compose.onNodeWithContentDescription("Repeat").performClick()
        compose.onNodeWithText("Each ayah 3 times").performClick()
        assertEquals(3, player.repeats.single().ayahTimes)

        compose.onNodeWithContentDescription("Close player").performClick()
        compose.onNodeWithTag(READER_PLAYER_TAG).assertDoesNotExist()
    }

    @Test
    fun `offline without cached audio says so instead of stalling`() {
        open()
        player.nowAt(route.origin(), 0, app.ayah(2, 2), playing = false)
        player.state.value = player.state.value.copy(problem = PlaybackProblem.OFFLINE)

        compose.onNodeWithText("Audio needs a connection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play audio").assertIsDisplayed()
    }

    @Test
    fun `audio of another reader shows in the player, and its title leads to that reader`() {
        open()
        val other = ReaderRoute.random(app.ayah(112, 1).value, app.ayah(112, 4).value)
        player.nowAt(other.origin(), 0, app.ayah(112, 2))

        compose.onNodeWithText("Surah 112 2").performClick()

        assertEquals(listOf<Any>(other.origin().route()), navigated)
    }

    @Test
    fun `the audio menu offers play from here and the surah for offline, and shows the download`() {
        open()
        compose.onNodeWithContentDescription("Audio").performClick()
        compose.onNodeWithText("Download this passage for offline").assertDoesNotExist()
        compose.onNodeWithText("Download this surah for offline").performClick()
        assertEquals(listOf(listOf(app.index.surahRange(2))), downloads.requested)

        downloads.state.value = DownloadState.Running(12, 287)
        compose.onNodeWithText("Downloading audio: 12 of 287").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, downloads.cancelled)

        downloads.state.value = DownloadState.Blocked(DownloadState.Blocked.Reason.WIFI_ONLY)
        compose.onNodeWithText("Downloads wait for Wi-Fi. You can change this in Settings.").assertIsDisplayed()

        compose.onNodeWithContentDescription("Audio").performClick()
        compose.onNodeWithText("Play from here").performClick()
        assertEquals(1, player.requests.size)
    }
}
