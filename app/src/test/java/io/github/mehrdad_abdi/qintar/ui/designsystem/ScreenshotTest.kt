package io.github.mehrdad_abdi.qintar.ui.designsystem

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
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.ComponentGalleryA
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.ComponentGalleryB
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.RankMarkGallery
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.ReaderShowcase
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.ReaderShowcaseSample
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.TodayShowcase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Visual tests (§8.1): the component galleries and both showcases, in light and dark, in English
 * and in Persian (RTL). Text is shaped and drawn by Robolectric's native graphics, with the
 * bundled fonts.
 *
 * Record with `./gradlew :app:recordRoborazziDebug`, compare with `:app:verifyRoborazziDebug`.
 * The images live in `app/src/test/screenshots/` so that changes show up in review.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ScreenshotTest(private val language: String, private val dark: Boolean) {

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun shot(name: String, windowHeightDp: Int = 3600, windowWidthDp: Int = 360, content: @Composable () -> Unit) {
        val night = if (dark) "night" else "notnight"
        // The window is taller than any gallery; the capture is cropped to the content node.
        RuntimeEnvironment.setQualifiers("$language-w${windowWidthDp}dp-h${windowHeightDp}dp-$night-xhdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    QintarTheme(darkTheme = dark) { Box(Modifier.testTag(TAG)) { content() } }
                }
            }
            val theme = if (dark) "dark" else "light"
            compose.onNodeWithTag(TAG).captureRoboImage("src/test/screenshots/${name}_${theme}_$language.png")
        }
    }

    @Test
    fun rankMarks() = shot("rank_marks") { RankMarkGallery() }

    @Test
    fun componentsTodayAndBookmarks() = shot("components_a") { ComponentGalleryA() }

    @Test
    fun componentsReaderInputsAndInsights() = shot("components_b") { ComponentGalleryB() }

    @Test
    fun todayShowcase() = shot("today") { TodayShowcase() }

    @Test
    fun readerShowcase() = shot("reader", windowHeightDp = 915, windowWidthDp = 412) {
        ReaderShowcase(Modifier.height(915.dp))
    }

    @Test
    fun readerShowcaseAyatAlKursiWithRankReached() = shot("reader_kursi", windowHeightDp = 915, windowWidthDp = 412) {
        ReaderShowcase(Modifier.height(915.dp), sample = ReaderShowcaseSample.AyatAlKursi)
    }

    companion object {
        private const val TAG = "shot"

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
