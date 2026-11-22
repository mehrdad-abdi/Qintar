package io.github.mehrdad_abdi.qintar.domain.service

/**
 * Builds the display string of an ayah (§5.4.2). The stored text is never altered: the only change ever
 * made to it is that some of its spaces become line breaks or no-break spaces, so
 * `withPauseBreaks(text).replace('\n', ' ') == text` always holds, and so does the same for the whole
 * display string once its no-break spaces are turned back into spaces.
 */
object AyahTextFormatter {
    /** U+06DD ARABIC END OF AYAH; the Quran font draws it around the digits that follow. */
    const val END_OF_AYAH = '۝'

    /**
     * Marks after which the reader may pause: ṣalā (ۖ), qalā (ۗ), mīm (ۘ), jīm (ۚ).
     * Deliberately absent: U+06D9 lā ("do not stop") and U+06DB (the paired three dots).
     */
    private const val BREAKING_MARKS = "ۖۗۘۚ"

    /** Every small high pause sign, including lā and the paired dots: all of them cling to their word. */
    private val PAUSE_MARKS = '\u06D6'..'\u06DC'

    /** U+00A0 NO-BREAK SPACE. */
    const val NBSP = '\u00A0'

    private const val ARABIC_INDIC_ZERO = '٠'

    /**
     * The ayah as shown in the reader: its text, a space, then the ayah-end marker.
     * Breaks are decided on the text alone, so the marker never starts a line of its own because of them.
     */
    fun format(text: String, ayahNumber: Int, breakAtPauseMarks: Boolean): String {
        val tied = tiePauseMarks(text)
        val body = if (breakAtPauseMarks) withPauseBreaks(tied) else tied
        return body + ' ' + ayahEndMarker(ayahNumber)
    }

    /**
     * Ties every pause mark (U+06D6…U+06DC, breaking or not) to the word before it: the space in front
     * of the mark becomes a no-break space, so a mark never wraps to a line of its own.
     * `tiePauseMarks(text).replace(NBSP, ' ') == text` always holds.
     */
    fun tiePauseMarks(text: String): String {
        var result: StringBuilder? = null
        for (i in 1 until text.length) {
            if (text[i] in PAUSE_MARKS && text[i - 1] == ' ') {
                if (result == null) result = StringBuilder(text)
                result.setCharAt(i - 1, NBSP)
            }
        }
        return result?.toString() ?: text
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
