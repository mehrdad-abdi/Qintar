package io.github.mehrdad_abdi.quranbookmarks

import android.os.Bundle
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
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppTheme
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.presentation.navigation.NavGraph
import io.github.mehrdad_abdi.quranbookmarks.presentation.theme.QuranBookmarksTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.getSettings().collectAsState(initial = io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings())

            val darkTheme = when (settings.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            val primaryColor = try {
                Color(android.graphics.Color.parseColor(settings.primaryColorHex))
            } catch (e: Exception) {
                Color(0xFFC5A05C) // Default gold
            }

            QuranBookmarksTheme(
                darkTheme = darkTheme,
                primaryColor = primaryColor
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