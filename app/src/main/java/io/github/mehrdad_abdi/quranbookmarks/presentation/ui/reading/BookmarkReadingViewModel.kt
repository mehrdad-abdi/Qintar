package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.PlaybackContext
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkDisplayTextUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetBookmarkContentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkReadingUiState(
    val bookmark: Bookmark? = null,
    val listItems: List<ReadingListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPlayingAudio: Boolean = false,
    val currentPlayingIndex: Int? = null,
    val reciters: List<ReciterData> = emptyList(),
    val selectedReciter: ReciterData? = null,
    val showReciterSelection: Boolean = false,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.SPEED_1,
    val readAyahIds: Set<String> = emptySet() // Set of ayah IDs read today
)

@HiltViewModel
class BookmarkReadingViewModel @Inject constructor(
    private val getBookmarkByIdUseCase: GetBookmarkByIdUseCase,
    private val getBookmarkDisplayTextUseCase: GetBookmarkDisplayTextUseCase,
    private val getBookmarkContentUseCase: GetBookmarkContentUseCase,
    private val quranRepository: QuranRepository,
    private val settingsRepository: SettingsRepository,
    private val audioService: AudioService,
    private val getTodayStatsUseCase: io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetTodayStatsUseCase,
    private val toggleAyahTrackingUseCase: io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.ToggleAyahTrackingUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "BookmarkReadingVM"
        private const val DEFAULT_RECITER = "ar.alafasy"
    }

    private val _uiState = MutableStateFlow(BookmarkReadingUiState())
    private var lastCompletedUrl: String? = null

    val uiState: StateFlow<BookmarkReadingUiState> = combine(
        _uiState,
        audioService.playbackState,
        getTodayStatsUseCase()
    ) { uiState, audioState, todayActivity ->
        // Handle auto-play next ayah when current one completes
        // Only trigger if we have a new completed URL and audio is NOT playing
        if (audioState.completedUrl != null &&
            audioState.completedUrl != lastCompletedUrl &&
            !audioState.isPlaying) {
            lastCompletedUrl = audioState.completedUrl
            handleAudioCompletion(audioState.completedUrl)
        }

        uiState.copy(
            isPlayingAudio = audioState.isPlaying,
            currentPlayingIndex = if (audioState.isPlaying) uiState.currentPlayingIndex else null,
            playbackSpeed = PlaybackSpeed.fromValue(audioState.playbackSpeed),
            readAyahIds = todayActivity.trackedAyahIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = BookmarkReadingUiState()
    )

    fun loadBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Load bookmark details
                val bookmarkResult = getBookmarkByIdUseCase(bookmarkId)
                if (bookmarkResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Bookmark not found"
                    )
                    return@launch
                }

                val bookmark = bookmarkResult.getOrThrow()
                Log.d(TAG, "Loaded bookmark: ${bookmark.getDisplayText()}")

                // Load verses for this bookmark and build list items
                val listItems = buildListItems(bookmark)

                // Load reciters with default reciter BEFORE updating state
                // This ensures selectedReciter is available for immediate playback
                loadReciters(DEFAULT_RECITER)

                _uiState.value = _uiState.value.copy(
                    bookmark = bookmark,
                    listItems = listItems,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bookmark", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load bookmark"
                )
            }
        }
    }

    private suspend fun buildListItems(bookmark: Bookmark): List<ReadingListItem> {
        val listItems = mutableListOf<ReadingListItem>()
        var globalIndex = 0

        try {
            // Get display text
            val displayText = try {
                getBookmarkDisplayTextUseCase(bookmark)
            } catch (e: Exception) {
                bookmark.getDisplayText()
            }

            // Get verses for this bookmark
            val contentResult = getBookmarkContentUseCase(bookmark)
            if (contentResult.isSuccess) {
                val verses = contentResult.getOrThrow()
                Log.d(TAG, "Loaded ${verses.size} verses for bookmark")

                // Calculate metadata
                val metadata = calculateBookmarkMetadata(verses)

                // Add bookmark header
                listItems.add(
                    ReadingListItem.BookmarkHeader(
                        bookmark = bookmark,
                        displayText = displayText,
                        metadata = metadata
                    )
                )

                // Add verses (each consumes a globalIndex)
                verses.forEach { verse ->
                    Log.d(TAG, "Adding verse ${verse.ayahInSurah} with globalIndex=$globalIndex")
                    listItems.add(
                        ReadingListItem.VerseItem(
                            verse = verse,
                            globalIndex = globalIndex,
                            bookmarkId = bookmark.id
                        )
                    )
                    globalIndex++
                }
            } else {
                Log.e(TAG, "Failed to load verses for bookmark: ${contentResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing bookmark", e)
        }

        Log.d(TAG, "Built ${listItems.size} list items")
        return listItems
    }

    private fun calculateBookmarkMetadata(verses: List<VerseMetadata>): String {
        if (verses.isEmpty()) return ""

        val verseCount = verses.size
        val pages = verses.map { it.page }.distinct().sorted()
        val juzs = verses.map {
            kotlin.math.min(((it.hizbQuarter - 1) / 8) + 1, 30)
        }.distinct().sorted()

        val verseText = "$verseCount verse${if (verseCount != 1) "s" else ""}"
        val pageText = if (pages.size == 1) {
            "Page ${pages.first()}"
        } else {
            "Pages ${pages.first()}-${pages.last()}"
        }
        val juzText = if (juzs.size == 1) {
            "Juz ${juzs.first()}"
        } else {
            "Juz ${juzs.first()}-${juzs.last()}"
        }

        return "$verseText • $pageText • $juzText"
    }

    private suspend fun loadReciters(defaultReciterId: String) {
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
            Log.d(TAG, "Pausing playback")
            audioService.pause()
        } else {
            val playingIndex = currentState.currentPlayingIndex ?: startIndex
            Log.d(TAG, "Starting playback at index $playingIndex")
            playAudioAtIndex(playingIndex)
        }
    }

    fun playAudioAtIndex(globalIndex: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val verseItems = currentState.listItems.filterIsInstance<ReadingListItem.VerseItem>()

            // Find the verse with this globalIndex
            val verseItem = verseItems.find { it.globalIndex == globalIndex }

            if (verseItem == null) {
                Log.w(TAG, "Invalid globalIndex: $globalIndex")
                return@launch
            }

            val verse = verseItem.verse
            Log.d(TAG, "=== PLAYING AUDIO DEBUG ===")
            Log.d(TAG, "Verse: Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}, GlobalIndex $globalIndex")

            // Prepare playback context for prefetching
            val bookmark = currentState.bookmark
            val allVersesList = verseItems.map { it.verse }
            val context = if (bookmark != null && allVersesList.isNotEmpty()) {
                PlaybackContext.SingleBookmark(
                    bookmarkId = bookmark.id,
                    allAyahs = allVersesList,
                    currentIndex = verseItems.indexOfFirst { it.globalIndex == globalIndex }
                )
            } else {
                null
            }

            // Use audioService.playVerse to handle bismillah automatically
            _uiState.value = currentState.copy(currentPlayingIndex = globalIndex)
            audioService.playVerse(verse, context)
        }
    }

    private suspend fun getAudioUrl(verseItem: ReadingListItem.VerseItem): String? {
        val state = _uiState.value
        val reciter = state.selectedReciter
        val verse = verseItem.verse

        return if (reciter != null) {
            // Check if we have a cached audio file first
            if (verse.cachedAudioPath != null) {
                Log.d(TAG, "Using cached audio: ${verse.cachedAudioPath}")
                return verse.cachedAudioPath
            }

            // Fall back to streaming URL using settings bitrate
            val settings = settingsRepository.getSettings().stateIn(viewModelScope).value
            val audioUrl = quranRepository.getAudioUrl(reciter.identifier, verse.globalAyahNumber, settings.reciterBitrate)
            Log.d(TAG, "Using streaming audio URL for ayah ${verse.globalAyahNumber} with bitrate ${settings.reciterBitrate}")
            audioUrl
        } else {
            Log.e(TAG, "No reciter selected")
            null
        }
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        audioService.setPlaybackSpeed(speed)
        Log.d(TAG, "Playback speed changed to ${speed.displayText}")
    }

    private fun handleAudioCompletion(completedUrl: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val currentPlayingIndex = currentState.currentPlayingIndex

                Log.d(TAG, "Audio completed: $completedUrl, current index: $currentPlayingIndex")

                // Normal verse-to-verse progression
                if (currentPlayingIndex != null) {
                    val verseItems = currentState.listItems.filterIsInstance<ReadingListItem.VerseItem>()

                    // Find the current verse and mark it as read
                    val currentVerseItem = verseItems.find { it.globalIndex == currentPlayingIndex }
                    if (currentVerseItem != null) {
                        markAyahAsRead(currentVerseItem)
                    }

                    // Find the next verse by globalIndex
                    val currentVersePosition = verseItems.indexOfFirst { it.globalIndex == currentPlayingIndex }
                    if (currentVersePosition >= 0 && currentVersePosition < verseItems.size - 1) {
                        val nextVerse = verseItems[currentVersePosition + 1]
                        Log.d(TAG, "Auto-playing next verse: globalIndex ${nextVerse.globalIndex}")

                        // Clear completion BEFORE playing next to prevent race condition
                        audioService.clearCompletion()

                        // Small delay to ensure MediaPlayer has fully released
                        kotlinx.coroutines.delay(100)

                        playAudioAtIndex(nextVerse.globalIndex)
                    } else {
                        Log.d(TAG, "Reached end of playlist, stopping")
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
        audioService.clearError()
    }

    /**
     * Toggle read status for a specific ayah
     */
    fun toggleAyahReadStatus(verseItem: ReadingListItem.VerseItem) {
        viewModelScope.launch {
            val bookmark = _uiState.value.bookmark ?: return@launch
            val ayahId = getAyahId(bookmark, verseItem.verse)
            try {
                toggleAyahTrackingUseCase(ayahId = ayahId)
                Log.d(TAG, "Toggled read status for ayah: $ayahId")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling ayah read status", e)
            }
        }
    }

    /**
     * Mark ayah as read (called when audio completes)
     */
    private fun markAyahAsRead(verseItem: ReadingListItem.VerseItem) {
        viewModelScope.launch {
            val bookmark = _uiState.value.bookmark ?: return@launch
            val ayahId = getAyahId(bookmark, verseItem.verse)
            val currentState = _uiState.value

            // Only mark if not already read today
            if (ayahId !in currentState.readAyahIds) {
                try {
                    toggleAyahTrackingUseCase(ayahId = ayahId)
                    Log.d(TAG, "Marked ayah as read: $ayahId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking ayah as read", e)
                }
            }
        }
    }

    /**
     * Generate unique ayah ID for tracking
     * Format: "bookmarkId:surah:ayah"
     */
    private fun getAyahId(bookmark: Bookmark, verse: VerseMetadata): String {
        return "${bookmark.id}:${verse.surahNumber}:${verse.ayahInSurah}"
    }

    override fun onCleared() {
        super.onCleared()
        audioService.release()
    }
}