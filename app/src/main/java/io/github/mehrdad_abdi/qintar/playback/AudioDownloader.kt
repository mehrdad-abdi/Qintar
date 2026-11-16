package io.github.mehrdad_abdi.qintar.playback

import io.github.mehrdad_abdi.qintar.di.ApplicationScope
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data object Idle : DownloadState

    data class Running(val done: Int, val total: Int) : DownloadState

    /** Everything asked for is stored. */
    data class Finished(val total: Int) : DownloadState

    /** Not started: [WIFI_ONLY] = on a metered connection while "Wi-Fi only" is on. */
    data class Blocked(val reason: Reason) : DownloadState {
        enum class Reason { OFFLINE, WIFI_ONLY }
    }

    /** Stopped at a file that could not be loaded; [done] files are stored. */
    data class Failed(val done: Int, val total: Int) : DownloadState
}

/** "Download for offline", as the reader sees it. One download at a time. */
interface AudioDownloads {
    val state: StateFlow<DownloadState>

    fun download(ranges: List<AyahRange>)
    fun cancel()

    /** The outcome has been shown. */
    fun acknowledge()
}

/**
 * Stores the recitation of whole passages in the audio cache, in the reciter and quality of the
 * moment (§7.8). Honours "Wi-Fi only": on a metered connection nothing is loaded.
 */
@Singleton
class AudioDownloader @Inject constructor(
    private val fetcher: AudioFetcher,
    private val network: NetworkStatus,
    private val prefs: PrefsStore,
    private val source: AudioSource,
    @ApplicationScope private val scope: CoroutineScope,
) : AudioDownloads {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    override val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var job: Job? = null

    override fun download(ranges: List<AyahRange>) {
        job?.cancel()
        job = scope.launch {
            val settings = prefs.prefs.first()
            if (!network.isOnline()) {
                _state.value = DownloadState.Blocked(DownloadState.Blocked.Reason.OFFLINE)
                return@launch
            }
            if (settings.wifiOnlyDownloads && !network.isUnmetered()) {
                _state.value = DownloadState.Blocked(DownloadState.Blocked.Reason.WIFI_ONLY)
                return@launch
            }
            // 1:1 is also the basmala that the player puts before other surahs.
            val ayahs = (listOf(AyahId.FIRST) + ranges.flatMap { it }).distinct()
            _state.value = DownloadState.Running(0, ayahs.size)
            ayahs.forEachIndexed { i, ayah ->
                ensureActive()
                try {
                    fetcher.fetch(source.url(settings.reciterId, ayah, settings.audioQuality))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _state.value = DownloadState.Failed(i, ayahs.size)
                    return@launch
                }
                _state.value = DownloadState.Running(i + 1, ayahs.size)
            }
            _state.value = DownloadState.Finished(ayahs.size)
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        _state.value = DownloadState.Idle
    }

    override fun acknowledge() {
        if (_state.value !is DownloadState.Running) _state.value = DownloadState.Idle
    }
}
