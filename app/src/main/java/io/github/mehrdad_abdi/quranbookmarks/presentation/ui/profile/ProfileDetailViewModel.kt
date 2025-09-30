package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.DeleteBookmarkUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarksByGroupUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkDisplayTextUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.GetProfileByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetBookmarkContentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileDetailUiState(
    val profile: BookmarkGroup? = null,
    val listItems: List<ProfileListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPlayingAudio: Boolean = false,
    val currentPlayingIndex: Int? = null,
    val reciters: List<ReciterData> = emptyList(),
    val selectedReciter: ReciterData? = null,
    val showReciterSelection: Boolean = false,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.SPEED_1,
    val pendingVerseAfterBismillah: Int? = null, // Tracks which verse to play after bismillah completes
    val isPlayingBismillah: Boolean = false // Indicates bismillah is currently playing
)

@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    private val getProfileByIdUseCase: GetProfileByIdUseCase,
    private val getBookmarksByGroupUseCase: GetBookmarksByGroupUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val getBookmarkDisplayTextUseCase: GetBookmarkDisplayTextUseCase,
    private val getBookmarkContentUseCase: GetBookmarkContentUseCase,
    private val quranRepository: QuranRepository,
    private val audioService: AudioService
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileDetailVM"
    }

    private val _uiState = MutableStateFlow(ProfileDetailUiState())
    private var lastCompletedUrl: String? = null

    val uiState: StateFlow<ProfileDetailUiState> = combine(
        _uiState,
        audioService.playbackState
    ) { uiState, audioState ->
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
            playbackSpeed = PlaybackSpeed.fromValue(audioState.playbackSpeed)
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileDetailUiState()
    )

    fun loadProfile(profileId: Long) {
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

                // Load bookmarks for this profile
                getBookmarksByGroupUseCase(profileId)
                    .catch { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load bookmarks"
                        )
                    }
                    .collect { bookmarks ->
                        Log.d(TAG, "Loaded ${bookmarks.size} bookmarks")

                        // Load verses for each bookmark and build list items
                        val listItems = buildListItems(bookmarks)

                        _uiState.value = _uiState.value.copy(
                            profile = profile,
                            listItems = listItems,
                            isLoading = false,
                            error = null
                        )

                        // Load reciters
                        loadReciters(profile.reciterEdition)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
        }
    }

    private suspend fun buildListItems(bookmarks: List<Bookmark>): List<ProfileListItem> {
        val listItems = mutableListOf<ProfileListItem>()
        var globalIndex = 0

        for (bookmark in bookmarks) {
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
                    Log.d(TAG, "Loaded ${verses.size} verses for bookmark ${bookmark.id}")

                    // Calculate metadata
                    val metadata = calculateBookmarkMetadata(verses)

                    // Add bookmark header (doesn't consume globalIndex, just stores the start position)
                    val headerStartIndex = globalIndex
                    listItems.add(
                        ProfileListItem.BookmarkHeader(
                            bookmark = bookmark,
                            displayText = displayText,
                            verseCount = verses.size,
                            metadata = metadata,
                            globalStartIndex = headerStartIndex
                        )
                    )

                    // Add verses (each consumes a globalIndex)
                    verses.forEach { verse ->
                        Log.d(TAG, "Adding verse ${verse.ayahInSurah} with globalIndex=$globalIndex")
                        listItems.add(
                            ProfileListItem.VerseItem(
                                verse = verse,
                                bookmark = bookmark,
                                displayNumber = verse.ayahInSurah.toString(),
                                globalIndex = globalIndex
                            )
                        )
                        globalIndex++
                    }
                } else {
                    Log.e(TAG, "Failed to load verses for bookmark ${bookmark.id}: ${contentResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing bookmark ${bookmark.id}", e)
            }
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
            val verseItems = currentState.listItems.filterIsInstance<ProfileListItem.VerseItem>()

            // Find the verse with this globalIndex
            val verseItem = verseItems.find { it.globalIndex == globalIndex }

            if (verseItem == null) {
                Log.w(TAG, "Invalid globalIndex: $globalIndex")
                return@launch
            }

            // Check if bismillah should be played first
            if (shouldPlayBismillah(verseItem)) {
                Log.d(TAG, "Playing bismillah before verse at globalIndex $globalIndex")
                val bismillahUrl = getBismillahAudioUrl()

                if (bismillahUrl != null) {
                    // Set pending verse and play bismillah
                    _uiState.value = currentState.copy(
                        currentPlayingIndex = globalIndex,
                        pendingVerseAfterBismillah = globalIndex,
                        isPlayingBismillah = true
                    )
                    audioService.playAudio(bismillahUrl)
                    return@launch
                } else {
                    Log.e(TAG, "Unable to get bismillah audio URL, playing verse directly")
                }
            }

            // Play the verse directly (either no bismillah needed or bismillah URL failed)
            val audioUrl = getAudioUrl(verseItem)

            if (audioUrl != null) {
                Log.d(TAG, "Playing verse at globalIndex $globalIndex: $audioUrl")
                _uiState.value = currentState.copy(
                    currentPlayingIndex = globalIndex,
                    isPlayingBismillah = false,
                    pendingVerseAfterBismillah = null
                )
                audioService.playAudio(audioUrl)
            } else {
                Log.e(TAG, "Unable to get audio URL for verse at globalIndex $globalIndex")
                _uiState.value = currentState.copy(
                    error = "Unable to get audio URL for this verse",
                    isPlayingBismillah = false,
                    pendingVerseAfterBismillah = null
                )
            }
        }
    }

    private fun getAudioUrl(verseItem: ProfileListItem.VerseItem): String? {
        val state = _uiState.value
        val reciter = state.selectedReciter
        val verse = verseItem.verse

        return if (reciter != null) {
            // Check if we have a cached audio file first
            if (verse.cachedAudioPath != null) {
                Log.d(TAG, "Using cached audio: ${verse.cachedAudioPath}")
                return verse.cachedAudioPath
            }

            // Fall back to streaming URL
            val audioUrl = quranRepository.getAudioUrl(reciter.identifier, verse.globalAyahNumber, "128")
            Log.d(TAG, "Using streaming audio URL for ayah ${verse.globalAyahNumber}")
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

    /**
     * Check if bismillah should be played before this verse
     * Bismillah plays for first verses (ayahInSurah == 1) of surahs 2-114, excluding surah 9 (At-Tawbah)
     */
    private fun shouldPlayBismillah(verseItem: ProfileListItem.VerseItem): Boolean {
        val verse = verseItem.verse
        val shouldPlay = verse.ayahInSurah == 1 &&
                        verse.surahNumber in 2..114 &&
                        verse.surahNumber != 9

        if (shouldPlay) {
            Log.d(TAG, "Bismillah needed for surah ${verse.surahNumber}, ayah ${verse.ayahInSurah}")
        }
        return shouldPlay
    }

    /**
     * Get audio URL for bismillah (global ayah number 1 - Al-Fatiha 1:1)
     * Check for cached bismillah first, fall back to streaming
     */
    private suspend fun getBismillahAudioUrl(): String? {
        val state = _uiState.value
        val reciter = state.selectedReciter

        return if (reciter != null) {
            // Check if bismillah is cached (surah 1, ayah 1)
            val cachedContent = quranRepository.getCachedContent(1, 1)
            if (cachedContent?.audioPath != null) {
                Log.d(TAG, "Using cached bismillah audio: ${cachedContent.audioPath}")
                return cachedContent.audioPath
            }

            // Fall back to streaming URL for global ayah number 1
            val audioUrl = quranRepository.getAudioUrl(reciter.identifier, 1, "128")
            Log.d(TAG, "Using streaming bismillah audio URL")
            audioUrl
        } else {
            Log.e(TAG, "No reciter selected for bismillah")
            null
        }
    }

    private fun handleAudioCompletion(completedUrl: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val currentPlayingIndex = currentState.currentPlayingIndex

                Log.d(TAG, "Audio completed: $completedUrl, current index: $currentPlayingIndex")

                // Check if bismillah just completed - if so, play the pending verse
                if (currentState.isPlayingBismillah && currentState.pendingVerseAfterBismillah != null) {
                    Log.d(TAG, "Bismillah completed, playing pending verse: ${currentState.pendingVerseAfterBismillah}")

                    // Clear completion BEFORE playing next to prevent race condition
                    audioService.clearCompletion()

                    // Small delay to ensure MediaPlayer has fully released
                    kotlinx.coroutines.delay(100)

                    val verseItems = currentState.listItems.filterIsInstance<ProfileListItem.VerseItem>()
                    val verseItem = verseItems.find { it.globalIndex == currentState.pendingVerseAfterBismillah }

                    if (verseItem != null) {
                        val audioUrl = getAudioUrl(verseItem)
                        if (audioUrl != null) {
                            _uiState.value = currentState.copy(
                                isPlayingBismillah = false,
                                pendingVerseAfterBismillah = null
                            )
                            audioService.playAudio(audioUrl)
                        } else {
                            Log.e(TAG, "Unable to get audio URL for pending verse")
                            _uiState.value = currentState.copy(
                                isPlayingBismillah = false,
                                pendingVerseAfterBismillah = null,
                                error = "Unable to get audio URL for verse"
                            )
                        }
                    }
                    return@launch
                }

                // Normal verse-to-verse progression
                if (currentPlayingIndex != null) {
                    val verseItems = currentState.listItems.filterIsInstance<ProfileListItem.VerseItem>()

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
                            isPlayingAudio = false,
                            isPlayingBismillah = false,
                            pendingVerseAfterBismillah = null
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
                _uiState.value = _uiState.value.copy(
                    isPlayingBismillah = false,
                    pendingVerseAfterBismillah = null
                )
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            try {
                val result = deleteBookmarkUseCase(bookmarkId)
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message ?: "Failed to delete bookmark"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to delete bookmark"
                )
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