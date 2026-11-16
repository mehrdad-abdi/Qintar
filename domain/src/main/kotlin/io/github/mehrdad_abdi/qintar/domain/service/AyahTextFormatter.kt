package io.github.mehrdad_abdi.qintar.domain.service

/**
 * Builds the display string of an ayah (§5.4.2). The stored text is never altered: the only change ever
 * made to it is that some of its spaces become line breaks, so
 * `withPauseBreaks(text).replace('\n', ' ') == text` always holds.
 */
object AyahTextFormatter {
    /** U+06DD ARABIC END OF AYAH; the Quran font draws it around the digits that follow. */
    const val END_OF_AYAH = '۝'

    /**
     * Marks after which the reader may pause: ṣalā (ۖ), qalā (ۗ), mīm (ۘ), jīm (ۚ).
     * Deliberately absent: U+06D9 lā ("do not stop") and U+06DB (the paired three dots).
     */
    private const val BREAKING_MARKS = "ۖۗۘۚ"

    private const val ARABIC_INDIC_ZERO = '٠'

    /**
     * The ayah as shown in the reader: its text, a space, then the ayah-end marker.
     * Breaks are decided on the text alone, so the marker never starts a line of its own because of them.
     */
    fun format(text: String, ayahNumber: Int, breakAtPauseMarks: Boolean): String {
        val body = if (breakAtPauseMarks) withPauseBreaks(text) else text
        return body + ' ' + ayahEndMarker(ayahNumber)
    }

    /** Turns the first space after each breaking pause mark into a line break. */
    fun withPauseBreaks(text: String): String {
        var result: StringBuilder? = null
        var breakAtNextSpace = false
        for (i in text.indices) {
            val c = text[i]
            if (c in BREAKING_MARKS) {
                breakAtNextSpace = true
            } else if (c == ' ' && breakAtNextSpace) {
                if (result == null) result = StringBuilder(text)
                result.setCharAt(i, '\n')
                breakAtNextSpace = false
            }
        }
        return result?.toString() ?: text
    }

    /** U+06DD followed by the number in Arabic-Indic digits — always, whatever the app language. */
    fun ayahEndMarker(ayahNumber: Int): String = END_OF_AYAH + arabicIndic(ayahNumber)

    fun arabicIndic(number: Int): String {
        require(number >= 0) { "Negative number: $number" }
        return number.toString().map { ARABIC_INDIC_ZERO + (it - '0') }.joinToString("")
    }
}
