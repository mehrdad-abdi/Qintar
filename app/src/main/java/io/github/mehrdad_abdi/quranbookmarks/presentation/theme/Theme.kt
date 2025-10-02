package io.github.mehrdad_abdi.quranbookmarks.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun QuranBookmarksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    primaryColor: Color = GoldPrimary,
    content: @Composable () -> Unit
) {
    // Derive secondary and tertiary colors from primary
    val secondaryColor = primaryColor.copy(alpha = 0.7f)
    val tertiaryColor = primaryColor.copy(alpha = 0.5f)

    val darkColorScheme = darkColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = tertiaryColor,
        background = DarkGreen,
        surface = DarkGreen.copy(alpha = 0.9f),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = WarmWhite,
        onSurface = WarmWhite,
        primaryContainer = primaryColor.copy(alpha = 0.2f),
        onPrimaryContainer = primaryColor,
        surfaceVariant = LightGreen,
        onSurfaceVariant = CreamBackground
    )

    val lightColorScheme = lightColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = tertiaryColor,
        background = CreamBackground,
        surface = WarmWhite,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = DarkGreen,
        onSurface = DarkGreen,
        primaryContainer = primaryColor.copy(alpha = 0.15f),
        onPrimaryContainer = primaryColor,
        surfaceVariant = CreamBackground.copy(alpha = 0.7f),
        onSurfaceVariant = DarkGreen.copy(alpha = 0.7f)
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}