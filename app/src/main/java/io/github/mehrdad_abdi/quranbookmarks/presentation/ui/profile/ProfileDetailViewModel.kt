package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.DeleteBookmarkUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarksByGroupUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkDisplayTextUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.GetProfileByIdUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkWithDisplayText(
    val bookmark: Bookmark,
    val displayText: String
)

data class ProfileDetailUiState(
    val profile: BookmarkGroup? = null,
    val bookmarks: List<BookmarkWithDisplayText> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    private val getProfileByIdUseCase: GetProfileByIdUseCase,
    private val getBookmarksByGroupUseCase: GetBookmarksByGroupUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,
    private val getBookmarkDisplayTextUseCase: GetBookmarkDisplayTextUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileDetailUiState())
    val uiState: StateFlow<ProfileDetailUiState> = _uiState.asStateFlow()

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

                // Load bookmarks for this profile
                getBookmarksByGroupUseCase(profileId)
                    .catch { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load bookmarks"
                        )
                    }
                    .collect { bookmarks ->
                        // Convert bookmarks to BookmarkWithDisplayText
                        val bookmarksWithDisplayText = bookmarks.map { bookmark ->
                            val displayText = try {
                                getBookmarkDisplayTextUseCase(bookmark)
                            } catch (e: Exception) {
                                bookmark.getDisplayText() // Fallback to original method
                            }
                            BookmarkWithDisplayText(bookmark, displayText)
                        }

                        _uiState.value = _uiState.value.copy(
                            profile = profile,
                            bookmarks = bookmarksWithDisplayText,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
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
                // Note: The bookmark list will automatically update through the flow from getBookmarksByGroupUseCase
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to delete bookmark"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}