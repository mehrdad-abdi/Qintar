package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateReciter(reciterEdition: String, bitrate: String)
    suspend fun updateNotificationEnabled(enabled: Boolean)
    suspend fun updateNotificationTime(time: String)
    suspend fun updateTheme(theme: io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme)
    suspend fun updatePrimaryColor(colorHex: String)
    suspend fun updatePlaybackSpeed(speed: Float)
}
