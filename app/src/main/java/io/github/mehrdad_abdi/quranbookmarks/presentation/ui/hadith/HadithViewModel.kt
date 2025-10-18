package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.hadith

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class HadithViewModel @Inject constructor(
    val settingsRepository: SettingsRepository
) : ViewModel()
