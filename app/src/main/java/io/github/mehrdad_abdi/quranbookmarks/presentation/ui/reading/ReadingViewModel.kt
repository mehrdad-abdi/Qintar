package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetBookmarkContentUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.util.Log
import javax.inject.Inject

data class ReadingUiState(
    val bookmark: Bookmark? = null,
    val verses: List<VerseMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPlayingAudio: Boolean = false,
    val currentPlayingVerse: Int? = null,
    val reciters: List<ReciterData> = emptyList(),
    val selectedReciter: ReciterData? = null,
    val showReciterSelection: Boolean = false,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.SPEED_1
)

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val getBookmarkByIdUseCase: GetBookmarkByIdUseCase,
    private val getBookmarkContentUseCase: GetBookmarkContentUseCase,
    private val quranRepository: QuranRepository,
    private val audioService: AudioService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = combine(
        _uiState,
        audioService.playbackState
    ) { uiState, audioState ->
        // Handle auto-play next ayah when current one completes
        if (audioState.completedUrl != null) {
            handleAudioCompletion(audioState.completedUrl)
        }

        uiState.copy(
            isPlayingAudio = audioState.isPlaying,
            currentPlayingVerse = if (audioState.isPlaying) uiState.currentPlayingVerse else null,
            playbackSpeed = PlaybackSpeed.fromValue(audioState.playbackSpeed)
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = ReadingUiState()
    )

    fun loadBookmarkContent(bookmarkId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // First, get the bookmark
                val bookmarkResult = getBookmarkByIdUseCase(bookmarkId)
                if (bookmarkResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = bookmarkResult.exceptionOrNull()?.message ?: "Failed to load bookmark"
                    )
                    return@launch
                }

                val bookmark = bookmarkResult.getOrThrow()

                // Then, get the content
                val contentResult = getBookmarkContentUseCase(bookmark)
                if (contentResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = contentResult.exceptionOrNull()?.message ?: "Failed to load content"
                    )
                    return@launch
                }

                val verses = contentResult.getOrThrow()

                _uiState.value = _uiState.value.copy(
                    bookmark = bookmark,
                    verses = verses,
                    isLoading = false,
                    error = null
                )

                // Load reciters for audio playback
                loadReciters()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load bookmark content"
                )
            }
        }
    }

    private fun loadReciters() {
        viewModelScope.launch {
            try {
                val recitersResult = quranRepository.getAvailableReciters()
                if (recitersResult.isSuccess) {
                    val reciters = recitersResult.getOrThrow()
                    val defaultReciter = reciters.find { it.identifier == "ar.alafasy" } ?: reciters.firstOrNull()

                    _uiState.value = _uiState.value.copy(
                        reciters = reciters,
                        selectedReciter = defaultReciter
                    )
                }
            } catch (e: Exception) {
                // Reciters loading failure shouldn't break the main functionality
                // Just log it silently
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
    }

    fun toggleAudioPlayback(verseIndex: Int? = null) {
        val currentState = _uiState.value
        val audioState = audioService.playbackState.value

        if (audioState.isPlaying) {
            // Pause playback
            audioService.pause()
        } else {
            // Start playback
            val playingIndex = verseIndex ?: 0
            val audioUrl = getAudioUrl(playingIndex)

            if (audioUrl != null) {
                _uiState.value = currentState.copy(currentPlayingVerse = playingIndex)
                audioService.playAudio(audioUrl)
            } else {
                _uiState.value = currentState.copy(
                    error = "Unable to get audio URL for this verse"
                )
            }
        }
    }

    fun getAudioUrl(verseIndex: Int): String? {
        val state = _uiState.value
        val reciter = state.selectedReciter
        val verse = state.verses.getOrNull(verseIndex)

        return if (reciter != null && verse != null) {
            // Check if we have a cached audio file first
            if (verse.cachedAudioPath != null) {
                Log.d("ReadingViewModel", "Using cached audio file for verse $verseIndex: ${verse.cachedAudioPath}")
                return verse.cachedAudioPath
            }

            // Fall back to streaming URL
            val globalAyahNumber = verse.globalAyahNumber
            val audioUrl = quranRepository.getAudioUrl(reciter.identifier, globalAyahNumber, "128")
            Log.d("ReadingViewModel", "Using streaming audio URL for verse $verseIndex (global ayah $globalAyahNumber, reciter ${reciter.identifier}): $audioUrl")
            audioUrl
        } else {
            Log.e("ReadingViewModel", "Cannot generate audio URL - reciter: $reciter, verse: $verse")
            null
        }
    }

    fun getImageUrl(verseIndex: Int, highRes: Boolean = false): String? {
        val verse = _uiState.value.verses.getOrNull(verseIndex)
        return if (verse != null) {
            // Check if we have a cached image file first
            if (verse.cachedImagePath != null && !highRes) {
                Log.d("ReadingViewModel", "Using cached image file for verse $verseIndex: ${verse.cachedImagePath}")
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

    // No longer needed - using globalAyahNumber from API response
    // private fun calculateGlobalAyahNumber(verse: VerseMetadata): Int

    private fun extractSurahNumber(verse: VerseMetadata): Int? {
        // This is a placeholder - in a real app, VerseMetadata would include surah number
        // For now, we'll return 1 as a fallback
        return 1
    }

    private fun extractAyahNumber(verse: VerseMetadata): Int? {
        // This is a placeholder - in a real app, VerseMetadata would include ayah number
        // For now, we'll return 1 as a fallback
        return 1
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        audioService.setPlaybackSpeed(speed)
        Log.d("ReadingViewModel", "Playback speed changed to ${speed.displayText}")
    }

    fun getCurrentPlaybackSpeed(): PlaybackSpeed {
        return audioService.getCurrentPlaybackSpeed()
    }

    private fun handleAudioCompletion(completedUrl: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val currentPlayingVerse = currentState.currentPlayingVerse

                Log.d("ReadingViewModel", "Audio completed: $completedUrl, current verse: $currentPlayingVerse")

                if (currentPlayingVerse != null) {
                    val nextVerseIndex = currentPlayingVerse + 1

                    // Check if there's a next verse available
                    if (nextVerseIndex < currentState.verses.size) {
                        Log.d("ReadingViewModel", "Auto-playing next verse: $nextVerseIndex")

                        // Clear the completion state first
                        audioService.clearCompletion()

                        // Play the next verse
                        val nextAudioUrl = getAudioUrl(nextVerseIndex)
                        if (nextAudioUrl != null) {
                            _uiState.value = currentState.copy(currentPlayingVerse = nextVerseIndex)
                            audioService.playAudio(nextAudioUrl)
                        } else {
                            Log.e("ReadingViewModel", "Failed to get audio URL for next verse: $nextVerseIndex")
                        }
                    } else {
                        Log.d("ReadingViewModel", "Reached end of verses, stopping auto-play")
                        // Clear completion since we've reached the end
                        audioService.clearCompletion()
                    }
                } else {
                    // Just clear the completion state if no current verse
                    audioService.clearCompletion()
                }
            } catch (e: Exception) {
                Log.e("ReadingViewModel", "Error in handleAudioCompletion", e)
                audioService.clearCompletion()
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