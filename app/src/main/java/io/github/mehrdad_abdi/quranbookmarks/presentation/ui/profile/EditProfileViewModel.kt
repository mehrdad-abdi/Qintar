package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.GetProfileByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.UpdateProfileUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.notification.ScheduleDailyNotificationUseCase
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.mehrdad_abdi.quranbookmarks.presentation.theme.ProfileColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getProfileByIdUseCase: GetProfileByIdUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val scheduleDailyNotificationUseCase: ScheduleDailyNotificationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    companion object {
        const val DEFAULT_RECITER = "ar.alafasy"
        const val DEFAULT_BITRATE = 64
    }

    fun loadProfile(profileId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val profile = getProfileByIdUseCase(profileId)
                if (profile != null) {
                    val colorIndex = ProfileColors.indexOfFirst {
                        it.toArgb() == profile.color
                    }.takeIf { it >= 0 } ?: 0

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        profileId = profile.id,
                        name = profile.name,
                        description = profile.description,
                        selectedColorIndex = colorIndex,
                        audioEnabled = profile.reciterEdition != "none",
                        notificationEnabled = profile.notificationSettings.enabled,
                        notificationTime = profile.notificationSettings.time,
                        originalProfile = profile
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Profile not found"
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

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null
        )
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateColor(colorIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedColorIndex = colorIndex)
    }

    fun updateAudioEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(audioEnabled = enabled)
    }

    fun updateNotificationEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationEnabled = enabled)
    }

    fun updateNotificationTime(time: String) {
        _uiState.value = _uiState.value.copy(notificationTime = time)
    }

    fun updateProfile(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        val originalProfile = currentState.originalProfile ?: return

        // Validate input
        if (currentState.name.isBlank()) {
            _uiState.value = currentState.copy(nameError = "Profile name is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)

            val updatedProfile = originalProfile.copy(
                name = currentState.name.trim(),
                description = currentState.description.trim(),
                color = ProfileColors[currentState.selectedColorIndex].toArgb(),
                reciterEdition = if (currentState.audioEnabled) DEFAULT_RECITER else "none",
                notificationSettings = originalProfile.notificationSettings.copy(
                    enabled = currentState.notificationEnabled,
                    time = currentState.notificationTime
                )
            )

            updateProfileUseCase(updatedProfile).fold(
                onSuccess = {
                    // Update notification scheduling
                    scheduleDailyNotificationUseCase(updatedProfile)
                    _uiState.value = currentState.copy(isLoading = false)
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to update profile"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class EditProfileUiState(
    val isLoading: Boolean = false,
    val profileId: Long = 0L,
    val name: String = "",
    val description: String = "",
    val selectedColorIndex: Int = 0,
    val audioEnabled: Boolean = true,
    val notificationEnabled: Boolean = false,
    val notificationTime: String = "07:00",
    val nameError: String? = null,
    val error: String? = null,
    val originalProfile: BookmarkGroup? = null
)