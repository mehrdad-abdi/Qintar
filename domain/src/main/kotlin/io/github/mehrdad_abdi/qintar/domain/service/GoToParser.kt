package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex

sealed interface GoToResult {
    /** Where to open the reader. */
    val target: AyahId? get() = null

    data class Ayah(val ref: AyahRef, override val target: AyahId) : GoToResult

    data class Page(val page: Int, override val target: AyahId) : GoToResult

    data class Juz(val juz: Int, override val target: AyahId) : GoToResult

    data class Invalid(val reason: Reason) : GoToResult

    enum class Reason {
        EMPTY,

        /** Not one of the accepted forms. */
        UNRECOGNIZED,

        /** A bare ayah number was typed but no surah is chosen. */
        NEEDS_SURAH,
        SURAH_OUT_OF_RANGE,

        /** The surah has no ayah with that number. */
        AYAH_OUT_OF_RANGE,
        PAGE_OUT_OF_RANGE,
        JUZ_OUT_OF_RANGE,
    }
}

/**
 * "Go to…" (§5.3): `2:255`, `255` within a chosen surah, `p300`, `j12`.
 *
 * Digits may be Latin, Arabic-Indic or Persian, since the field is typed with whatever keyboard the
 * user has; `p` and `j` are accepted in either case, and spaces are ignored.
 */
object GoToParser {
    private val SURAH_AYAH = Regex("""(\p{Nd}+):(\p{Nd}+)""")
    private val NUMBER = Regex("""\p{Nd}+""")
    private val PREFIXED = Regex("""([pPjJ])(\p{Nd}+)""")

    fun parse(input: String, currentSurah: Int?, index: QuranIndex): GoToResult {
        val text = input.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return GoToResult.Invalid(GoToResult.Reason.EMPTY)

        SURAH_AYAH.matchEntire(text)?.let { match ->
            return ayah(number(match.groupValues[1]), number(match.groupValues[2]), index)
        }
        PREFIXED.matchEntire(text)?.let { match ->
            val n = number(match.groupValues[2])
            return if (match.groupValues[1].lowercase() == "p") {
                if (n != null && n in 1..Quran.PAGE_COUNT) {
                    GoToResult.Page(n, index.pageRange(n).start)
                } else {
                    GoToResult.Invalid(GoToResult.Reason.PAGE_OUT_OF_RANGE)
                }
            } else {
                if (n != null && n in 1..Quran.JUZ_COUNT) {
                    GoToResult.Juz(n, index.juzRange(n).start)
                } else {
                    GoToResult.Invalid(GoToResult.Reason.JUZ_OUT_OF_RANGE)
                }
            }
        }
        if (NUMBER.matches(text)) {
            currentSurah ?: return GoToResult.Invalid(GoToResult.Reason.NEEDS_SURAH)
            return ayah(currentSurah, number(text), index)
        }
        return GoToResult.Invalid(GoToResult.Reason.UNRECOGNIZED)
    }

    private fun ayah(surah: Int?, ayah: Int?, index: QuranIndex): GoToResult {
        if (surah == null || surah !in 1..Quran.SURAH_COUNT) return GoToResult.Invalid(GoToResult.Reason.SURAH_OUT_OF_RANGE)
        if (ayah == null || ayah < 1) return GoToResult.Invalid(GoToResult.Reason.AYAH_OUT_OF_RANGE)
        val ref = AyahRef(surah, ayah)
        val id = index.ayahId(ref) ?: return GoToResult.Invalid(GoToResult.Reason.AYAH_OUT_OF_RANGE)
        return GoToResult.Ayah(ref, id)
    }

    /** Null when the digits are too many to be any valid number, which callers report as out of range. */
    private fun number(digits: String): Int? {
        if (digits.length > 6) return null
        return digits.fold(0) { acc, c -> acc * 10 + Character.digit(c, 10) }
    }
}
