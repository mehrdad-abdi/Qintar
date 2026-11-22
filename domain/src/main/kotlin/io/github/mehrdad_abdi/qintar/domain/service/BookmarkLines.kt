package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex

/** One line of the bulk "add bookmarks" box: a bookmark to create, or the line number that is not one. */
sealed interface BookmarkLine {
    val line: Int

    data class Ok(override val line: Int, val draft: BookmarkDraft) : BookmarkLine

    data class Problem(override val line: Int) : BookmarkLine
}

/**
 * The bulk box of the group editor (owner, 2026-11-22): one bookmark per line, in the forms of
 * "Go to…" — `2:255` an ayah, `2:255-257` a range within one surah, `s36` a surah, `p300` a page —
 * with the Persian and Arabic letters and digits [GoToParser] accepts, and `/` for the colon. A juz is
 * not offered as a bookmark, so `j12` is a problem here. Blank lines are skipped.
 */
object BookmarkLines {
    private val RANGE = Regex("""(\p{Nd}+)[:/](\p{Nd}+)[-–—](\p{Nd}+)""")
    private val SURAH = Regex("""(s|س|سوره|سورة)(\p{Nd}+)""", RegexOption.IGNORE_CASE)

    fun parse(text: String, index: QuranIndex): List<BookmarkLine> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.filterNot { it.isWhitespace() }
            if (line.isEmpty()) null else parseLine(i + 1, line, index)
        }

    private fun parseLine(number: Int, line: String, index: QuranIndex): BookmarkLine {
        RANGE.matchEntire(line)?.let { match ->
            val (s, a, b) = match.destructured
            val surah = digits(s) ?: return BookmarkLine.Problem(number)
            val from = digits(a)?.let { index.ayahId(AyahRef(surah, it)) } ?: return BookmarkLine.Problem(number)
            val to = digits(b)?.let { index.ayahId(AyahRef(surah, it)) } ?: return BookmarkLine.Problem(number)
            if (surah !in 1..Quran.SURAH_COUNT || to < from) return BookmarkLine.Problem(number)
            val draft = if (from == to) BookmarkDrafts.ayah(from) else BookmarkDrafts.range(from, to)
            return BookmarkLine.Ok(number, draft)
        }
        SURAH.matchEntire(line)?.let { match ->
            val surah = digits(match.groupValues[2])
            return if (surah != null && surah in 1..Quran.SURAH_COUNT) {
                BookmarkLine.Ok(number, BookmarkDrafts.surah(surah, index))
            } else {
                BookmarkLine.Problem(number)
            }
        }
        return when (val result = GoToParser.parse(line, currentSurah = null, index = index)) {
            is GoToResult.Ayah -> BookmarkLine.Ok(number, BookmarkDrafts.ayah(result.target))
            is GoToResult.Page -> BookmarkLine.Ok(number, BookmarkDrafts.page(result.page, index))
            is GoToResult.Juz, is GoToResult.Invalid -> BookmarkLine.Problem(number)
        }
    }

    private fun digits(text: String): Int? =
        if (text.isEmpty() || text.length > 6) null else text.fold(0) { acc, c -> acc * 10 + Character.digit(c, 10) }
}
