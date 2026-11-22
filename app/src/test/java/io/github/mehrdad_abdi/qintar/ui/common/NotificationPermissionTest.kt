package io.github.mehrdad_abdi.qintar.ui.common

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.onboarding.OnboardingScreen
import io.github.mehrdad_abdi.qintar.feature.onboarding.OnboardingTags
import io.github.mehrdad_abdi.qintar.testing.onboardingViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The owner's bug (2026-11-22): in the tour, "Allow" was tapped in the system dialog, yet the red
 * "notifications are blocked" text stayed. The gate trusted the dialog's reported answer; it now asks
 * the system what is really allowed, and the red text opens the app's notification settings.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPermissionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val manager get() = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    @Test
    fun `a dialog that reports no while the system allows notifications counts as allowed`() {
        assertTrue(NotificationGate.resolve(reported = false, allowedNow = true))
        assertTrue(NotificationGate.resolve(reported = true, allowedNow = true))
        assertFalse(NotificationGate.resolve(reported = false, allowedNow = false))
        // The dialog said yes but the app's notifications are off in the system: still blocked.
        assertFalse(NotificationGate.resolve(reported = true, allowedNow = false))
    }

    @Test
    fun `blocked notifications show a red text that opens the settings, and coming back allowed turns the reminder on`() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        manager.setNotificationsEnabled(false)
        val fixture = DomainFixture()
        val vm = fixture.onboardingViewModel()
        compose.setContent { QintarTheme { OnboardingScreen(onFinished = {}, viewModel = vm) } }
        repeat(3) { compose.onNodeWithTag(OnboardingTags.NEXT).performClick() }

        compose.onNodeWithTag(OnboardingTags.REMINDER).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(fixture.prefs.current.reminderEnabled)
        compose.onNodeWithTag(NOTIFICATIONS_BLOCKED_TAG).performScrollTo().assertIsDisplayed().performClick()

        val opened = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(app.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        // The user allows them in the system settings and comes back.
        manager.setNotificationsEnabled(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithTag(NOTIFICATIONS_BLOCKED_TAG).assertDoesNotExist()
        assertTrue("the reminder the user wanted is on", fixture.prefs.current.reminderEnabled)
        compose.onNodeWithText("Time").performScrollTo().assertIsDisplayed()
    }
}
