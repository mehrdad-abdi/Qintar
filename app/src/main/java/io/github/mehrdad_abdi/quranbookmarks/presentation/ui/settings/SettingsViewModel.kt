package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppLanguage
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val availableReciters: List<ReciterData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val quranRepository: QuranRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadReciters()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            settingsRepository.getSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    isLoading = false
                )
            }
        }
    }

    private fun loadReciters() {
        viewModelScope.launch {
            quranRepository.getAvailableReciters().fold(
                onSuccess = { reciters ->
                    _uiState.value = _uiState.value.copy(availableReciters = reciters)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to load reciters: ${error.message}"
                    )
                }
            )
        }
    }

    fun updateReciter(reciterEdition: String) {
        viewModelScope.launch {
            try {
                // Keep current bitrate when updating reciter
                val currentSettings = settingsRepository.getSettings().first()
                settingsRepository.updateReciter(reciterEdition, currentSettings.reciterBitrate)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update reciter: ${e.message}"
                )
            }
        }
    }

    fun updateNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.updateNotificationEnabled(enabled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update notification: ${e.message}"
                )
            }
        }
    }

    fun updateNotificationTime(time: String) {
        viewModelScope.launch {
            try {
                settingsRepository.updateNotificationTime(time)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update time: ${e.message}"
                )
            }
        }
    }

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            try {
                settingsRepository.updateTheme(theme)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update theme: ${e.message}"
                )
            }
        }
    }

    fun updatePrimaryColor(colorHex: String) {
        viewModelScope.launch {
            try {
                settingsRepository.updatePrimaryColor(colorHex)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update color: ${e.message}"
                )
            }
        }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            try {
                settingsRepository.updateLanguage(language)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update language: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
