package io.github.mehrdad_abdi.qintar.feature.reader

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex

/** One item of the reader's list (§7.7). [key] is stable and unique within a model. */
sealed interface ReaderRow {
    /** Index of the segment this row belongs to. */
    val segment: Int
    val key: String

    /** Start of a segment of a group: the bookmark's title stands here. */
    data class SegmentHeader(override val segment: Int, val context: ReadContext) : ReaderRow {
        override val key get() = "segment-$segment"
    }

    data class SurahHeader(override val segment: Int, val surah: SurahInfo) : ReaderRow {
        override val key get() = "surah-$segment-${surah.number}"
    }

    /** Before ayah 1 of every surah except 1 and 9. Not an ayah: no check, never counted. */
    data class Basmala(override val segment: Int, val surah: Int) : ReaderRow {
        override val key get() = "basmala-$segment-$surah"
    }

    data class Ayah(
        override val segment: Int,
        val id: AyahId,
        /** What a tick on this row is counted under. */
        val context: ReadContext,
        val surah: Int,
        val number: Int,
        val page: Int,
        val juz: Int,
        val sajda: SajdaType,
    ) : ReaderRow {
        override val key get() = "ayah-$segment-${id.value}"
    }

    /** Stands before the first ayah of [page] when the page changes inside a segment. */
    data class PageDivider(
        override val segment: Int,
        val page: Int,
        val juz: Int,
        /** The hizb (1…60) when it changes at this page, otherwise null. */
        val hizb: Int?,
        /** A juz begins on this page: it gets the prominent label. */
        val startsJuz: Boolean,
    ) : ReaderRow {
        override val key get() = "page-$segment-$page"
    }
}

/**
 * The reader's list, computed from [QuranIndex] alone — no text — so that it knows its full size and
 * the position of any ayah at once, even for the whole Quran (§7.7).
 */
class ReaderRowModel private constructor(
    val segments: List<Segment>,
    val rows: List<ReaderRow>,
    private val surahs: List<SurahInfo>,
    /** Per segment: row index of each of its ayahs, in order. */
    private val ayahRows: List<IntArray>,
) {
    fun surahOf(row: ReaderRow.Ayah): SurahInfo = surahs[row.surah - 1]

    /** Row index of [ayah] in [segment]; null when the segment does not contain it. */
    fun indexOf(segment: Int, ayah: AyahId): Int? {
        val range = segments.getOrNull(segment)?.range ?: return null
        return if (ayah in range) ayahRows[segment][ayah.value - range.start.value] else null
    }

    /** The first segment that contains [ayah], with its row index. */
    fun find(ayah: AyahId): Int? = segments.indices.firstNotNullOfOrNull { indexOf(it, ayah) }

    /**
     * Where a view that wants to show the row [rowIndex] should start: the surah header, basmala and
     * page divider that stand directly above an ayah belong to it, so that opening at the first ayah
     * of a surah shows its header instead of scrolling it off.
     */
    fun blockStart(rowIndex: Int): Int {
        var i = rowIndex.coerceIn(0, rows.lastIndex)
        while (i > 0 && rows[i - 1].let { it is ReaderRow.Basmala || it is ReaderRow.SurahHeader || it is ReaderRow.PageDivider }) i--
        return i
    }

    /** The ayah row at [rowIndex], or the nearest one after it (then before it). */
    fun ayahAtOrAfter(rowIndex: Int): ReaderRow.Ayah? {
        for (i in rowIndex.coerceAtLeast(0) until rows.size) (rows[i] as? ReaderRow.Ayah)?.let { return it }
        for (i in rowIndex.coerceAtMost(rows.lastIndex) downTo 0) (rows[i] as? ReaderRow.Ayah)?.let { return it }
        return null
    }

    companion object {
        /** Hizb quarters come four to a hizb. */
        fun hizbOf(quarter: Int): Int = (quarter - 1) / 4 + 1

        /**
         * @param segmentHeaders a header row at the start of each segment; on by default for a group.
         */
        fun build(
            segments: List<Segment>,
            index: QuranIndex,
            segmentHeaders: Boolean = segments.size > 1,
        ): ReaderRowModel {
            val rows = ArrayList<ReaderRow>(segments.sumOf { it.range.size } + 16)
            val ayahRows = ArrayList<IntArray>(segments.size)
            segments.forEachIndexed { s, segment ->
                if (segmentHeaders) rows += ReaderRow.SegmentHeader(s, segment.context)
                val positions = IntArray(segment.range.size)
                var previous: AyahId? = null
                var previousPage = 0
                for (id in segment.range) {
                    val ref = index.ref(id)
                    val page = index.pageOf(id)
                    val juz = index.juzOf(id)
                    if (previous != null && page != previousPage) {
                        val hizb = hizbOf(index.hizbQuarterOf(id))
                        rows += ReaderRow.PageDivider(
                            segment = s,
                            page = page,
                            juz = juz,
                            hizb = hizb.takeIf { it != hizbOf(index.hizbQuarterOf(previous!!)) },
                            startsJuz = juz != index.juzOf(previous!!),
                        )
                    }
                    if (ref.ayah == 1) {
                        val surah = index.surah(ref.surah)
                        rows += ReaderRow.SurahHeader(s, surah)
                        if (surah.hasBasmala) rows += ReaderRow.Basmala(s, surah.number)
                    }
                    positions[id.value - segment.range.start.value] = rows.size
                    rows += ReaderRow.Ayah(s, id, segment.context, ref.surah, ref.ayah, page, juz, index.sajdaOf(id))
                    previous = id
                    previousPage = page
                }
                ayahRows += positions
            }
            return ReaderRowModel(segments, rows, index.surahs, ayahRows)
        }
    }
}
