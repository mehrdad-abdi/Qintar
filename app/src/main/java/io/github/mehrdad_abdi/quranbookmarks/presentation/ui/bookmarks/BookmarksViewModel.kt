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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkWithAyahs(
    val bookmark: Bookmark,
    val ayahs: List<VerseMetadata> = emptyList(),
    val isLoadingAyahs: Boolean = false
)

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val bookmarksWithAyahs: List<BookmarkWithAyahs> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPlayingAyah: Int? = null,
    val isPlayingAll: Boolean = false
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val getAllBookmarksUseCase: GetAllBookmarksUseCase,
    private val getAllTagsUseCase: GetAllTagsUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val getAyahsFromBookmarkUseCase: GetAyahsFromBookmarkUseCase,
    private val quranRepository: QuranRepository,
    private val audioService: AudioService
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    private var lastCompletedUrl: String? = null
    private var allAyahsList: List<VerseMetadata> = emptyList()
    private var currentPlayAllIndex: Int = 0

    val uiState: StateFlow<BookmarksUiState> = combine(
        _uiState,
        audioService.playbackState
    ) { uiState, audioState ->
        // Handle auto-play next when playing all
        if (uiState.isPlayingAll &&
            audioState.completedUrl != null &&
            audioState.completedUrl != lastCompletedUrl &&
            !audioState.isPlaying) {
            lastCompletedUrl = audioState.completedUrl
            handlePlayAllNext()
        }

        uiState.copy(
            currentPlayingAyah = if (audioState.isPlaying) uiState.currentPlayingAyah else null,
            isPlayingAll = if (!audioState.isPlaying) false else uiState.isPlayingAll
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = BookmarksUiState()
    )

    init {
        loadBookmarks()
        loadTags()
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
                val ayahReferences = getAyahsFromBookmarkUseCase(bookmark)

                // Load all ayahs from cache or API
                val ayahs = ayahReferences.mapNotNull { ayahRef ->
                    // Try to get from cache first
                    val cachedContent = quranRepository.getCachedContent(ayahRef.surah, ayahRef.ayah)

                    if (cachedContent != null) {
                        // Use cached metadata
                        cachedContent.metadata
                    } else {
                        // Fall back to API if not cached
                        quranRepository.getVerseMetadata(ayahRef.globalAyahNumber).getOrNull()
                    }
                }

                BookmarkWithAyahs(bookmark, ayahs, false)
            } catch (e: Exception) {
                BookmarkWithAyahs(bookmark, emptyList(), false)
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
                val cachedContent = quranRepository.getCachedContent(verse.surahNumber, verse.ayahInSurah)

                val audioUrl = if (cachedContent?.audioPath != null) {
                    // Use cached audio file
                    "file://${cachedContent.audioPath}"
                } else {
                    // Fall back to streaming
                    quranRepository.getAudioUrl("ar.alafasy", verse.globalAyahNumber, "64")
                }

                _uiState.value = _uiState.value.copy(currentPlayingAyah = verse.globalAyahNumber)
                audioService.playAudio(audioUrl)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to play audio: ${e.message}")
            }
        }
    }

    fun isAyahPlaying(globalAyahNumber: Int): Boolean {
        return _uiState.value.currentPlayingAyah == globalAyahNumber
    }

    fun togglePlayAll() {
        if (_uiState.value.isPlayingAll) {
            // Stop playing
            audioService.pause()
            _uiState.value = _uiState.value.copy(isPlayingAll = false, currentPlayingAyah = null)
        } else {
            // Start playing all
            allAyahsList = _uiState.value.bookmarksWithAyahs.flatMap { it.ayahs }
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
            _uiState.value = _uiState.value.copy(isPlayingAll = false, currentPlayingAyah = null)
        }
    }

    private fun playAyahAtIndex(index: Int) {
        if (index < allAyahsList.size) {
            val verse = allAyahsList[index]
            viewModelScope.launch {
                try {
                    val cachedContent = quranRepository.getCachedContent(verse.surahNumber, verse.ayahInSurah)

                    val audioUrl = if (cachedContent?.audioPath != null) {
                        "file://${cachedContent.audioPath}"
                    } else {
                        quranRepository.getAudioUrl("ar.alafasy", verse.globalAyahNumber, "64")
                    }

                    _uiState.value = _uiState.value.copy(currentPlayingAyah = verse.globalAyahNumber)
                    audioService.playAudio(audioUrl)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = "Failed to play audio: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioService.release()
    }
}
