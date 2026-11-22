package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLine
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLines
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import org.junit.Assert.assertEquals
import org.junit.Test

/** The group editor's bulk box: one bookmark per line, in the forms of "Go to". */
class BookmarkLinesTest {
    private val app = DomainFixture()
    private val index = app.index

    private fun drafts(text: String) = BookmarkLines.parse(text, index).map { (it as BookmarkLine.Ok).draft }

    @Test
    fun `an ayah, a range in one surah, a surah and a page, one per line`() {
        val result = drafts(
            """
            2:255
            2:255-257
            s36
            p300
            """.trimIndent(),
        )
        assertEquals(listOf(BookmarkKind.AYAH, BookmarkKind.RANGE, BookmarkKind.SURAH, BookmarkKind.PAGE), result.map { it.kind })
        assertEquals(AyahRange.single(app.ayah(2, 255)), result[0].range)
        assertEquals(AyahRange(app.ayah(2, 255), app.ayah(2, 257)), result[1].range)
        assertEquals(index.surahRange(36), result[2].range)
        assertEquals(index.pageRange(300), result[3].range)
    }

    @Test
    fun `Persian and Arabic letters and digits, slashes and blank lines`() {
        val result = drafts("۲/۲۵۵\n\n  س۳۶  \nسورة ٦٧\nص۳۰۰\n٢:٢٥٥–٢٥٧\n")
        assertEquals(5, result.size)
        assertEquals(AyahRange.single(app.ayah(2, 255)), result[0].range)
        assertEquals(index.surahRange(36), result[1].range)
        assertEquals(index.surahRange(67), result[2].range)
        assertEquals(index.pageRange(300), result[3].range)
        assertEquals(AyahRange(app.ayah(2, 255), app.ayah(2, 257)), result[4].range)
    }

    @Test
    fun `lines that are not a bookmark are reported by their number`() {
        val result = BookmarkLines.parse("2:255\nhello\nj12\n2:257-255\n2:290\ns115\n255", index)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), result.filterIsInstance<BookmarkLine.Problem>().map { it.line })
    }
}
