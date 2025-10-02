package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.random

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.TemporaryBookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random.CreateRandomBookmarkUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RandomModeSelectionUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val generatedBookmarkId: Long? = null
)

@HiltViewModel
class RandomModeSelectionViewModel @Inject constructor(
    private val createRandomBookmarkUseCase: CreateRandomBookmarkUseCase,
    private val temporaryBookmarkRepository: TemporaryBookmarkRepository
) : ViewModel() {

    companion object {
        private const val TAG = "RandomModeSelectionVM"
    }

    private val _uiState = MutableStateFlow(RandomModeSelectionUiState())
    val uiState: StateFlow<RandomModeSelectionUiState> = _uiState.asStateFlow()

    fun generateRandomAyah() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = createRandomBookmarkUseCase.createRandomAyah()
                if (result.isSuccess) {
                    val bookmark = result.getOrThrow()
                    temporaryBookmarkRepository.saveTemporaryBookmark(bookmark)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        generatedBookmarkId = TemporaryBookmarkRepository.TEMP_BOOKMARK_ID
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to generate random ayah: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating random ayah", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun generateRandomPage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = createRandomBookmarkUseCase.createRandomPage()
                if (result.isSuccess) {
                    val bookmark = result.getOrThrow()
                    temporaryBookmarkRepository.saveTemporaryBookmark(bookmark)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        generatedBookmarkId = TemporaryBookmarkRepository.TEMP_BOOKMARK_ID
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to generate random page: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating random page", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun clearGeneratedBookmark() {
        _uiState.value = _uiState.value.copy(generatedBookmarkId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
