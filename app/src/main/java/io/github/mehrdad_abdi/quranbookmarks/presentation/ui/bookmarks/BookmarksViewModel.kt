package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahWithBookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.PlaybackContext
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

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val bookmarksWithAyahs: List<BookmarkWithAyahs> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPlayingAyah: io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahPlaybackKey? = null,
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
                    !audioState.isPlaying &&
                    !audioState.isPlayingBismillah) { // Don't handle completion if bismillah is playing
                    lastCompletedUrl = audioState.completedUrl

                    // Mark the ayah as read
                    val currentPlaybackKey = _uiState.value.currentPlayingAyah
                    markAyahAsRead(currentPlaybackKey)

                    // Handle play-all next
                    if (_uiState.value.isPlayingAll) {
                        handlePlayAllNext()
                    } else {
                        // Handle single ayah auto-play next
                        handleSingleAyahNext()
                    }
                }
            }
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

    fun playAyah(bookmarkId: Long, verse: VerseMetadata) {
        viewModelScope.launch {
            try {
                val playbackKey = io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahPlaybackKey(bookmarkId, verse.surahNumber, verse.ayahInSurah)

                // Check if this verse from this bookmark is already selected
                if (_uiState.value.currentPlayingAyah == playbackKey &&
                    currentPlayingBookmarkId == bookmarkId) {
                    // Same verse from same bookmark - toggle play/pause
                    if (audioService.playbackState.value.isPlaying) {
                        audioService.pause()
                    } else {
                        audioService.resume()
                    }
                    return@launch
                }

                // Different verse or different bookmark - play it
                currentPlayingBookmarkId = bookmarkId

                // Get all ayahs for this bookmark for context
                val bookmarkWithAyahs = _uiState.value.bookmarksWithAyahs.find { it.bookmark.id == bookmarkId }
                val allAyahs = bookmarkWithAyahs?.ayahs ?: emptyList()
                val currentIndex = allAyahs.indexOfFirst {
                    it.surahNumber == verse.surahNumber && it.ayahInSurah == verse.ayahInSurah
                }

                val context = if (currentIndex >= 0 && allAyahs.isNotEmpty()) {
                    PlaybackContext.SingleBookmark(bookmarkId, allAyahs, currentIndex)
                } else {
                    null
                }

                _uiState.value = _uiState.value.copy(currentPlayingAyah = playbackKey)
                audioService.playVerse(verse, context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to play audio: ${e.message}")
            }
        }
    }

    fun isAyahPlaying(bookmarkId: Long, surah: Int, ayah: Int): Boolean {
        val playbackKey = io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahPlaybackKey(bookmarkId, surah, ayah)
        return _uiState.value.currentPlayingAyah == playbackKey &&
               audioService.playbackState.value.isPlaying
    }

    fun isAyahSelected(bookmarkId: Long, surah: Int, ayah: Int): Boolean {
        val playbackKey = io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahPlaybackKey(bookmarkId, surah, ayah)
        return _uiState.value.currentPlayingAyah == playbackKey
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

    /**
     * Handle auto-play next when a single ayah completes
     * Finds the next ayah in the current bookmark and plays it
     */
    private fun handleSingleAyahNext() {
        val currentPlaybackKey = _uiState.value.currentPlayingAyah
        val playingBookmarkId = currentPlayingBookmarkId

        if (currentPlaybackKey == null || playingBookmarkId == null) {
            return
        }

        // Find the bookmark that's currently playing
        val bookmarkWithAyahs = _uiState.value.bookmarksWithAyahs.find {
            it.bookmark.id == playingBookmarkId
        }

        if (bookmarkWithAyahs == null) {
            return
        }

        // Find the index of the current ayah in this bookmark
        val currentIndex = bookmarkWithAyahs.ayahs.indexOfFirst {
            it.surahNumber == currentPlaybackKey.surah && it.ayahInSurah == currentPlaybackKey.ayah
        }

        if (currentIndex == -1) {
            return
        }

        // Check if there's a next ayah in this bookmark
        val nextIndex = currentIndex + 1
        if (nextIndex < bookmarkWithAyahs.ayahs.size) {
            val nextAyah = bookmarkWithAyahs.ayahs[nextIndex]

            viewModelScope.launch {
                // Clear completion and add small delay before playing next
                audioService.clearCompletion()
                kotlinx.coroutines.delay(100)

                // Use audioService.playVerse to properly handle bismillah
                val playbackKey = io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahPlaybackKey(playingBookmarkId, nextAyah.surahNumber, nextAyah.ayahInSurah)

                // Provide context for prefetching
                val context = PlaybackContext.SingleBookmark(
                    playingBookmarkId,
                    bookmarkWithAyahs.ayahs,
                    nextIndex
                )

                _uiState.value = _uiState.value.copy(currentPlayingAyah = playbackKey)
                audioService.playVerse(nextAyah, context)
            }
        } else {
            // No more ayahs in this bookmark - stop
            audioService.clearCompletion()
            audioService.stop()
            lastCompletedUrl = null
            currentPlayingBookmarkId = null
            _uiState.value = _uiState.value.copy(currentPlayingAyah = null)
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

                // Use audioService.playVerse to properly handle bismillah
                val playbackKey = io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahPlaybackKey(
                    ayahWithBookmark.bookmarkId,
                    ayahWithBookmark.verse.surahNumber,
                    ayahWithBookmark.verse.ayahInSurah
                )

                // Provide context for Play All
                val context = PlaybackContext.AllBookmarks(allAyahsList, index)

                _uiState.value = _uiState.value.copy(currentPlayingAyah = playbackKey)
                audioService.playVerse(ayahWithBookmark.verse, context)
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

    private fun markAyahAsRead(playbackKey: io.github.mehrdad_abdi.quranbookmarks.domain.model.AyahPlaybackKey?) {
        if (playbackKey == null) return

        viewModelScope.launch {
            // Find the bookmark and verse for this playback key
            val bookmarkWithAyah = _uiState.value.bookmarksWithAyahs.firstOrNull { bookmarkWithAyahs ->
                bookmarkWithAyahs.bookmark.id == playbackKey.bookmarkId &&
                bookmarkWithAyahs.ayahs.any { it.surahNumber == playbackKey.surah && it.ayahInSurah == playbackKey.ayah }
            }

            if (bookmarkWithAyah != null) {
                val verse = bookmarkWithAyah.ayahs.find {
                    it.surahNumber == playbackKey.surah && it.ayahInSurah == playbackKey.ayah
                }
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
