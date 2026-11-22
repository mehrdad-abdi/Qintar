package io.github.mehrdad_abdi.qintar.feature.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyReport
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.onboarding.OnboardingScreen
import io.github.mehrdad_abdi.qintar.testing.backupViewModel
import io.github.mehrdad_abdi.qintar.testing.onboardingViewModel
import io.github.mehrdad_abdi.qintar.testing.settingsViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Settings, onboarding and the carried-over card, in light and dark, English and Persian.
 * Record with `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SettingsScreenshotTest(private val language: String, private val dark: Boolean) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = DomainFixture(
        initialPrefs = UserPrefs(
            goalRank = Rank.QANIT,
            dayStartMinutes = 180,
            reminderEnabled = true,
            reminderTime = LocalTime.of(20, 0),
            quranFontSp = 32,
        ),
    )

    private fun shot(name: String, heightDp: Int, content: @Composable () -> Unit) {
        val night = if (dark) "night" else "notnight"
        RuntimeEnvironment.setQualifiers("$language-w412dp-h${heightDp}dp-$night-xhdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { QintarTheme(darkTheme = dark) { Box(Modifier.height(heightDp.dp).testTag(TAG)) { content() } } }
            }
            val theme = if (dark) "dark" else "light"
            compose.onNodeWithTag(TAG).captureRoboImage("src/test/screenshots/${name}_${theme}_$language.png")
        }
    }

    @Test
    fun settings() {
        val vm = app.settingsViewModel()
        val backup = app.backupViewModel(Dispatchers.Unconfined)
        shot("feature_settings", 2600) { SettingsScreen(onBack = {}, onNavigate = {}, viewModel = vm, backupViewModel = backup) }
    }

    @Test
    fun onboardingHadith() {
        val vm = app.onboardingViewModel()
        shot("feature_onboarding_hadith", 1500) { OnboardingScreen(onFinished = {}, viewModel = vm) }
    }

    @Test
    fun onboardingRanks() {
        val vm = app.onboardingViewModel()
        vm.next()
        shot("feature_onboarding_ranks", 915) { OnboardingScreen(onFinished = {}, viewModel = vm) }
    }

    @Test
    fun onboardingGoal() {
        val vm = app.onboardingViewModel()
        repeat(3) { vm.next() }
        shot("feature_onboarding_goal", 1300) { OnboardingScreen(onFinished = {}, viewModel = vm) }
    }

    @Test
    fun migrationCard() {
        val report = LegacyReport(bookmarks = 12, groups = 3, events = 480, talliedAyahs = 60, days = 41, khatmCarried = true, skippedAyahIds = 0, skippedBookmarks = 0, skippedDays = 0)
        shot("feature_migration_card", 520) {
            androidx.compose.foundation.layout.Column(Modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                MigrationNoticeCard(MigrationNotice.Carried(report), onDismiss = {}, onRetry = {})
                MigrationNoticeCard(MigrationNotice.Failed(retrying = false), onDismiss = {}, onRetry = {})
            }
        }
    }

    private companion object {
        const val TAG = "shot"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> = listOf(
            arrayOf("en", false),
            arrayOf("en", true),
            arrayOf("fa", false),
            arrayOf("fa", true),
        )
    }

}
