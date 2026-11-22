package io.github.mehrdad_abdi.qintar.diagnostics

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.feature.settings.DiagnosticsScreen
import io.github.mehrdad_abdi.qintar.feature.settings.DiagnosticsTags
import io.github.mehrdad_abdi.qintar.settingsOf
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/** The canary's screen and its bug button, driven by taps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticsUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dir: File = Files.createTempDirectory("diagnostics").toFile()
    private val diagnostics = Diagnostics(dir, { Instant.parse("2026-11-22T14:05:31Z") }, { "device" }, { it.run() })

    private fun events() = File(dir, "events.log").takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun `the screen explains how to report, and its switches turn recording and screenshots on`() {
        compose.setContent { QintarTheme { DiagnosticsScreen(onBack = {}, diagnostics = diagnostics) } }
        compose.onNodeWithText("To report a bug").assertIsDisplayed()
        compose.onNodeWithText("attach the saved file", substring = true).assertExists()

        compose.onNodeWithTag(DiagnosticsTags.SCREENSHOTS).assertIsNotEnabled()
        compose.onNodeWithTag(DiagnosticsTags.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(DiagnosticsTags.RECORD).performScrollTo().performClick()
        assertTrue(diagnostics.state.value.enabled)
        compose.onNodeWithTag(DiagnosticsTags.SCREENSHOTS).assertIsEnabled().performClick()
        assertTrue(diagnostics.state.value.screenshotsAllowed)
        compose.onNodeWithText("1 events · 0 crashes · 0 screenshots").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `save asks where to keep the zip, the issue button opens GitHub, and clear empties the record`() {
        diagnostics.setEnabled(true)
        compose.setContent { QintarTheme { DiagnosticsScreen(onBack = {}, diagnostics = diagnostics) } }

        compose.onNodeWithTag(DiagnosticsTags.SAVE).performScrollTo().assertIsEnabled().performClick()
        val save = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, save.action)
        assertEquals("qintar-diagnostics-2026-11-22-1405.zip", save.getStringExtra(Intent.EXTRA_TITLE))

        compose.onNodeWithTag(DiagnosticsTags.ISSUE).performScrollTo().performClick()
        val issue = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, issue.action)
        assertEquals(Diagnostics.NEW_ISSUE_URL, issue.dataString)

        compose.onNodeWithTag(DiagnosticsTags.CLEAR).performScrollTo().performClick()
        assertTrue(diagnostics.state.value.isEmpty)
    }

    @Test
    fun `the bug button shows only while recording, and marks the problem with a screenshot when allowed`() {
        compose.setContent {
            QintarTheme { CompositionLocalProvider(LocalDiagnostics provides diagnostics) { Box { DiagnosticsMarker(diagnostics, "ReaderRoute") } } }
        }
        compose.onNodeWithTag(DIAGNOSTICS_MARKER_TAG).assertDoesNotExist()

        diagnostics.setEnabled(true)
        compose.onNodeWithTag(DIAGNOSTICS_MARKER_TAG).performClick()
        assertTrue(events().contains("[marker] the user marked a problem on ReaderRoute"))
        assertEquals("no screenshot without permission", 0, diagnostics.state.value.screenshots)

        diagnostics.setScreenshotsAllowed(true)
        compose.onNodeWithTag(DIAGNOSTICS_MARKER_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, diagnostics.state.value.screenshots)
    }

    @Test
    fun `setting changes are logged by name and value`() {
        val before = settingsOf(UserPrefs())
        val after = settingsOf(UserPrefs(reminderEnabled = true, sajdaDhikr = false))
        assertEquals(mapOf("reminderEnabled" to "true", "sajdaDhikr" to "false"), after.filter { (k, v) -> before[k] != v })
    }
}
