package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/** The "Numerals" setting of §5.9 / §6.6. Provided by the app shell from user settings. */
enum class NumeralStyle { FollowLanguage, Latin }

val LocalNumeralStyle = staticCompositionLocalOf { NumeralStyle.FollowLanguage }

/**
 * Digit shaping (§6.6): Persian digits in `fa`, Arabic-Indic in `ar`, Latin otherwise.
 * Done by code-point mapping rather than `NumberFormat` so that the result is the same on every
 * Android version and in JVM tests; the numbers shown in this app are plain counts.
 */
object Numerals {
    private const val ARABIC_INDIC_ZERO = 0x0660
    private const val PERSIAN_ZERO = 0x06F0

    /** Replaces every ASCII digit in [text] by the digit of [language] ("fa", "ar", anything else). */
    fun localize(text: String, language: String): String = when (language) {
        "fa" -> mapDigits(text, PERSIAN_ZERO)
        "ar" -> mapDigits(text, ARABIC_INDIC_ZERO)
        else -> text
    }

    fun format(number: Int, language: String): String = localize(number.toString(), language)

    fun arabicIndic(number: Int): String = mapDigits(number.toString(), ARABIC_INDIC_ZERO)

    /** U+06DD followed by Arabic-Indic digits, always, whatever the app language (§5.4.2). */
    fun ayahEndMarker(ayahNumber: Int): String = "۝" + arabicIndic(ayahNumber)

    private fun mapDigits(text: String, zero: Int): String {
        if (text.none { it in '0'..'9' }) return text
        val out = StringBuilder(text.length)
        for (ch in text) {
            if (ch in '0'..'9') out.append((zero + (ch - '0')).toChar()) else out.append(ch)
        }
        return out.toString()
    }
}

@Composable
@ReadOnlyComposable
private fun numeralLanguage(): String =
    if (LocalNumeralStyle.current == NumeralStyle.Latin) "en" else LocalConfiguration.current.locales[0].language

/** A count in the display numerals of the current language and numeral setting. */
@Composable
@ReadOnlyComposable
fun formatNumber(number: Int): String = Numerals.format(number, numeralLanguage())

/** Any text containing ASCII digits ("08:00–12:00", "2.5", "33%") in the display numerals. */
@Composable
@ReadOnlyComposable
fun localizeDigits(text: String): String = Numerals.localize(text, numeralLanguage())
