package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppLanguage
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateReciter(reciterEdition: String, bitrate: String)
    suspend fun updateNotificationEnabled(enabled: Boolean)
    suspend fun updateNotificationTime(time: String)
    suspend fun updateTheme(theme: AppTheme)
    suspend fun updatePrimaryColor(colorHex: String)
    suspend fun updatePlaybackSpeed(speed: Float)
    suspend fun updateLanguage(language: AppLanguage)
}
