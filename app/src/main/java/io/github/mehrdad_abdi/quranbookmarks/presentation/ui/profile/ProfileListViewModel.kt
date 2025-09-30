package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.DeleteProfileUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.GetAllProfilesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileListViewModel @Inject constructor(
    private val getAllProfilesUseCase: GetAllProfilesUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileListUiState())
    val uiState: StateFlow<ProfileListUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            getAllProfilesUseCase()
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "Unknown error occurred"
                    )
                }
                .collect { profiles ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        profiles = profiles,
                        error = null
                    )
                }
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            deleteProfileUseCase(profileId).fold(
                onSuccess = {
                    // Profiles will be automatically updated via the Flow
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        error = throwable.message ?: "Failed to delete profile"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class ProfileListUiState(
    val isLoading: Boolean = false,
    val profiles: List<BookmarkGroup> = emptyList(),
    val error: String? = null
)