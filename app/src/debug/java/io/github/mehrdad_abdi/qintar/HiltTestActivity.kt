package io.github.mehrdad_abdi.qintar

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.mehrdad_abdi.qintar.domain.service.ReadingLogImporter
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import javax.inject.Inject

/**
 * An empty Hilt-enabled activity for tests that set their own Compose content but need
 * `hiltViewModel()` to work against the real graph, and for the graph test, which reads the
 * injected fields. Debug builds only; not exported.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity() {
    @Inject lateinit var tracker: ReadingTracker
    @Inject lateinit var importer: ReadingLogImporter
    @Inject lateinit var clock: DayClock
    @Inject lateinit var clockAgain: DayClock
}
