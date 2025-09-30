package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.CreateProfileUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.notification.ScheduleDailyNotificationUseCase
import androidx.compose.ui.graphics.toArgb
import io.github.mehrdad_abdi.quranbookmarks.presentation.theme.ProfileColors
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddProfileViewModel @Inject constructor(
    private val createProfileUseCase: CreateProfileUseCase,
    private val scheduleDailyNotificationUseCase: ScheduleDailyNotificationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProfileUiState())
    val uiState: StateFlow<AddProfileUiState> = _uiState.asStateFlow()

    companion object {
        const val DEFAULT_RECITER = "ar.alafasy"
        const val DEFAULT_BITRATE = 64
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

    fun createProfile(onSuccess: (Long) -> Unit) {
        val currentState = _uiState.value

        // Validate input
        if (currentState.name.isBlank()) {
            _uiState.value = currentState.copy(nameError = "Profile name is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)

            val profile = BookmarkGroup(
                name = currentState.name.trim(),
                description = currentState.description.trim(),
                color = ProfileColors[currentState.selectedColorIndex].toArgb(),
                reciterEdition = if (currentState.audioEnabled) DEFAULT_RECITER else "none",
                notificationSettings = NotificationSettings(
                    enabled = currentState.notificationEnabled,
                    schedule = NotificationSchedule.DAILY,
                    time = currentState.notificationTime
                )
            )

            createProfileUseCase(profile).fold(
                onSuccess = { profileId ->
                    // Schedule notification if enabled
                    if (currentState.notificationEnabled) {
                        scheduleDailyNotificationUseCase(profile.copy(id = profileId))
                    }
                    _uiState.value = currentState.copy(isLoading = false)
                    onSuccess(profileId)
                },
                onFailure = { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create profile"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class AddProfileUiState(
    val name: String = "",
    val description: String = "",
    val selectedColorIndex: Int = 0,
    val audioEnabled: Boolean = true, // Default to enabled
    val notificationEnabled: Boolean = false,
    val notificationTime: String = "07:00", // Default to 7:00 AM
    val nameError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)