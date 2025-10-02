package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.CreateBookmarkUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.surah.GetAllSurahsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddBookmarkUiState(
    val selectedSurah: Surah? = null,
    val ayahNumber: String = "",
    val endAyahNumber: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val bookmarkType: BookmarkType = BookmarkType.AYAH,
    val isLoading: Boolean = false,
    val isLoadingSurahs: Boolean = false,
    val surahs: List<Surah> = emptyList(),
    val error: String? = null,
    val surahError: String? = null,
    val ayahError: String? = null,
    val endAyahError: String? = null
)

@HiltViewModel
class AddBookmarkViewModel @Inject constructor(
    private val createBookmarkUseCase: CreateBookmarkUseCase,
    private val getAllSurahsUseCase: GetAllSurahsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBookmarkUiState())
    val uiState: StateFlow<AddBookmarkUiState> = _uiState.asStateFlow()

    companion object {
        const val TOTAL_SURAHS = 114
        const val MAX_AYAH_IN_LONGEST_SURAH = 286 // Al-Baqarah has 286 ayahs
    }

    init {
        loadSurahs()
    }

    private fun loadSurahs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSurahs = true)

            getAllSurahsUseCase().fold(
                onSuccess = { surahs ->
                    _uiState.value = _uiState.value.copy(
                        surahs = surahs,
                        isLoadingSurahs = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingSurahs = false,
                        error = error.message ?: "Failed to load surahs"
                    )
                }
            )
        }
    }

    fun updateSelectedSurah(surah: Surah) {
        _uiState.value = _uiState.value.copy(
            selectedSurah = surah,
            surahError = null
        )
    }

    fun updateAyahNumber(ayah: String) {
        _uiState.value = _uiState.value.copy(
            ayahNumber = ayah,
            ayahError = null
        )
    }

    fun updateEndAyahNumber(endAyah: String) {
        _uiState.value = _uiState.value.copy(
            endAyahNumber = endAyah,
            endAyahError = null
        )
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateBookmarkType(type: BookmarkType) {
        _uiState.value = _uiState.value.copy(
            bookmarkType = type,
            // Clear end ayah when switching away from range
            endAyahNumber = if (type != BookmarkType.RANGE) "" else _uiState.value.endAyahNumber
        )
    }

    fun updateTagInput(input: String) {
        _uiState.value = _uiState.value.copy(tagInput = input)
    }

    fun addTag(tag: String) {
        val trimmedTag = tag.trim()
        if (trimmedTag.isNotEmpty() && !_uiState.value.tags.contains(trimmedTag)) {
            _uiState.value = _uiState.value.copy(
                tags = _uiState.value.tags + trimmedTag,
                tagInput = ""
            )
        }
    }

    fun removeTag(tag: String) {
        _uiState.value = _uiState.value.copy(
            tags = _uiState.value.tags.filter { it != tag }
        )
    }

    fun createBookmark(onSuccess: () -> Unit) {
        val currentState = _uiState.value

        // Validate input
        if (!validateInput(currentState)) {
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)

            try {
                val startSurah: Int
                val startAyah: Int
                val endSurah: Int
                val endAyah: Int

                if (currentState.bookmarkType == BookmarkType.PAGE) {
                    // For page bookmarks, we don't need surah information
                    val pageNumber = currentState.ayahNumber.toInt()
                    startSurah = 1 // Placeholder - page bookmarks don't have a specific surah
                    startAyah = pageNumber
                    endSurah = 1
                    endAyah = pageNumber
                } else {
                    val selectedSurah = currentState.selectedSurah
                        ?: throw IllegalStateException("No surah selected")

                    startSurah = selectedSurah.number
                    startAyah = if (currentState.bookmarkType == BookmarkType.SURAH) 1 else currentState.ayahNumber.toInt()
                    endSurah = startSurah
                    endAyah = when (currentState.bookmarkType) {
                        BookmarkType.AYAH -> startAyah
                        BookmarkType.RANGE -> currentState.endAyahNumber.toInt()
                        BookmarkType.SURAH -> selectedSurah.numberOfAyahs
                        BookmarkType.PAGE -> startAyah // This case won't be reached
                    }
                }

                val bookmark = Bookmark(
                    type = currentState.bookmarkType,
                    startSurah = startSurah,
                    startAyah = startAyah,
                    endSurah = endSurah,
                    endAyah = endAyah,
                    description = currentState.description.trim(),
                    tags = currentState.tags
                )

                createBookmarkUseCase(bookmark).fold(
                    onSuccess = {
                        _uiState.value = currentState.copy(isLoading = false)
                        onSuccess()
                    },
                    onFailure = { error ->
                        _uiState.value = currentState.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to create bookmark"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = e.message ?: "Invalid input"
                )
            }
        }
    }

    private fun validateInput(state: AddBookmarkUiState): Boolean {
        var isValid = true
        val updatedState = state.copy(
            surahError = null,
            ayahError = null,
            endAyahError = null
        )

        // Validate Surah selection (not required for PAGE type)
        if (state.bookmarkType != BookmarkType.PAGE && state.selectedSurah == null) {
            _uiState.value = updatedState.copy(surahError = "Please select a surah")
            isValid = false
        }

        // Validate Ayah number for AYAH and RANGE types
        if (state.bookmarkType == BookmarkType.AYAH || state.bookmarkType == BookmarkType.RANGE) {
            val ayahNumber = state.ayahNumber.toIntOrNull()
            val maxAyahs = state.selectedSurah?.numberOfAyahs ?: MAX_AYAH_IN_LONGEST_SURAH

            if (ayahNumber == null || ayahNumber < 1) {
                _uiState.value = _uiState.value.copy(ayahError = "Ayah number must be at least 1")
                isValid = false
            } else if (ayahNumber > maxAyahs) {
                _uiState.value = _uiState.value.copy(ayahError = "Ayah number cannot exceed $maxAyahs for this surah")
                isValid = false
            }
        }

        // Validate end ayah for RANGE type
        if (state.bookmarkType == BookmarkType.RANGE) {
            val startAyah = state.ayahNumber.toIntOrNull()
            val endAyah = state.endAyahNumber.toIntOrNull()
            val maxAyahs = state.selectedSurah?.numberOfAyahs ?: MAX_AYAH_IN_LONGEST_SURAH

            if (endAyah == null || endAyah < 1) {
                _uiState.value = _uiState.value.copy(endAyahError = "End ayah number must be at least 1")
                isValid = false
            } else if (endAyah > maxAyahs) {
                _uiState.value = _uiState.value.copy(endAyahError = "End ayah number cannot exceed $maxAyahs for this surah")
                isValid = false
            } else if (startAyah != null && endAyah <= startAyah) {
                _uiState.value = _uiState.value.copy(endAyahError = "End ayah must be greater than start ayah")
                isValid = false
            }
        }

        return isValid
    }


    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}