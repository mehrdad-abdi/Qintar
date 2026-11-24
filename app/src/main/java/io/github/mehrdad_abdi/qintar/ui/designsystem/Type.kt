package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import io.github.mehrdad_abdi.qintar.R

/**
 * Vazirmatn variable font (wght axis 100–900), one file for Persian, Arabic and Latin (§6.2).
 * Each weight is the same resource instantiated at a point on the axis, which works from API 26.
 */
@OptIn(ExperimentalTextApi::class)
private fun vazirmatn(weight: FontWeight) = Font(
    resId = R.font.vazirmatn_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val UiFontFamily = FontFamily(
    vazirmatn(FontWeight.Normal),
    vazirmatn(FontWeight.Medium),
    vazirmatn(FontWeight.SemiBold),
)

/** Amiri Quran: paired with the Tanzil Uthmani text; draws U+06DD + digits as the ayah-end ornament. */
val QuranFontFamily = FontFamily(Font(R.font.amiri_quran, FontWeight.Normal))

/** Tabular figures, so that counts do not jitter as they change (§6.2). */
private const val TABULAR = "tnum"

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun ui(size: Int, lineHeight: Int, weight: FontWeight, tabular: Boolean = false) = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = if (tabular) TABULAR else null,
    platformStyle = NoFontPadding,
    lineHeightStyle = CenteredLines,
)

/** The six roles of §6.2. Prefer these names in components; [QintarTypography] maps them onto M3. */
object QintarType {
    /** 56/60 semibold, tabular: the Today count. */
    val Display = ui(56, 60, FontWeight.SemiBold, tabular = true)
    val Headline = ui(24, 32, FontWeight.SemiBold)
    val Title = ui(18, 26, FontWeight.Medium)
    val Body = ui(16, 24, FontWeight.Normal)
    val Label = ui(13, 18, FontWeight.Medium)
    val Caption = ui(12, 16, FontWeight.Normal)

    /** Body-sized emphasised text: row titles, button-like text. Not a new size, only a weight. */
    val BodyStrong = ui(16, 24, FontWeight.Medium)

    /** Counters inside rows and charts: label size with tabular figures. */
    val Numeric = ui(13, 18, FontWeight.Medium, tabular = true)
}

internal val QintarTypography = Typography(
    displayLarge = QintarType.Display,
    displayMedium = QintarType.Display,
    displaySmall = QintarType.Display,
    headlineLarge = QintarType.Headline,
    headlineMedium = QintarType.Headline,
    headlineSmall = QintarType.Headline,
    titleLarge = QintarType.Title,
    titleMedium = QintarType.Title,
    titleSmall = QintarType.BodyStrong,
    bodyLarge = QintarType.Body,
    bodyMedium = QintarType.Body,
    bodySmall = QintarType.Caption,
    labelLarge = QintarType.Label,
    labelMedium = QintarType.Label,
    labelSmall = QintarType.Caption,
)

/** Reader font size bounds of the Aa panel (§5.4.1). */
const val QuranTextSizeMinSp = 20
const val QuranTextSizeMaxSp = 48
const val QuranTextSizeDefaultSp = 28

/**
 * Style for Quran text. [sizeSp] is the user's reader setting and deliberately does **not** follow
 * the system font scale (§6.2: the Quran text has its own slider). Line height is 1.9× so the
 * diacritics and the small high pause marks have room. Always right-to-left, whatever the app
 * language (§5.4.2).
 */
@Composable
@ReadOnlyComposable
fun quranTextStyle(sizeSp: Int = QuranTextSizeDefaultSp): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    val size = (sizeSp / fontScale).sp
    return TextStyle(
        fontFamily = QuranFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = size,
        lineHeight = size * 1.9f,
        letterSpacing = 0.sp,
        textDirection = TextDirection.Rtl,
        textAlign = TextAlign.Start,
        platformStyle = NoFontPadding,
        lineHeightStyle = CenteredLines,
    )
}

/**
 * How far a pause mark is lowered, in ems. Amiri Quran draws the marks high above the line, so on
 * every line but the first they reached into the line above (owner, 2026-11-25).
 */
private const val PAUSE_MARK_SHIFT = -0.3f

private val PauseMarkStyle = androidx.compose.ui.text.SpanStyle(
    baselineShift = androidx.compose.ui.text.style.BaselineShift(PAUSE_MARK_SHIFT),
)

/**
 * An ayah's display string (from `AyahTextFormatter`), ready to draw: each pause mark (U+06D6…U+06DC)
 * and the space it sits on are drawn a little lower, just above the end of their word. The text is
 * unchanged; only where the marks are drawn.
 */
fun quranDisplay(text: String): androidx.compose.ui.text.AnnotatedString {
    if (text.none { it in '\u06D6'..'\u06DC' }) return androidx.compose.ui.text.AnnotatedString(text)
    val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
    for (i in text.indices) {
        if (text[i] !in '\u06D6'..'\u06DC') continue
        val start = if (i > 0 && text[i - 1].isWhitespace() && text[i - 1] != '\n') i - 1 else i
        builder.addStyle(PauseMarkStyle, start, i + 1)
    }
    return builder.toAnnotatedString()
}
