package io.github.mehrdad_abdi.quranbookmarks.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_settings",
        Context.MODE_PRIVATE
    )

    private val _settingsFlow = MutableStateFlow(loadSettings())

    // Keep a strong reference to prevent garbage collection
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settingsFlow.value = loadSettings()
    }

    init {
        // Listen to preference changes
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun getSettings(): Flow<AppSettings> {
        return _settingsFlow.asStateFlow()
    }

    override suspend fun updateReciter(reciterEdition: String, bitrate: String) {
        prefs.edit()
            .putString(KEY_RECITER, reciterEdition)
            .putString(KEY_BITRATE, bitrate)
            .apply()
    }

    override suspend fun updateNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply()
    }

    override suspend fun updateNotificationTime(time: String) {
        prefs.edit().putString(KEY_NOTIFICATION_TIME, time).apply()
    }

    override suspend fun updateTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    override suspend fun updatePrimaryColor(colorHex: String) {
        prefs.edit().putString(KEY_PRIMARY_COLOR, colorHex).apply()
    }

    private fun loadSettings(): AppSettings {
        val reciter = prefs.getString(KEY_RECITER, DEFAULT_RECITER) ?: DEFAULT_RECITER
        val bitrate = prefs.getString(KEY_BITRATE, DEFAULT_BITRATE) ?: DEFAULT_BITRATE
        val notificationEnabled = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        val notificationTime = prefs.getString(KEY_NOTIFICATION_TIME, DEFAULT_TIME) ?: DEFAULT_TIME
        val themeName = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        val primaryColor = prefs.getString(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR) ?: DEFAULT_PRIMARY_COLOR
        val theme = try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }

        return AppSettings(
            reciterEdition = reciter,
            reciterBitrate = bitrate,
            notificationSettings = NotificationSettings(
                enabled = notificationEnabled,
                time = notificationTime
            ),
            theme = theme,
            primaryColorHex = primaryColor
        )
    }

    companion object {
        private const val KEY_RECITER = "reciter_edition"
        private const val KEY_BITRATE = "reciter_bitrate"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_NOTIFICATION_TIME = "notification_time"
        private const val KEY_THEME = "theme"
        private const val KEY_PRIMARY_COLOR = "primary_color"

        private const val DEFAULT_RECITER = "ar.alafasy"
        private const val DEFAULT_BITRATE = "64"
        private const val DEFAULT_TIME = "07:00"
        private const val DEFAULT_PRIMARY_COLOR = "#C5A05C" // Gold
    }
}
