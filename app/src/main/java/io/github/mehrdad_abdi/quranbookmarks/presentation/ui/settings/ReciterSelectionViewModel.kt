package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReciterSelectionUiState(
    val reciters: List<ReciterData> = emptyList(),
    val selectedReciterId: String = "ar.alafasy",
    val playingReciterId: String? = null,
    val loadingSampleReciterId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectionComplete: Boolean = false
)

@HiltViewModel
class ReciterSelectionViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val settingsRepository: SettingsRepository,
    private val audioService: AudioService
) : ViewModel() {

    companion object {
        private const val SAMPLE_AYAH_REF = "57:21" // Surah Hadid, Ayah 21
    }

    private val _uiState = MutableStateFlow(ReciterSelectionUiState())
    private val _navigationEvent = MutableStateFlow(false)
    val navigationEvent: StateFlow<Boolean> = _navigationEvent.asStateFlow()

    val uiState: StateFlow<ReciterSelectionUiState> = combine(
        _uiState,
        audioService.playbackState,
        settingsRepository.getSettings()
    ) { uiState, audioState, settings ->
        uiState.copy(
            selectedReciterId = settings.reciterEdition,
            playingReciterId = if (audioState.isPlaying) uiState.playingReciterId else null,
            selectionComplete = false // Reset in combined flow
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = ReciterSelectionUiState()
    )

    init {
        loadReciters()
    }

    private fun loadReciters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            quranRepository.getAvailableReciters().fold(
                onSuccess = { reciters ->
                    _uiState.value = _uiState.value.copy(
                        reciters = reciters,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to load reciters: ${error.message}",
                        isLoading = false
                    )
                }
            )
        }
    }

    fun selectReciter(reciter: ReciterData) {
        viewModelScope.launch {
            try {
                // Fetch sample ayah to extract bitrate from audio URL
                _uiState.value = _uiState.value.copy(loadingSampleReciterId = reciter.identifier)

                val ayahResult = quranRepository.getAyahWithAudio(SAMPLE_AYAH_REF, reciter.identifier)

                ayahResult.fold(
                    onSuccess = { ayahData ->
                        // Extract bitrate from audio URL
                        val audioUrl = ayahData.audio ?: ""
                        val bitrate = extractBitrateFromUrl(audioUrl)

                        // Save reciter and bitrate
                        settingsRepository.updateReciter(reciter.identifier, bitrate)

                        _uiState.value = _uiState.value.copy(
                            loadingSampleReciterId = null
                        )

                        // Trigger navigation event
                        _navigationEvent.value = true
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to select reciter: ${error.message}",
                            loadingSampleReciterId = null
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to select reciter: ${e.message}",
                    loadingSampleReciterId = null
                )
            }
        }
    }

    fun playSample(reciter: ReciterData) {
        viewModelScope.launch {
            try {
                // If already playing this reciter, pause it
                if (_uiState.value.playingReciterId == reciter.identifier) {
                    audioService.pause()
                    _uiState.value = _uiState.value.copy(playingReciterId = null)
                    return@launch
                }

                _uiState.value = _uiState.value.copy(loadingSampleReciterId = reciter.identifier)

                // Fetch sample ayah audio
                val ayahResult = quranRepository.getAyahWithAudio(SAMPLE_AYAH_REF, reciter.identifier)

                ayahResult.fold(
                    onSuccess = { ayahData ->
                        val audioUrl = ayahData.audio
                        if (audioUrl != null) {
                            _uiState.value = _uiState.value.copy(
                                playingReciterId = reciter.identifier,
                                loadingSampleReciterId = null
                            )
                            audioService.playAudio(audioUrl)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                error = "No audio available for this reciter",
                                loadingSampleReciterId = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to play sample: ${error.message}",
                            loadingSampleReciterId = null
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to play sample: ${e.message}",
                    loadingSampleReciterId = null
                )
            }
        }
    }

    private fun extractBitrateFromUrl(audioUrl: String): String {
        // Extract bitrate from URL like: https://cdn.islamic.network/quran/audio/64/ar.alafasy/5196.mp3
        // The bitrate is the number before the reciter identifier
        val regex = """/(\d+)/[^/]+/\d+\.mp3""".toRegex()
        val match = regex.find(audioUrl)
        return match?.groupValues?.get(1) ?: "64" // Default to 64 if not found
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        audioService.release()
    }
}
