package io.github.mehrdad_abdi.quranbookmarks

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.presentation.navigation.NavGraph
import io.github.mehrdad_abdi.quranbookmarks.presentation.theme.QuranBookmarksTheme
import io.github.mehrdad_abdi.quranbookmarks.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(newBase: Context) {
        Log.d(TAG, "attachBaseContext called")
        Log.d(TAG, "Original context locale: ${newBase.resources.configuration.locales[0]}")

        // Apply the saved language before attaching the base context
        val language = runBlocking {
            try {
                val settings = settingsRepository.getSettings().first()
                Log.d(TAG, "Loaded language from settings: ${settings.language.localeCode} (${settings.language.nativeName})")
                settings.language
            } catch (e: Exception) {
                Log.e(TAG, "Error loading language settings, using ENGLISH", e)
                io.github.mehrdad_abdi.quranbookmarks.domain.model.AppLanguage.ENGLISH
            }
        }
        val context = LocaleHelper.applyLocale(newBase, language)
        Log.d(TAG, "Context after applyLocale: ${context.resources.configuration.locales[0]}")
        super.attachBaseContext(context)
    }

    override fun getResources(): Resources {
        val res = super.getResources()

        // Get current language from settings
        val language = runBlocking {
            try {
                settingsRepository.getSettings().first().language
            } catch (e: Exception) {
                Log.e(TAG, "Error loading language in getResources", e)
                io.github.mehrdad_abdi.quranbookmarks.domain.model.AppLanguage.ENGLISH
            }
        }

        // Create configuration with the correct locale
        val config = Configuration(res.configuration)
        val locale = Locale(language.localeCode)
        config.setLocale(locale)

        Log.d(TAG, "getResources() returning resources with locale: ${config.locales[0]}")

        // Return context with updated configuration
        return createConfigurationContext(config).resources
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        Log.d(TAG, "Activity resources locale: ${resources.configuration.locales[0]}")

        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.getSettings().collectAsState(initial = io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings())

            Log.d(TAG, "Settings language in onCreate: ${settings.language.localeCode}")

            val darkTheme = when (settings.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            val primaryColor = try {
                Color(android.graphics.Color.parseColor(settings.primaryColorHex))
            } catch (e: Exception) {
                Color(0xFF006B7D) // Default Qintar teal
            }

            QuranBookmarksTheme(
                darkTheme = darkTheme,
                primaryColor = primaryColor,
                isRtl = settings.language.isRtl
            ) {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavGraph(
                        navController = navController
                    )
                }
            }
        }
    }
}