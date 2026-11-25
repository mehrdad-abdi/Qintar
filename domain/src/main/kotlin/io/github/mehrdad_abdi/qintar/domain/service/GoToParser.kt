package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex

sealed interface GoToResult {
    /** Where to open the reader. */
    val target: AyahId? get() = null

    data class Ayah(val ref: AyahRef, override val target: AyahId) : GoToResult

    /** A whole surah: `36`, `s36` or its name; the reader opens at its first ayah. */
    data class Surah(val surah: Int, override val target: AyahId) : GoToResult

    data class Page(val page: Int, override val target: AyahId) : GoToResult

    data class Juz(val juz: Int, override val target: AyahId) : GoToResult

    data class Invalid(val reason: Reason) : GoToResult

    enum class Reason {
        EMPTY,

        /** Not one of the accepted forms. */
        UNRECOGNIZED,
        SURAH_OUT_OF_RANGE,

        /** The surah has no ayah with that number. */
        AYAH_OUT_OF_RANGE,
        PAGE_OUT_OF_RANGE,
        JUZ_OUT_OF_RANGE,
    }
}

/**
 * "Go to…" (§5.3): `2:255`, `36` or `s36` (a surah), `p300`, `j12`, or a surah's name with or
 * without an ayah number (`yasin 12`, `یس ۱۲`; [SurahNames]). Every form works whatever the app's
 * language.
 *
 * Digits may be Latin, Arabic-Indic or Persian, since the field is typed with whatever keyboard the
 * user has; `p` and `j` are accepted in either case, and spaces are ignored. On a Persian or Arabic
 * keyboard (owner, 2026-11-22): `ج12` or `جزء ۱۲` is a juz, `ص300`, `صفحه ۳۰۰` or `صفحة ٣٠٠` a page,
 * `س36`, `سوره ۳۶` or `سورة ٣٦` a surah,
 * and `/` may stand for the colon.
 */
object GoToParser {
    private val SURAH_AYAH = Regex("""(\p{Nd}+)[:/](\p{Nd}+)""")
    private val NUMBER = Regex("""\p{Nd}+""")
    private val PREFIXED = Regex("""(p|j|s|ج|جزء|ص|صفحه|صفحة|س|سوره|سورة)(\p{Nd}+)""", RegexOption.IGNORE_CASE)
    private val PAGE_PREFIXES = setOf("p", "ص", "صفحه", "صفحة")
    private val SURAH_PREFIXES = setOf("s", "س", "سوره", "سورة")

    /** A name, then maybe an ayah number: "yasin12", "یس:۱۲". */
    private val NAMED = Regex("""(.*?\p{L}.*?)[:/]?(\p{Nd}*)""")

    /** "surah" typed before a name or number is forgiven. */
    private val SURAH_WORDS = listOf("surah", "sura", "سوره", "سورة")

    /**
     * @param surahs where names are looked up; the index's own, except in tests.
     */
    fun parse(input: String, index: QuranIndex, surahs: List<SurahInfo> = index.surahs): GoToResult {
        val text = input.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return GoToResult.Invalid(GoToResult.Reason.EMPTY)

        SURAH_AYAH.matchEntire(text)?.let { match ->
            return ayah(number(match.groupValues[1]), number(match.groupValues[2]), index)
        }
        PREFIXED.matchEntire(text)?.let { match ->
            val n = number(match.groupValues[2])
            if (match.groupValues[1].lowercase() in SURAH_PREFIXES) return surah(n, index)
            return if (match.groupValues[1].lowercase() in PAGE_PREFIXES) {
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
        if (NUMBER.matches(text)) return surah(number(text), index)
        NAMED.matchEntire(text)?.let { match ->
            val name = withoutSurahWord(match.groupValues[1])
            val digits = match.groupValues[2]
            if (name.isEmpty()) return if (digits.isEmpty()) GoToResult.Invalid(GoToResult.Reason.UNRECOGNIZED) else surah(number(digits), index)
            val found = SurahNames.search(name, surahs).firstOrNull() ?: return GoToResult.Invalid(GoToResult.Reason.UNRECOGNIZED)
            return if (digits.isEmpty()) surah(found.number, index) else ayah(found.number, number(digits), index)
        }
        return GoToResult.Invalid(GoToResult.Reason.UNRECOGNIZED)
    }

    /**
     * The surahs the typed name may be, closest first, while no number has been typed after it: the
     * drop-down under the field. Empty for every other form.
     */
    fun suggestions(input: String, index: QuranIndex, surahs: List<SurahInfo> = index.surahs): List<SurahInfo> {
        val text = input.filterNot { it.isWhitespace() }
        if (text.isEmpty() || text.any { it.isDigit() }) return emptyList()
        val name = withoutSurahWord(text)
        return if (name.isEmpty()) emptyList() else SurahNames.search(name, surahs)
    }

    private fun withoutSurahWord(name: String): String {
        val word = SURAH_WORDS.firstOrNull { name.lowercase().startsWith(it) } ?: return name
        return name.substring(word.length)
    }

    private fun surah(n: Int?, index: QuranIndex): GoToResult =
        if (n != null && n in 1..Quran.SURAH_COUNT) GoToResult.Surah(n, index.surahRange(n).start) else GoToResult.Invalid(GoToResult.Reason.SURAH_OUT_OF_RANGE)

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
