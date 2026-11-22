package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of DESIGN.md §6.1, kept as plain ARGB longs so that the contrast test can
 * read them without a Compose runtime. Feature code never uses these directly: it reads
 * `MaterialTheme.colorScheme` and [QintarTheme.extendedColors].
 */
data class QintarPalette(
    val background: Long,
    val surface: Long,
    val surfaceContainer: Long,
    val outlineVariant: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val gold: Long,
    val goldInk: Long,
    val goldContainer: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
) {
    companion object {
        val Light = QintarPalette(
            background = 0xFFFAF7F0,
            surface = 0xFFFFFDF8,
            surfaceContainer = 0xFFF3EEE3,
            outlineVariant = 0xFFE2DACB,
            onSurface = 0xFF1E2A2A,
            onSurfaceVariant = 0xFF5E6B68,
            primary = 0xFF0F5C55,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFD5EBE6,
            onPrimaryContainer = 0xFF05332F,
            gold = 0xFFB08026, // §6.1 lists #B8862B, which is 2.80:1 on surfaceContainer; darkened, same hue, to reach 3:1
            goldInk = 0xFF7A5A12,
            goldContainer = 0xFFF4E7C8,
            error = 0xFFB3261E,
            onError = 0xFFFFFFFF,
            errorContainer = 0xFFF9DEDC,
            onErrorContainer = 0xFF410E0B,
        )

        val Dark = QintarPalette(
            background = 0xFF0F1514,
            surface = 0xFF151C1B,
            surfaceContainer = 0xFF1C2524,
            outlineVariant = 0xFF2C3836,
            onSurface = 0xFFE6ECE9,
            onSurfaceVariant = 0xFF9DAAA6,
            primary = 0xFF7FCFC3,
            onPrimary = 0xFF00382F,
            primaryContainer = 0xFF1F4A44,
            onPrimaryContainer = 0xFFC9EEE7,
            gold = 0xFFE2B659,
            goldInk = 0xFFE2B659,
            goldContainer = 0xFF4A3A14,
            error = 0xFFF2B8B5,
            onError = 0xFF601410,
            errorContainer = 0xFF8C1D18,
            onErrorContainer = 0xFFF9DEDC,
        )

        /** The optional "pure black" variant of the dark theme (§6.1). */
        val PureBlack = Dark.copy(background = 0xFF000000)
    }
}

/** Roles that Material 3 has no slot for. Gold means achievement and appears nowhere else. */
@Immutable
data class ExtendedColors(
    /** Ring, rank marks, rank-up. Graphics only: never use for text. */
    val gold: Color,
    /** Gold-coloured text. */
    val goldInk: Color,
    /** Rank chip background. Text on it uses [goldInk]. */
    val goldContainer: Color,
)

internal val LocalExtendedColors = staticCompositionLocalOf {
    QintarPalette.Light.toExtendedColors()
}

internal fun QintarPalette.toExtendedColors() = ExtendedColors(
    gold = Color(gold),
    goldInk = Color(goldInk),
    goldContainer = Color(goldContainer),
)

internal fun QintarPalette.toColorScheme(dark: Boolean): ColorScheme {
    val bg = Color(background)
    val surface = Color(surface)
    val container = Color(surfaceContainer)
    val hairline = Color(outlineVariant)
    val ink = Color(onSurface)
    val quietInk = Color(onSurfaceVariant)
    val primary = Color(primary)
    val onPrimary = Color(onPrimary)
    val primaryContainer = Color(primaryContainer)
    val onPrimaryContainer = Color(onPrimaryContainer)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    // One green: secondary mirrors primary so that stock Material components (navigation bar
    // indicator, filter chips) stay inside the palette. Tertiary is the gold family.
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = if (dark) Color(QintarPalette.Light.primary) else Color(QintarPalette.Dark.primary),
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = Color(goldInk),
        onTertiary = if (dark) Color(goldContainer) else Color(0xFFFFFFFF),
        tertiaryContainer = Color(goldContainer),
        onTertiaryContainer = Color(goldInk),
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = container,
        onSurfaceVariant = quietInk,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = surface,
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = quietInk,
        outlineVariant = hairline,
        scrim = Color(0xFF000000),
        surfaceBright = surface,
        surfaceDim = container,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = container,
        surfaceContainerHigh = container,
        surfaceContainerHighest = container,
    )
}
