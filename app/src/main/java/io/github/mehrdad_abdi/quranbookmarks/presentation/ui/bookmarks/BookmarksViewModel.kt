package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.DeleteBookmarkUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetAllBookmarksUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetAllTagsUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.GetAyahsFromBookmarkUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkWithAyahs(
    val bookmark: Bookmark,
    val displayText: String,
    val ayahs: List<VerseMetadata> = emptyList(),
    val isLoadingAyahs: Boolean = false,
    val isExpanded: Boolean = false
)

/**
 * Wrapper class to uniquely identify an ayah with its bookmark context
 * This prevents confusion when the same ayah appears in multiple bookmarks
 */
data class AyahWithBookmark(
    val bookmarkId: Long,
    val verse: VerseMetadata
)

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val bookmarksWithAyahs: List<BookmarkWithAyahs> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPlayingAyah: Int? = null,
    val isPlayingAll: Boolean = false,
    val readAyahIds: Set<String> = emptySet(),
    val playbackSpeed: io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed = io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed.SPEED_1
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val getAllBookmarksUseCase: GetAllBookmarksUseCase,
    private val getAllTagsUseCase: GetAllTagsUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val getBookmarkContentUseCase: io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetBookmarkContentUseCase,
    private val getBookmarkDisplayTextUseCase: io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkDisplayTextUseCase,
    private val quranRepository: QuranRepository,
    private val settingsRepository: SettingsRepository,
    private val audioService: AudioService,
    private val getTodayStatsUseCase: io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetTodayStatsUseCase,
    private val toggleAyahTrackingUseCase: io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.ToggleAyahTrackingUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "BookmarksVM"
    }

    private val _uiState = MutableStateFlow(BookmarksUiState())
    private var lastCompletedUrl: String? = null
    private var allAyahsList: List<AyahWithBookmark> = emptyList()
    private var currentPlayAllIndex: Int = 0
    private var pendingVerseAfterBismillah: VerseMetadata? = null
    private var isPlayingBismillah: Boolean = false
    private var currentPlayingBookmarkId: Long? = null

    val uiState: StateFlow<BookmarksUiState> = combine(
        _uiState,
        audioService.playbackState,
        getTodayStatsUseCase()
    ) { uiState, audioState, todayActivity ->
        uiState.copy(
            currentPlayingAyah = if (audioState.isPlaying) uiState.currentPlayingAyah else null,
            isPlayingAll = if (!audioState.isPlaying) false else uiState.isPlayingAll,
            readAyahIds = todayActivity.trackedAyahIds,
            playbackSpeed = io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed.fromValue(audioState.playbackSpeed)
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = BookmarksUiState()
    )

    init {
        loadBookmarks()
        loadTags()
        observeAudioCompletion()
    }

    private fun observeAudioCompletion() {
        viewModelScope.launch {
            audioService.playbackState.collect { audioState ->
                // When audio completes
                if (audioState.completedUrl != null &&
                    audioState.completedUrl != lastCompletedUrl &&
                    !audioState.isPlaying) {
                    lastCompletedUrl = audioState.completedUrl

                    // Check if bismillah just completed
                    if (isPlayingBismillah && pendingVerseAfterBismillah != null) {
                        android.util.Log.d(TAG, "Bismillah completed, playing pending verse")
                        audioService.clearCompletion()
                        kotlinx.coroutines.delay(100)

                        val verse = pendingVerseAfterBismillah!!
                        isPlayingBismillah = false
                        pendingVerseAfterBismillah = null
                        playAyahDirectWithoutBismillah(verse)
                        return@collect
                    }

                    // Mark the ayah as read
                    val currentAyahNumber = _uiState.value.currentPlayingAyah
                    if (currentAyahNumber != null) {
                        markAyahAsRead(currentAyahNumber)
                    }

                    // Handle play-all next
                    if (_uiState.value.isPlayingAll) {
                        handlePlayAllNext()
                    }
                }
            }
        }
    }

    private suspend fun playAyahDirect(verse: VerseMetadata) {
        android.util.Log.d(TAG, "=== PLAYING AUDIO DEBUG ===")
        android.util.Log.d(TAG, "Verse: Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}")

        // Check if bismillah should be played first
        if (shouldPlayBismillah(verse)) {
            android.util.Log.d(TAG, "✓ BISMILLAH REQUIRED for Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}")
            val bismillahUrl = getBismillahAudioUrl()

            if (bismillahUrl != null) {
                android.util.Log.d(TAG, "▶ PLAYING BISMILLAH: $bismillahUrl")
                isPlayingBismillah = true
                pendingVerseAfterBismillah = verse
                _uiState.value = _uiState.value.copy(currentPlayingAyah = verse.globalAyahNumber)
                audioService.playAudio(bismillahUrl)
                return
            } else {
                android.util.Log.e(TAG, "✗ BISMILLAH URL IS NULL - playing verse directly")
            }
        } else {
            android.util.Log.d(TAG, "✗ No bismillah needed for Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}")
        }

        playAyahDirectWithoutBismillah(verse)
    }

    private suspend fun playAyahDirectWithoutBismillah(verse: VerseMetadata) {
        try {
            val cachedContent = quranRepository.getCachedContent(verse.surahNumber, verse.ayahInSurah)
            val settings = settingsRepository.getSettings().stateIn(viewModelScope).value

            val audioUrl = if (cachedContent?.audioPath != null) {
                "file://${cachedContent.audioPath}"
            } else {
                quranRepository.getAudioUrl(settings.reciterEdition, verse.globalAyahNumber, settings.reciterBitrate)
            }

            android.util.Log.d(TAG, "▶ PLAYING VERSE: $audioUrl")
            _uiState.value = _uiState.value.copy(currentPlayingAyah = verse.globalAyahNumber)
            audioService.playAudio(audioUrl)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Failed to play audio: ${e.message}")
        }
    }

    /**
     * Check if bismillah should be played before this verse
     * Bismillah plays for first verses (ayahInSurah == 1) of surahs 2-114, excluding surah 9 (At-Tawbah)
     */
    private fun shouldPlayBismillah(verse: VerseMetadata): Boolean {
        android.util.Log.d(TAG, "Checking bismillah: Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}")
        android.util.Log.d(TAG, "  - ayahInSurah == 1? ${verse.ayahInSurah == 1}")
        android.util.Log.d(TAG, "  - surahNumber in 2..114? ${verse.surahNumber in 2..114}")
        android.util.Log.d(TAG, "  - surahNumber != 9? ${verse.surahNumber != 9}")

        val shouldPlay = verse.ayahInSurah == 1 &&
                        verse.surahNumber in 2..114 &&
                        verse.surahNumber != 9

        android.util.Log.d(TAG, "  - RESULT: shouldPlay = $shouldPlay")
        return shouldPlay
    }

    /**
     * Get audio URL for bismillah (global ayah number 1 - Al-Fatiha 1:1)
     */
    private suspend fun getBismillahAudioUrl(): String? {
        android.util.Log.d(TAG, "getBismillahAudioUrl called")

        try {
            // Check if bismillah is cached (surah 1, ayah 1)
            val cachedContent = quranRepository.getCachedContent(1, 1)
            android.util.Log.d(TAG, "  - Cached content for 1:1: ${cachedContent != null}")
            android.util.Log.d(TAG, "  - Cached audio path: ${cachedContent?.audioPath}")

            if (cachedContent?.audioPath != null) {
                android.util.Log.d(TAG, "  → Using cached bismillah audio: ${cachedContent.audioPath}")
                return "file://${cachedContent.audioPath}"
            }

            // Fall back to streaming URL for global ayah number 1
            val settings = settingsRepository.getSettings().stateIn(viewModelScope).value
            val audioUrl = quranRepository.getAudioUrl(settings.reciterEdition, 1, settings.reciterBitrate)
            android.util.Log.d(TAG, "  → Using streaming bismillah URL: $audioUrl (bitrate: ${settings.reciterBitrate})")
            return audioUrl
        } catch (e: Exception) {
            android.util.Log.e(TAG, "  ✗ Error getting bismillah URL", e)
            return null
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            getAllBookmarksUseCase().collect { bookmarks ->
                val filteredBookmarks = filterBookmarks(bookmarks, _uiState.value.selectedTag)

                // Load all ayahs at once (from cache or API)
                val bookmarksWithAyahs = loadAllAyahsForBookmarks(filteredBookmarks)

                _uiState.value = _uiState.value.copy(
                    bookmarks = bookmarks,
                    bookmarksWithAyahs = bookmarksWithAyahs,
                    isLoading = false
                )
            }
        }
    }

    private suspend fun loadAllAyahsForBookmarks(bookmarks: List<Bookmark>): List<BookmarkWithAyahs> {
        return bookmarks.map { bookmark ->
            try {
                // Get display text using use case
                val displayText = try {
                    getBookmarkDisplayTextUseCase(bookmark)
                } catch (e: Exception) {
                    bookmark.getDisplayText()
                }

                // Use GetBookmarkContentUseCase which handles all bookmark types correctly
                val contentResult = getBookmarkContentUseCase(bookmark)
                val ayahs = if (contentResult.isSuccess) {
                    contentResult.getOrThrow()
                } else {
                    emptyList()
                }

                BookmarkWithAyahs(bookmark, displayText, ayahs, false)
            } catch (e: Exception) {
                BookmarkWithAyahs(bookmark, bookmark.getDisplayText(), emptyList(), false)
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                val tags = getAllTagsUseCase()
                _uiState.value = _uiState.value.copy(allTags = tags)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun selectTag(tag: String?) {
        viewModelScope.launch {
            val filteredBookmarks = filterBookmarks(_uiState.value.bookmarks, tag)

            // Load all ayahs for filtered bookmarks
            val bookmarksWithAyahs = loadAllAyahsForBookmarks(filteredBookmarks)

            _uiState.value = _uiState.value.copy(
                selectedTag = tag,
                bookmarksWithAyahs = bookmarksWithAyahs
            )
        }
    }

    private fun filterBookmarks(bookmarks: List<Bookmark>, tag: String?): List<Bookmark> {
        return if (tag == null) {
            bookmarks
        } else {
            bookmarks.filter { it.tags.contains(tag) }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            deleteBookmarkUseCase(bookmarkId).fold(
                onSuccess = {
                    // Bookmarks will auto-reload via Flow
                    loadTags() // Reload tags in case a tag was removed
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to delete bookmark"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun playAyah(verse: VerseMetadata) {
        viewModelScope.launch {
            try {
                // Check if this verse is already selected
                if (_uiState.value.currentPlayingAyah == verse.globalAyahNumber) {
                    // Same verse - toggle play/pause
                    if (audioService.playbackState.value.isPlaying) {
                        audioService.pause()
                    } else {
                        audioService.resume()
                    }
                    return@launch
                }

                // Different verse - play it
                val cachedContent = quranRepository.getCachedContent(verse.surahNumber, verse.ayahInSurah)
                val settings = settingsRepository.getSettings().stateIn(viewModelScope).value

                val audioUrl = if (cachedContent?.audioPath != null) {
                    // Use cached audio file
                    "file://${cachedContent.audioPath}"
                } else {
                    // Fall back to streaming using settings reciter and bitrate
                    quranRepository.getAudioUrl(settings.reciterEdition, verse.globalAyahNumber, settings.reciterBitrate)
                }

                _uiState.value = _uiState.value.copy(currentPlayingAyah = verse.globalAyahNumber)
                audioService.playAudio(audioUrl)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to play audio: ${e.message}")
            }
        }
    }

    fun isAyahPlaying(bookmarkId: Long, globalAyahNumber: Int): Boolean {
        return _uiState.value.currentPlayingAyah == globalAyahNumber &&
               currentPlayingBookmarkId == bookmarkId &&
               audioService.playbackState.value.isPlaying
    }

    fun isAyahSelected(bookmarkId: Long, globalAyahNumber: Int): Boolean {
        return _uiState.value.currentPlayingAyah == globalAyahNumber &&
               currentPlayingBookmarkId == bookmarkId
    }

    fun isBookmarkCurrentlyPlaying(bookmarkId: Long): Boolean {
        return currentPlayingBookmarkId == bookmarkId
    }

    fun togglePlayAll() {
        if (_uiState.value.isPlayingAll) {
            // Stop playing
            audioService.pause()
            currentPlayingBookmarkId = null
            _uiState.value = _uiState.value.copy(isPlayingAll = false, currentPlayingAyah = null)
        } else {
            // Start playing all - wrap each ayah with its bookmark ID to maintain context
            allAyahsList = _uiState.value.bookmarksWithAyahs.flatMap { bookmarkWithAyahs ->
                bookmarkWithAyahs.ayahs.map { verse ->
                    AyahWithBookmark(bookmarkWithAyahs.bookmark.id, verse)
                }
            }
            if (allAyahsList.isNotEmpty()) {
                currentPlayAllIndex = 0
                _uiState.value = _uiState.value.copy(isPlayingAll = true)
                playAyahAtIndex(currentPlayAllIndex)
            }
        }
    }

    private fun handlePlayAllNext() {
        currentPlayAllIndex++
        if (currentPlayAllIndex < allAyahsList.size) {
            playAyahAtIndex(currentPlayAllIndex)
        } else {
            // Finished playing all
            audioService.clearCompletion()
            audioService.stop()
            lastCompletedUrl = null
            _uiState.value = _uiState.value.copy(isPlayingAll = false, currentPlayingAyah = null)
        }
    }

    private fun playAyahAtIndex(index: Int) {
        if (index < allAyahsList.size) {
            val ayahWithBookmark = allAyahsList[index]
            currentPlayingBookmarkId = ayahWithBookmark.bookmarkId
            viewModelScope.launch {
                // Clear completion and add small delay before playing next
                audioService.clearCompletion()
                kotlinx.coroutines.delay(100)

                // Use playAyahDirect to properly handle bismillah
                playAyahDirect(ayahWithBookmark.verse)
            }
        }
    }

    fun toggleAyahReadStatus(bookmark: Bookmark, verse: VerseMetadata) {
        viewModelScope.launch {
            val ayahId = getAyahId(bookmark, verse)
            try {
                toggleAyahTrackingUseCase(ayahId = ayahId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to toggle read status: ${e.message}")
            }
        }
    }

    private fun markAyahAsRead(globalAyahNumber: Int) {
        viewModelScope.launch {
            // Find the bookmark and verse for this globalAyahNumber
            val bookmarkWithAyah = _uiState.value.bookmarksWithAyahs.firstOrNull { bookmarkWithAyahs ->
                bookmarkWithAyahs.ayahs.any { it.globalAyahNumber == globalAyahNumber }
            }

            if (bookmarkWithAyah != null) {
                val verse = bookmarkWithAyah.ayahs.find { it.globalAyahNumber == globalAyahNumber }
                if (verse != null) {
                    val ayahId = getAyahId(bookmarkWithAyah.bookmark, verse)

                    // Get fresh stats to avoid race condition
                    val todayStats = getTodayStatsUseCase.getTodaySnapshot()

                    // Only mark if not already read today
                    if (!todayStats.isAyahTracked(ayahId)) {
                        try {
                            toggleAyahTrackingUseCase(ayahId = ayahId)
                        } catch (e: Exception) {
                            // Silent failure for automatic tracking
                        }
                    }
                }
            }
        }
    }

    private fun getAyahId(bookmark: Bookmark, verse: VerseMetadata): String {
        return "${bookmark.id}:${verse.surahNumber}:${verse.ayahInSurah}"
    }

    fun setPlaybackSpeed(speed: io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed) {
        audioService.setPlaybackSpeed(speed)
    }

    fun pauseAyah() {
        audioService.pause()
    }

    fun resumeAyah() {
        audioService.resume()
    }

    fun toggleBookmarkExpansion(bookmarkId: Long) {
        val currentBookmarks = _uiState.value.bookmarksWithAyahs
        val updatedBookmarks = currentBookmarks.map { bookmarkWithAyahs ->
            if (bookmarkWithAyahs.bookmark.id == bookmarkId) {
                bookmarkWithAyahs.copy(isExpanded = !bookmarkWithAyahs.isExpanded)
            } else {
                bookmarkWithAyahs
            }
        }
        _uiState.value = _uiState.value.copy(bookmarksWithAyahs = updatedBookmarks)
    }

    /**
     * Expand a bookmark if it's currently collapsed
     * Returns true if the bookmark was expanded, false if it was already expanded
     */
    fun expandBookmarkIfNeeded(bookmarkId: Long): Boolean {
        val currentBookmarks = _uiState.value.bookmarksWithAyahs
        val bookmark = currentBookmarks.find { it.bookmark.id == bookmarkId }

        if (bookmark != null && !bookmark.isExpanded) {
            val updatedBookmarks = currentBookmarks.map { bookmarkWithAyahs ->
                if (bookmarkWithAyahs.bookmark.id == bookmarkId) {
                    bookmarkWithAyahs.copy(isExpanded = true)
                } else {
                    bookmarkWithAyahs
                }
            }
            _uiState.value = _uiState.value.copy(bookmarksWithAyahs = updatedBookmarks)
            return true
        }

        return false
    }

    /**
     * Smart toggle: Expand all if any are collapsed, collapse all if all are expanded
     */
    fun expandOrCollapseAll() {
        val currentBookmarks = _uiState.value.bookmarksWithAyahs

        // Check if all bookmarks are currently expanded
        val allExpanded = currentBookmarks.all { it.isExpanded }

        // If all expanded, collapse all. Otherwise, expand all.
        val newExpandedState = !allExpanded

        val updatedBookmarks = currentBookmarks.map { bookmarkWithAyahs ->
            bookmarkWithAyahs.copy(isExpanded = newExpandedState)
        }

        _uiState.value = _uiState.value.copy(bookmarksWithAyahs = updatedBookmarks)
    }

    override fun onCleared() {
        super.onCleared()
        audioService.release()
    }
}
