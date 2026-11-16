package io.github.mehrdad_abdi.qintar.di

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import io.github.mehrdad_abdi.qintar.HiltTestActivity
import io.github.mehrdad_abdi.qintar.QintarApp
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.service.DefaultReadingTracker
import io.github.mehrdad_abdi.qintar.feature.today.TodayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Builds the production Hilt graph (real Room databases, DataStore, the bundled Quran) under
 * Robolectric: a missing or duplicated binding fails here rather than at app start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = QintarApp::class)
class HiltGraphTest {
    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun `the graph provides one tracker behind both interfaces, one clock, and a working view model`() {
        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.tracker is DefaultReadingTracker)
                assertSame("the log has exactly one writer", activity.tracker, activity.importer)
                assertSame(activity.clock, activity.clockAgain)

                val today = ViewModelProvider(activity)[TodayViewModel::class.java]
                waitFor("Today to load") { !today.state.value.loading }
                assertEquals(activity.clock.today(), today.state.value.day)
                assertEquals(0, today.state.value.rank.count)

                // A reading written through the injected tracker reaches the view model through Room.
                runBlocking { activity.tracker.mark(AyahId(262), ReadContext.Browse, ReadMethod.TAP) }
                waitFor("the reading to show") { today.state.value.rank.count == 1 }
                assertFalse(today.state.value.loading)
            }
        }
    }
}
