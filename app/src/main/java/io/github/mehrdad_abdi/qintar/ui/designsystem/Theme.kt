package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The one theme of the app. Dynamic colour is deliberately not offered: the palette is part of
 * the identity (§6.1).
 *
 * @param pureBlack with [darkTheme], replaces the dark background with #000000.
 */
@Composable
fun QintarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = when {
        !darkTheme -> QintarPalette.Light
        pureBlack -> QintarPalette.PureBlack
        else -> QintarPalette.Dark
    }
    val colorScheme = palette.toColorScheme(dark = darkTheme)
    CompositionLocalProvider(LocalExtendedColors provides palette.toExtendedColors()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QintarTypography,
            shapes = MaterialShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
                ProvideTextStyle(QintarType.Body, content)
            }
        }
    }
}

object QintarTheme {
    /** Gold roles. Material roles are read from `MaterialTheme.colorScheme`. */
    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
}
