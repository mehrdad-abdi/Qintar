package io.github.mehrdad_abdi.qintar.feature.reader

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import io.github.mehrdad_abdi.qintar.domain.testing.QuranFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRowModelTest {
    private val index = QuranFixture.index

    private fun model(vararg ranges: AyahRange, context: (Int) -> ReadContext = { ReadContext.Browse }) =
        ReaderRowModel.build(ranges.mapIndexed { i, range -> Segment(context(i), range) }, index)

    /** The text rows; the end-of-surah and end-of-bookmark actions are tested in ReaderGapsViewModelTest. */
    private fun ReaderRowModel.shape(): List<String> = rows.mapNotNull { row ->
        when (row) {
            is ReaderRow.SegmentHeader -> "segment ${row.segment}"
            is ReaderRow.SurahHeader -> "surah ${row.surah.number}"
            is ReaderRow.Basmala -> "basmala"
            is ReaderRow.Ayah -> "${row.surah}:${row.number}"
            is ReaderRow.PageDivider -> "page ${row.page}"
            is ReaderRow.SegmentEnd, is ReaderRow.SurahEnd -> null
        }
    }

    @Test
    fun `surah 1 has a header and no basmala row, because its basmala is ayah 1`() {
        val shape = model(index.surahRange(1)).shape()

        assertEquals(listOf("surah 1", "1:1", "1:2", "1:3", "1:4", "1:5", "1:6", "1:7"), shape)
    }

    @Test
    fun `a surah boundary inside the scope gets a header and the basmala before ayah 1`() {
        val shape = model(AyahRange(QuranFixture.ayah(1, 6), QuranFixture.ayah(2, 2))).shape()

        assertEquals(listOf("1:6", "1:7", "surah 2", "basmala", "2:1", "2:2"), shape)
    }

    @Test
    fun `surah 9 has a header and no basmala`() {
        val shape = model(AyahRange(QuranFixture.ayah(8, 75), QuranFixture.ayah(9, 2))).shape()

        assertEquals(listOf("8:75", "surah 9", "9:1", "9:2"), shape.filterNot { it.startsWith("page") })
        assertFalse("basmala" in shape)
    }

    @Test
    fun `a scope that starts inside a surah starts with its first ayah`() {
        val kursi = QuranFixture.ayah(2, 255)

        assertEquals(listOf("2:255"), model(AyahRange.single(kursi)).shape())
    }

    @Test
    fun `the basmala is a row of its own and never an ayah`() {
        val model = model(index.surahRange(112))

        assertEquals(listOf("surah 112", "basmala", "112:1", "112:2", "112:3", "112:4"), model.shape())
        assertEquals("only ayahs can be ticked", 4, model.rows.count { it is ReaderRow.Ayah })
    }

    @Test
    fun `a page divider stands before the first ayah of each new page`() {
        // The fixture puts 10 or 11 ayahs on a page: page 2 starts inside al-Baqarah.
        val model = model(AyahRange.of(1, 40))
        val rows = model.rows

        val dividers = rows.filterIsInstance<ReaderRow.PageDivider>()
        assertEquals(listOf(2, 3, 4), dividers.map { it.page })
        for (divider in dividers) {
            val next = rows[rows.indexOf(divider) + 1] as ReaderRow.Ayah
            val before = rows.subList(0, rows.indexOf(divider)).last { it is ReaderRow.Ayah } as ReaderRow.Ayah
            assertEquals(divider.page, next.page)
            assertEquals(divider.page - 1, before.page)
        }
        assertFalse("no divider before the very first ayah", rows.first() is ReaderRow.PageDivider)
    }

    @Test
    fun `when a surah starts on a new page the divider comes before its header`() {
        val all = model(AyahRange.ALL)
        val rows = all.rows
        val headersAfterDivider = rows.indices.count { i ->
            rows[i] is ReaderRow.SurahHeader && i > 0 && rows[i - 1] is ReaderRow.PageDivider
        }
        val dividerAfterHeaderOrBasmala = rows.indices.any { i ->
            rows[i] is ReaderRow.PageDivider && i > 0 && (rows[i - 1] is ReaderRow.SurahHeader || rows[i - 1] is ReaderRow.Basmala)
        }

        assertTrue("the fixture has surahs that start a page", headersAfterDivider > 0)
        assertFalse(dividerAfterHeaderOrBasmala)
    }

    @Test
    fun `the whole Quran is 6236 ayahs, 114 headers, 112 basmalas and 603 page dividers, with unique keys`() {
        val rows = model(AyahRange.ALL).rows

        assertEquals(6236, rows.count { it is ReaderRow.Ayah })
        assertEquals(114, rows.count { it is ReaderRow.SurahHeader })
        assertEquals(112, rows.count { it is ReaderRow.Basmala })
        assertEquals(603, rows.count { it is ReaderRow.PageDivider })
        assertEquals(29, rows.count { it is ReaderRow.PageDivider && it.startsJuz })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `any ayah's row is found without scanning`() {
        val model = model(AyahRange.ALL)
        val ayah = QuranFixture.ayah(36, 1)

        val row = model.rows[model.indexOf(0, ayah)!!] as ReaderRow.Ayah

        assertEquals(ayah, row.id)
        assertEquals(36 to 1, row.surah to row.number)
        assertNull(model(index.surahRange(1)).indexOf(0, ayah))
    }

    @Test
    fun `a group is one run of rows per bookmark, each under its own header and context`() {
        val ikhlas = index.surahRange(112)
        val kursi = AyahRange.single(QuranFixture.ayah(2, 255))
        val model = model(kursi, ikhlas, kursi) { ReadContext.Bookmark(10L + it) }

        assertEquals(
            listOf("segment 0", "2:255", "segment 1", "surah 112", "basmala", "112:1", "112:2", "112:3", "112:4", "segment 2", "2:255"),
            model.shape(),
        )
        val kursiRows = model.rows.filterIsInstance<ReaderRow.Ayah>().filter { it.id == kursi.start }
        assertEquals(listOf(ReadContext.Bookmark(10), ReadContext.Bookmark(12)), kursiRows.map { it.context })
        assertEquals("the same ayah twice still has two keys", 2, kursiRows.map { it.key }.toSet().size)
        // Each bookmark ends with its Mark read row, so the third one starts two rows later.
        assertEquals(12, model.indexOf(2, kursi.start))
    }

    @Test
    fun `sajda ayahs are flagged`() {
        val row = model(index.surahRange(96)).rows.last { it is ReaderRow.Ayah } as ReaderRow.Ayah

        assertEquals(19, row.number)
        assertTrue(row.sajda != io.github.mehrdad_abdi.qintar.domain.model.SajdaType.NONE)
    }

    @Test
    fun `the shown text keeps every pause mark on the line of the word before it`() {
        val sala = 'ۖ'
        val la = 'ۙ'
        val shown = AyahTextFormatter.format("alpha $sala beta gamma $la delta", 1, breakAtPauseMarks = true)

        assertEquals("alpha $sala\nbeta gamma $la delta ۝١", shown)
        assertFalse("no mark is preceded by a breakable space", Regex(" [ۖ-ۜ]").containsMatchIn(shown))
    }
}
