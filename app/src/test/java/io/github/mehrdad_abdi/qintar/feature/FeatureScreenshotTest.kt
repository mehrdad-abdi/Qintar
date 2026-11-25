package io.github.mehrdad_abdi.qintar.feature

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
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.domain.testing.FakeQuranTextSource
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmDetailScreen
import io.github.mehrdad_abdi.qintar.feature.khatm.NewKhatmScreen
import io.github.mehrdad_abdi.qintar.feature.read.ReadScreen
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderScreen
import io.github.mehrdad_abdi.qintar.feature.today.TodayScreen
import io.github.mehrdad_abdi.qintar.testing.khatmDetailViewModel
import io.github.mehrdad_abdi.qintar.testing.newKhatmViewModel
import io.github.mehrdad_abdi.qintar.testing.readViewModel
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.testing.todayViewModel
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.designsystem.showcase.SampleAyahs
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The real Today and Reader screens over the in-memory domain, in light and dark, English and
 * Persian. Surah names are the fixture's ("Surah 8"); the Quran text of al-Anfal 1–3 is real.
 *
 * Record with `./gradlew :app:recordRoborazziDebug`; images are in `app/src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class FeatureScreenshotTest(private val language: String, private val dark: Boolean) {
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

    @Test
    fun todayMidDay() {
        runBlocking {
            // Two earlier days of reading, then 47 ayahs today, 21 of them in a khatm with a daily goal.
            app.tracker.markRange(AyahRange.of(1, 120), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
            app.clock.advanceDays(1)
            app.tracker.markRange(AyahRange.of(1, 60), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
            app.clock.advanceDays(1)
            val khatm = app.khatms.start("Ramadan khatm", startFrom = app.ayah(8, 1), dailyPages = 4f)
            app.tracker.markRange(AyahRange(app.ayah(8, 1), app.ayah(8, 21)), ReadContext.Khatm(khatm.id), ReadMethod.BULK, RangeMode.FILL)
            app.tracker.markRange(AyahRange.of(1, 26), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
            val morning = app.library.createGroup("Morning", schedule = Schedule.daily(TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0))))
            app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(groupId = morning.id))
            app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = morning.id, repeatTarget = 3))
            app.library.create(BookmarkDrafts.surah(18, app.index).copy(schedule = Schedule.daily()))
        }
        val viewModel = app.todayViewModel(Dispatchers.Unconfined)
        shot("feature_today") { TodayScreen(onNavigate = {}, viewModel = viewModel) }
    }

    @Test
    fun todayEmpty() {
        val viewModel = app.todayViewModel(Dispatchers.Unconfined)
        shot("feature_today_empty") { TodayScreen(onNavigate = {}, viewModel = viewModel) }
    }

    @Test
    fun readerKhatm() {
        val text = FakeQuranTextSource(
            app.index,
            mapOf(
                app.ayah(8, 1) to SampleAyahs.ANFAL_1,
                app.ayah(8, 2) to SampleAyahs.ANFAL_2,
                app.ayah(8, 3) to SampleAyahs.ANFAL_3,
            ),
        )
        val route = runBlocking {
            val khatm = app.khatms.start("Ramadan khatm", startFrom = app.ayah(8, 1))
            val context = ReadContext.Khatm(khatm.id)
            app.tracker.markRange(AyahRange.of(1, 45), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
            app.tracker.mark(app.ayah(8, 1), context, ReadMethod.TAP)
            app.tracker.mark(app.ayah(8, 1), context, ReadMethod.TAP)
            app.library.toggleStar(app.ayah(8, 2))
            ReaderRoute.khatm(khatm.id, startAyahId = app.ayah(8, 1).value - 1)
        }
        val viewModel = app.readerViewModel(route, Dispatchers.Unconfined, text)
        shot("feature_reader") { ReaderScreen(route, onBack = {}, onNavigate = {}, viewModel = viewModel) }
    }

    @Test
    fun readTab() {
        runBlocking {
            app.tracker.markRange(app.index.surahRange(112), ReadContext.Browse, ReadMethod.BULK, RangeMode.FILL)
            app.tracker.markRange(AyahRange.of(1, 140), ReadContext.Browse, ReadMethod.BULK, RangeMode.FILL)
            app.prefs.update { it.copy(lastBrowseAyahId = app.ayah(18, 10)) }
            val ramadan = app.khatms.start("Ramadan khatm", dailyPages = 4f)
            app.tracker.markRange(AyahRange.of(1, 420), ReadContext.Khatm(ramadan.id), ReadMethod.BULK, RangeMode.FILL)
            app.clock.advanceMinutes(5)
            app.khatms.start("Personal khatm", startFrom = app.ayah(18, 1))
        }
        val viewModel = app.readViewModel(Dispatchers.Unconfined)
        shot("feature_read") { ReadScreen(onNavigate = {}, viewModel = viewModel) }
    }

    @Test
    fun khatmDetail() {
        val khatm = runBlocking {
            val khatm = app.khatms.start("Ramadan khatm", dailyPages = 4f)
            val context = ReadContext.Khatm(khatm.id)
            app.tracker.markRange(AyahRange.of(1, 900), context, ReadMethod.BULK, RangeMode.FILL)
            app.clock.advanceDays(1)
            app.tracker.markRange(AyahRange.of(901, 1400), context, ReadMethod.BULK, RangeMode.FILL)
            app.clock.advanceDays(1)
            app.tracker.markRange(AyahRange.of(1401, 1650), context, ReadMethod.BULK, RangeMode.FILL)
            app.tracker.markRange(AyahRange.of(3000, 3100), context, ReadMethod.BULK, RangeMode.FILL)
            khatm
        }
        val viewModel = app.khatmDetailViewModel(khatm.id, Dispatchers.Unconfined)
        shot("feature_khatm_detail") { KhatmDetailScreen(KhatmDetailRoute(khatm.id), onBack = {}, onNavigate = {}, viewModel = viewModel) }
    }

    @Test
    fun khatmDetailHijri() {
        // R-SET-5: long Hijri month names in the tiles and the list.
        val khatm = runBlocking {
            val khatm = app.khatms.start("Ramadan khatm", dailyPages = 4f)
            app.tracker.markRange(AyahRange.of(1, 900), ReadContext.Khatm(khatm.id), ReadMethod.BULK, RangeMode.FILL)
            khatm
        }
        val viewModel = app.khatmDetailViewModel(khatm.id, Dispatchers.Unconfined)
        val dates = io.github.mehrdad_abdi.qintar.domain.service.CalendarDates(io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem.ISLAMIC)
        shot("feature_khatm_detail_hijri") {
            androidx.compose.runtime.CompositionLocalProvider(io.github.mehrdad_abdi.qintar.ui.common.LocalCalendarDates provides dates) {
                KhatmDetailScreen(KhatmDetailRoute(khatm.id), onBack = {}, onNavigate = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun newKhatmForm() {
        runBlocking { app.khatms.start("Ramadan khatm") }
        val viewModel = app.newKhatmViewModel(Dispatchers.Unconfined)
        viewModel.onStartText("2:255")
        viewModel.onDailyText("4")
        shot("feature_new_khatm") { NewKhatmScreen(onBack = {}, onNavigate = {}, viewModel = viewModel) }
    }

    @Test
    fun readerBookmark() {
        val text = FakeQuranTextSource(app.index, mapOf(app.ayah(112, 1) to SampleAyahs.ANFAL_1, app.ayah(112, 2) to SampleAyahs.ANFAL_2))
        val route = runBlocking {
            val bookmark = app.library.create(
                BookmarkDrafts.surah(112, app.index).copy(
                    label = "Surat al-Ikhlas",
                    description = "Narrated: it is equal to a third of the Quran.",
                    repeatTarget = 3,
                ),
            )
            app.tracker.markRange(app.index.surahRange(112), ReadContext.Bookmark(bookmark.id), ReadMethod.BULK, RangeMode.ADD)
            ReaderRoute.bookmark(bookmark.id)
        }
        val viewModel = app.readerViewModel(route, Dispatchers.Unconfined, text)
        shot("feature_reader_bookmark") { ReaderScreen(route, onBack = {}, onNavigate = {}, viewModel = viewModel) }
    }

    companion object {
        private const val TAG = "shot"
        private const val HEIGHT_DP = 915

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
