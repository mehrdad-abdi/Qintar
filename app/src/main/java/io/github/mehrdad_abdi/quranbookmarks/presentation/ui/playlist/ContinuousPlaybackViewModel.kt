package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.playlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.GetProfileByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetProfilePlaylistUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.PlaylistVerse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContinuousPlaybackUiState(
    val profile: BookmarkGroup? = null,
    val playlist: List<PlaylistVerse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPlayingAudio: Boolean = false,
    val currentPlayingIndex: Int? = null,
    val reciters: List<ReciterData> = emptyList(),
    val selectedReciter: ReciterData? = null,
    val showReciterSelection: Boolean = false,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.SPEED_1
)

@HiltViewModel
class ContinuousPlaybackViewModel @Inject constructor(
    private val getProfileByIdUseCase: GetProfileByIdUseCase,
    private val getProfilePlaylistUseCase: GetProfilePlaylistUseCase,
    private val quranRepository: QuranRepository,
    private val audioService: AudioService
) : ViewModel() {

    companion object {
        private const val TAG = "ContinuousPlayback"
    }

    private val _uiState = MutableStateFlow(ContinuousPlaybackUiState())
    private var lastCompletedUrl: String? = null // Track last completed URL to avoid duplicate handling

    val uiState: StateFlow<ContinuousPlaybackUiState> = combine(
        _uiState,
        audioService.playbackState
    ) { uiState, audioState ->
        // Handle auto-play next ayah when current one completes
        if (audioState.completedUrl != null && audioState.completedUrl != lastCompletedUrl) {
            lastCompletedUrl = audioState.completedUrl
            handleAudioCompletion(audioState.completedUrl)
        }

        uiState.copy(
            isPlayingAudio = audioState.isPlaying,
            currentPlayingIndex = if (audioState.isPlaying) uiState.currentPlayingIndex else null,
            playbackSpeed = PlaybackSpeed.fromValue(audioState.playbackSpeed)
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = ContinuousPlaybackUiState()
    )

    fun loadPlaylist(profileId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Load profile details
                val profile = getProfileByIdUseCase(profileId)
                if (profile == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Profile not found"
                    )
                    return@launch
                }

                Log.d(TAG, "Loaded profile: ${profile.name}")

                // Load playlist
                val playlistResult = getProfilePlaylistUseCase(profileId)
                if (playlistResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = playlistResult.exceptionOrNull()?.message ?: "Failed to load playlist"
                    )
                    return@launch
                }

                val playlist = playlistResult.getOrThrow()
                Log.d(TAG, "Loaded playlist with ${playlist.size} verses")

                _uiState.value = _uiState.value.copy(
                    profile = profile,
                    playlist = playlist,
                    isLoading = false,
                    error = null
                )

                // Load reciters
                loadReciters(profile.reciterEdition)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlist", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load playlist"
                )
            }
        }
    }

    private fun loadReciters(defaultReciterId: String) {
        viewModelScope.launch {
            try {
                val recitersResult = quranRepository.getAvailableReciters()
                if (recitersResult.isSuccess) {
                    val reciters = recitersResult.getOrThrow()
                    val defaultReciter = reciters.find { it.identifier == defaultReciterId }
                        ?: reciters.find { it.identifier == "ar.alafasy" }
                        ?: reciters.firstOrNull()

                    _uiState.value = _uiState.value.copy(
                        reciters = reciters,
                        selectedReciter = defaultReciter
                    )
                    Log.d(TAG, "Loaded reciters, selected: ${defaultReciter?.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading reciters", e)
                // Reciters loading failure shouldn't break the main functionality
            }
        }
    }

    fun toggleReciterSelection() {
        _uiState.value = _uiState.value.copy(
            showReciterSelection = !_uiState.value.showReciterSelection
        )
    }

    fun selectReciter(reciter: ReciterData) {
        _uiState.value = _uiState.value.copy(
            selectedReciter = reciter,
            showReciterSelection = false
        )
        Log.d(TAG, "Selected reciter: ${reciter.name}")
    }

    fun togglePlayback(startIndex: Int = 0) {
        val currentState = _uiState.value
        val audioState = audioService.playbackState.value

        if (audioState.isPlaying) {
            // Pause playback
            Log.d(TAG, "Pausing playback")
            audioService.pause()
        } else {
            // Start or resume playback
            val playingIndex = currentState.currentPlayingIndex ?: startIndex
            Log.d(TAG, "Starting playback at index $playingIndex")
            playAudioAtIndex(playingIndex)
        }
    }

    fun playAudioAtIndex(index: Int) {
        val currentState = _uiState.value

        if (index < 0 || index >= currentState.playlist.size) {
            Log.w(TAG, "Invalid playlist index: $index")
            return
        }

        val audioUrl = getAudioUrl(index)
        if (audioUrl != null) {
            Log.d(TAG, "Playing verse at index $index: $audioUrl")
            _uiState.value = currentState.copy(currentPlayingIndex = index)
            audioService.playAudio(audioUrl)
        } else {
            Log.e(TAG, "Unable to get audio URL for verse at index $index")
            _uiState.value = currentState.copy(
                error = "Unable to get audio URL for this verse"
            )
        }
    }

    fun getAudioUrl(index: Int): String? {
        val state = _uiState.value
        val reciter = state.selectedReciter
        val playlistVerse = state.playlist.getOrNull(index)

        return if (reciter != null && playlistVerse != null) {
            val verse = playlistVerse.verse

            // Check if we have a cached audio file first
            if (verse.cachedAudioPath != null) {
                Log.d(TAG, "Using cached audio file for index $index: ${verse.cachedAudioPath}")
                return verse.cachedAudioPath
            }

            // Fall back to streaming URL
            val globalAyahNumber = verse.globalAyahNumber
            val audioUrl = quranRepository.getAudioUrl(reciter.identifier, globalAyahNumber, "128")
            Log.d(TAG, "Using streaming audio URL for index $index (global ayah $globalAyahNumber): $audioUrl")
            audioUrl
        } else {
            Log.e(TAG, "Cannot generate audio URL - reciter: $reciter, verse exists: ${playlistVerse != null}")
            null
        }
    }

    fun getImageUrl(index: Int, highRes: Boolean = false): String? {
        val playlistVerse = _uiState.value.playlist.getOrNull(index)
        return if (playlistVerse != null) {
            val verse = playlistVerse.verse

            // Check if we have a cached image file first
            if (verse.cachedImagePath != null && !highRes) {
                Log.d(TAG, "Using cached image file for index $index: ${verse.cachedImagePath}")
                return verse.cachedImagePath
            }

            // Fall back to URL for images (or if highRes is requested)
            val surahNumber = extractSurahNumber(verse)
            val ayahNumber = extractAyahNumber(verse)
            if (surahNumber != null && ayahNumber != null) {
                quranRepository.getImageUrl(surahNumber, ayahNumber, highRes)
            } else null
        } else null
    }

    private fun extractSurahNumber(verse: io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata): Int? {
        // Placeholder - should be in VerseMetadata
        return 1
    }

    private fun extractAyahNumber(verse: io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata): Int? {
        // Placeholder - should be in VerseMetadata
        return 1
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        audioService.setPlaybackSpeed(speed)
        Log.d(TAG, "Playback speed changed to ${speed.displayText}")
    }

    fun getCurrentPlaybackSpeed(): PlaybackSpeed {
        return audioService.getCurrentPlaybackSpeed()
    }

    private fun handleAudioCompletion(completedUrl: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val currentPlayingIndex = currentState.currentPlayingIndex

                Log.d(TAG, "Audio completed: $completedUrl, current index: $currentPlayingIndex")

                if (currentPlayingIndex != null) {
                    val nextIndex = currentPlayingIndex + 1

                    // Check if there's a next verse available
                    if (nextIndex < currentState.playlist.size) {
                        Log.d(TAG, "Auto-playing next verse: $nextIndex")

                        // Clear the completion state first
                        audioService.clearCompletion()

                        // Play the next verse
                        playAudioAtIndex(nextIndex)
                    } else {
                        Log.d(TAG, "Reached end of playlist, stopping auto-play")
                        // Stop playback and clear state
                        audioService.stop()
                        audioService.clearCompletion()
                        lastCompletedUrl = null
                        _uiState.value = currentState.copy(currentPlayingIndex = null)
                    }
                } else {
                    // Just clear the completion state if no current index
                    audioService.clearCompletion()
                    lastCompletedUrl = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleAudioCompletion", e)
                audioService.clearCompletion()
                lastCompletedUrl = null
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
        audioService.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        audioService.release()
    }
}