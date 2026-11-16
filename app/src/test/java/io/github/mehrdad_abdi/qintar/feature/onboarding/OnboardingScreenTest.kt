package io.github.mehrdad_abdi.qintar.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.settings.SettingsTags
import io.github.mehrdad_abdi.qintar.testing.onboardingViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real onboarding screen, driven by taps. */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var finished = 0

    private fun open(app: DomainFixture) {
        val vm = app.onboardingViewModel()
        compose.setContent { QintarTheme { OnboardingScreen(onFinished = { finished++ }, viewModel = vm) } }
    }

    @Test
    fun `the tour walks through the ranks, the language, and the goal and reminder, then starts`() {
        val app = DomainFixture()
        open(app)

        compose.onNodeWithText("Welcome").assertIsDisplayed()
        compose.onNodeWithText("Read a little every day").assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.NEXT).performClick()

        compose.onNodeWithText("Choose your language").assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.LANGUAGE_PREFIX + AppLanguage.ARABIC.name).performClick()
        compose.waitForIdle()
        assertEquals(AppLanguage.ARABIC, app.prefs.current.language)
        compose.onNodeWithTag(OnboardingTags.NEXT).performClick()

        compose.onNodeWithText("A goal and a reminder").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.GOAL_PREFIX + Rank.QANIT.name).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Rank.QANIT, app.prefs.current.goalRank)
        assertEquals(0, finished)

        compose.onNodeWithTag(OnboardingTags.DONE).performClick()
        compose.waitUntil(5_000) { finished > 0 }
        assertEquals(1, finished)
        assertTrue(app.prefs.current.onboardingDone)
        assertEquals("the choices were kept", Rank.QANIT, app.prefs.current.goalRank)
    }

    @Test
    fun `skip leaves from any page and counts as done`() {
        val app = DomainFixture()
        open(app)
        compose.onNodeWithTag(OnboardingTags.NEXT).performClick()

        compose.onNodeWithTag(OnboardingTags.SKIP).performClick()

        compose.waitUntil(5_000) { finished > 0 }
        assertEquals(1, finished)
        assertTrue(app.prefs.current.onboardingDone)
    }

    @Test
    fun `back returns to the previous page`() {
        open(DomainFixture())
        compose.onNodeWithTag(OnboardingTags.NEXT).performClick()
        compose.onNodeWithTag(OnboardingTags.BACK).performClick()
        compose.onNodeWithText("Read a little every day").assertIsDisplayed()
    }

    @Test
    fun `a migrated user gets one what's new page`() {
        val app = DomainFixture(initialPrefs = UserPrefs(legacyMigrationState = LegacyMigrationState.MIGRATED))
        open(app)

        compose.onNodeWithText("Welcome to Qintar 2").assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.NEXT).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.DONE).performClick()

        compose.waitUntil(5_000) { finished > 0 }
        assertEquals(1, finished)
        assertTrue(app.prefs.current.onboardingDone)
    }
}
