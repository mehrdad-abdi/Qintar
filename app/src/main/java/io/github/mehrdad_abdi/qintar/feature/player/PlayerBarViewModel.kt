package io.github.mehrdad_abdi.qintar.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.playback.PlaybackOrigin
import io.github.mehrdad_abdi.qintar.playback.PlaybackProblem
import io.github.mehrdad_abdi.qintar.playback.PlayerController
import io.github.mehrdad_abdi.qintar.playback.RepeatSettings
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerBarState(
    /** Something is loaded in the player. */
    val visible: Boolean = false,
    val surah: SurahInfo? = null,
    val ayahNumber: Int = 0,
    val basmala: Boolean = false,
    val dhikr: Boolean = false,
    val reciterId: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val speed: Float = 1f,
    val repeat: RepeatSettings = RepeatSettings(),
    val problem: PlaybackProblem? = null,
    val origin: PlaybackOrigin? = null,
) {
    /** The reader the queue came from; it opens at the ayah being played. */
    val returnRoute: ReaderRoute? get() = origin?.route()
}

/**
 * The mini player (§5.4, §7.8), in the reader and above the tabs: what is playing, and its controls.
 * The playback speed is a setting; the service follows it.
 */
@HiltViewModel
class PlayerBarViewModel @Inject constructor(
    private val player: PlayerController,
    private val prefs: PrefsStore,
    private val index: Provider<QuranIndex>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerBarState())
    val state: StateFlow<PlayerBarState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(player.state, prefs.prefs) { p, settings -> p to settings.playbackSpeed }.collect { (p, speed) ->
                val item = p.item
                val index = if (item != null) withContext(io) { index.get() } else null
                _state.update {
                    PlayerBarState(
                        visible = p.active,
                        surah = item?.let { index?.surah(it.surah) },
                        ayahNumber = item?.let { index?.ref(it.ayah)?.ayah } ?: 0,
                        basmala = item?.basmala == true,
                        dhikr = item?.dhikr == true,
                        reciterId = p.reciterId,
                        playing = p.playing,
                        loading = p.loading,
                        speed = speed,
                        repeat = p.repeat,
                        problem = p.problem,
                        origin = p.origin,
                    )
                }
            }
        }
    }

    fun onPlayPause() = player.togglePlayPause()
    fun onNext() = player.next()
    fun onPrevious() = player.previous()
    fun onClose() = player.close()

    /** The speed button steps through [SPEEDS] and starts over. */
    fun onSpeed() {
        val current = _state.value.speed
        val at = SPEEDS.indexOfFirst { abs(it - current) < 0.01f }
        val next = SPEEDS[(at + 1) % SPEEDS.size]
        viewModelScope.launch { prefs.update { it.copy(playbackSpeed = next) } }
    }

    fun onRepeatAyah(times: Int) = player.setRepeat(_state.value.repeat.copy(ayahTimes = times.coerceAtLeast(1)))

    fun onRepeatPassage(on: Boolean) = player.setRepeat(_state.value.repeat.copy(passage = on))

    companion object {
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val AYAH_REPEATS = listOf(1, 2, 3, 5, 10)
    }
}
