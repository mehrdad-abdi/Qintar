package io.github.mehrdad_abdi.qintar.feature.bookmarks

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
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.domain.testing.FakeQuranTextSource
import io.github.mehrdad_abdi.qintar.testing.bookmarkEditorViewModel
import io.github.mehrdad_abdi.qintar.testing.bookmarksViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.SampleAyahs
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Bookmarks tab (Now, Groups) and the bookmark editor with its text preview, in light and dark,
 * English and Persian. Record with `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class BookmarksScreenshotTest(private val language: String, private val dark: Boolean) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = DomainFixture()

    private fun shot(name: String, content: @Composable () -> Unit) {
        val night = if (dark) "night" else "notnight"
        RuntimeEnvironment.setQualifiers("$language-w412dp-h${HEIGHT_DP}dp-$night-xhdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    QintarTheme(darkTheme = dark) { Box(Modifier.height(HEIGHT_DP.dp).testTag(TAG)) { content() } }
                }
            }
            val theme = if (dark) "dark" else "light"
            compose.onNodeWithTag(TAG).captureRoboImage("src/test/screenshots/${name}_${theme}_$language.png")
        }
    }

    private fun seed() = runBlocking {
        val morning = app.library.createGroup(
            "Morning",
            "After fajr",
            Schedule.daily(TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0))),
        )
        val kursi = app.library.create(
            BookmarkDrafts.ayah(app.ayah(2, 255)).copy(
                groupId = morning.id,
                description = "Whoever reads it after every prescribed prayer, nothing stands between him and Paradise.",
                repeatTarget = 3,
            ),
        )
        app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = morning.id, description = "A third of the Quran."))
        app.tracker.markRange(kursi.range, ReadContext.Bookmark(kursi.id), ReadMethod.BULK, RangeMode.ADD)
        app.library.create(
            BookmarkDrafts.surah(18, app.index).copy(
                label = "Friday",
                description = "Light between the two Fridays.",
                schedule = Schedule(setOf(DayOfWeek.SUNDAY), null),
            ),
        )
        app.library.create(BookmarkDrafts.page(293, app.index).copy(schedule = Schedule.daily(TimeWindow(LocalTime.of(20, 0), LocalTime.of(22, 0)))))
        app.library.create(BookmarkDrafts.juz(30, app.index))
        app.library.createGroup("Night")
    }

    @Test
    fun bookmarksNow() {
        seed()
        val vm = app.bookmarksViewModel(Dispatchers.Unconfined)
        shot("feature_bookmarks_now") { BookmarksScreen(onNavigate = {}, viewModel = vm) }
    }

    @Test
    fun bookmarksGroups() {
        seed()
        val vm = app.bookmarksViewModel(Dispatchers.Unconfined)
        vm.onView(BookmarksView.GROUPS)
        shot("feature_bookmarks_groups") { BookmarksScreen(onNavigate = {}, viewModel = vm) }
    }

    @Test
    fun bookmarkEditor() {
        val text = FakeQuranTextSource(
            app.index,
            mapOf(
                app.ayah(8, 1) to SampleAyahs.ANFAL_1,
                app.ayah(8, 2) to SampleAyahs.ANFAL_2,
                app.ayah(8, 3) to SampleAyahs.ANFAL_3,
            ),
        )
        val vm = app.bookmarkEditorViewModel(
            BookmarkEditorRoute(
                draftFirstAyahId = app.ayah(8, 1).value,
                draftLastAyahId = app.ayah(8, 12).value,
                draftKind = BookmarkKind.RANGE.name,
            ),
            Dispatchers.Unconfined,
            text,
        )
        vm.onLabel("Al-Anfal opening")
        vm.onDescription("The ayahs I recite before a journey.")
        shot("feature_bookmark_editor") {
            BookmarkEditorScreen(BookmarkEditorRoute(), onBack = {}, onNavigate = {}, viewModel = vm)
        }
    }

    private companion object {
        const val TAG = "shot"
        const val HEIGHT_DP = 915

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
