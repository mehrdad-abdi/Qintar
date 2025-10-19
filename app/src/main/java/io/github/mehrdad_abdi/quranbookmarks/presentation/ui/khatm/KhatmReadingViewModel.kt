package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.khatm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.khatm.GetKhatmProgressUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.khatm.UpdateKhatmProgressUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetTodayStatsUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.ToggleAyahTrackingUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KhatmReadingUiState(
    val currentPage: Int = 1,
    val verses: List<VerseMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPlayingAudio: Boolean = false,
    val currentPlayingIndex: Int? = null, // Global index within page verses
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.SPEED_1,
    val showJumpDialog: Boolean = false,
    val readAyahIds: Set<String> = emptySet() // Ayahs read today
)

@HiltViewModel
class KhatmReadingViewModel @Inject constructor(
    private val getKhatmProgressUseCase: GetKhatmProgressUseCase,
    private val updateKhatmProgressUseCase: UpdateKhatmProgressUseCase,
    private val quranRepository: QuranRepository,
    private val settingsRepository: SettingsRepository,
    private val audioService: AudioService,
    private val getTodayStatsUseCase: GetTodayStatsUseCase,
    private val toggleAyahTrackingUseCase: ToggleAyahTrackingUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "KhatmReadingVM"
        private const val KHATM_BOOKMARK_ID = -1L // Use -1 like random reading
    }

    private val _uiState = MutableStateFlow(KhatmReadingUiState())
    private var lastCompletedUrl: String? = null

    val uiState: StateFlow<KhatmReadingUiState> = combine(
        _uiState,
        audioService.playbackState,
        getTodayStatsUseCase()
    ) { uiState, audioState, todayStats ->
        // Handle auto-play next ayah when current one completes
        if (audioState.completedUrl != null &&
            audioState.completedUrl != lastCompletedUrl &&
            !audioState.isPlaying) {
            lastCompletedUrl = audioState.completedUrl
            handleAudioCompletion()
        }

        uiState.copy(
            isPlayingAudio = audioState.isPlaying,
            currentPlayingIndex = if (audioState.isPlaying) uiState.currentPlayingIndex else null,
            playbackSpeed = PlaybackSpeed.fromValue(audioState.playbackSpeed),
            readAyahIds = todayStats.trackedAyahIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KhatmReadingUiState()
    )

    init {
        loadLastPage()
    }

    private fun loadLastPage() {
        viewModelScope.launch {
            try {
                val progress = getKhatmProgressUseCase.getSnapshot()
                loadPage(progress.lastPageRead)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading last page", e)
                loadPage(1) // Default to page 1
            }
        }
    }

    fun loadPage(pageNumber: Int) {
        if (pageNumber !in 1..KhatmProgress.TOTAL_PAGES) {
            Log.e(TAG, "Invalid page number: $pageNumber")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentPage = pageNumber
            )

            try {
                // Load verses for this page (don't save progress until ayah is marked)
                val result = quranRepository.getPageMetadata(pageNumber)
                if (result.isSuccess) {
                    val verses = result.getOrThrow()
                    Log.d(TAG, "Loaded ${verses.size} verses for page $pageNumber")
                    _uiState.value = _uiState.value.copy(
                        verses = verses,
                        isLoading = false,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load page"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading page $pageNumber", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load page"
                )
            }
        }
    }

    fun playAudioAtIndex(verseIndex: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val verses = currentState.verses

            if (verseIndex !in verses.indices) {
                Log.w(TAG, "Invalid verse index: $verseIndex")
                return@launch
            }

            val verse = verses[verseIndex]
            Log.d(TAG, "Playing audio for verse $verseIndex: Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}")

            val audioUrl = getAudioUrl(verse)
            if (audioUrl != null) {
                _uiState.value = currentState.copy(currentPlayingIndex = verseIndex)
                audioService.playAudio(audioUrl)
            } else {
                Log.e(TAG, "Unable to get audio URL for verse")
                _uiState.value = currentState.copy(error = "Unable to get audio URL")
            }
        }
    }

    fun togglePlayback() {
        val audioState = audioService.playbackState.value

        if (audioState.isPlaying) {
            Log.d(TAG, "Pausing playback")
            audioService.pause()
        } else {
            // Start playing from first verse or current playing index
            val startIndex = _uiState.value.currentPlayingIndex ?: 0
            playAudioAtIndex(startIndex)
        }
    }

    private suspend fun getAudioUrl(verse: VerseMetadata): String? {
        return try {
            // Check if we have cached audio first
            if (verse.cachedAudioPath != null) {
                Log.d(TAG, "Using cached audio: ${verse.cachedAudioPath}")
                return verse.cachedAudioPath
            }

            // Fall back to streaming URL using settings
            val settings = settingsRepository.getSettings().stateIn(viewModelScope).value
            val audioUrl = quranRepository.getAudioUrl(
                reciter = settings.reciterEdition,
                ayahNumber = verse.globalAyahNumber,
                bitrate = settings.reciterBitrate
            )
            Log.d(TAG, "Using streaming audio URL: $audioUrl")
            audioUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio URL", e)
            null
        }
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        audioService.setPlaybackSpeed(speed)
        Log.d(TAG, "Playback speed changed to ${speed.displayText}")
    }

    private fun handleAudioCompletion() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val currentPlayingIndex = currentState.currentPlayingIndex

                if (currentPlayingIndex != null) {
                    val verses = currentState.verses

                    // Mark the completed ayah as read
                    if (currentPlayingIndex in verses.indices) {
                        val completedVerse = verses[currentPlayingIndex]
                        markAyahAsRead(completedVerse)
                    }

                    // Play next verse if available
                    if (currentPlayingIndex < verses.size - 1) {
                        val nextIndex = currentPlayingIndex + 1
                        Log.d(TAG, "Auto-playing next verse: index $nextIndex")

                        audioService.clearCompletion()
                        kotlinx.coroutines.delay(100)

                        playAudioAtIndex(nextIndex)
                    } else {
                        // End of page reached
                        Log.d(TAG, "Reached end of page, stopping")
                        audioService.clearCompletion()
                        audioService.stop()
                        lastCompletedUrl = null
                        _uiState.value = currentState.copy(
                            currentPlayingIndex = null,
                            isPlayingAudio = false
                        )
                    }
                } else {
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

    fun toggleJumpDialog() {
        _uiState.value = _uiState.value.copy(
            showJumpDialog = !_uiState.value.showJumpDialog
        )
    }

    fun jumpToPage(pageNumber: Int) {
        if (pageNumber in 1..KhatmProgress.TOTAL_PAGES) {
            loadPage(pageNumber)
            toggleJumpDialog()
        }
    }


    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
        audioService.clearError()
    }

    /**
     * Toggle read status for a specific ayah in Khatm reading
     * Uses KHATM_BOOKMARK_ID (-1) as the bookmark identifier
     * Also updates lastPageRead to current page when marking as read
     */
    fun toggleAyahReadStatus(verse: VerseMetadata) {
        viewModelScope.launch {
            val ayahId = getAyahId(verse)
            val currentPage = _uiState.value.currentPage
            try {
                toggleAyahTrackingUseCase(ayahId = ayahId)
                // Update last page read to current page
                updateKhatmProgressUseCase(currentPage)
                Log.d(TAG, "Toggled ayah read status and updated lastPageRead to $currentPage")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling ayah read status", e)
            }
        }
    }

    /**
     * Mark ayah as read (called when audio completes)
     * Only marks if not already read today
     * Also updates lastPageRead to current page when marking as read
     */
    private fun markAyahAsRead(verse: VerseMetadata) {
        viewModelScope.launch {
            val ayahId = getAyahId(verse)
            val currentState = _uiState.value

            // Only mark if not already read today
            if (ayahId !in currentState.readAyahIds) {
                try {
                    toggleAyahTrackingUseCase(ayahId = ayahId)
                    // Update last page read to current page
                    updateKhatmProgressUseCase(currentState.currentPage)
                    Log.d(TAG, "Marked ayah as read: $ayahId and updated lastPageRead to ${currentState.currentPage}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking ayah as read", e)
                }
            }
        }
    }

    /**
     * Generate unique ayah ID for tracking in Khatm reading
     * Format: "-1:surah:ayah" (using -1 as bookmark ID)
     */
    private fun getAyahId(verse: VerseMetadata): String {
        return "$KHATM_BOOKMARK_ID:${verse.surahNumber}:${verse.ayahInSurah}"
    }

    override fun onCleared() {
        super.onCleared()
        audioService.release()
    }
}
