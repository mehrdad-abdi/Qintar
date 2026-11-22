package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRecency
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Recency of one mushaf page (§4.8). */
data class PageRecency(
    val page: Int,
    val range: AyahRange,
    /** Numbers of the surahs that have ayahs on this page, in order. */
    val surahs: List<Int>,
    val ayahCount: Int,
    val neverRead: Int,
    /**
     * The minimum of `lastRead` over the page's ayahs, where never-read is older than everything:
     * null as soon as one ayah has never been read.
     */
    val oldest: LocalDate?,
    /** The most recent reading of any ayah on the page; null if none was ever read. Tints the page grid. */
    val newest: LocalDate?,
) {
    /** Share of the page's ayahs ever read. */
    val coverage: Float get() = (ayahCount - neverRead).toFloat() / ayahCount
}

data class Revisit(
    /** All 604 pages in mushaf order. */
    val pages: List<PageRecency>,
) {
    /** Pages with every ayah read at least once, the one read longest ago first. */
    val readLongAgo: List<PageRecency>
        get() = pages.filter { it.neverRead == 0 }.sortedWith(compareBy<PageRecency> { it.oldest }.thenBy { it.page })

    /** Pages with any never-read ayah, in mushaf order. */
    val notReadYet: List<PageRecency> get() = pages.filter { it.neverRead > 0 }
}

/** Per-ayah recency aggregated to pages and surahs. Tallies have no ayah identity and play no part. */
class RevisitRanker @Inject constructor(
    private val log: ReadLogStore,
    private val index: QuranIndex,
) {
    fun observe(): Flow<Revisit> = log.observeRecency(AyahRange.ALL).map { rank(it, index) }

    /** "Last read 12 Nov · 7 times" for the ayah sheet; null if the ayah was never read. */
    fun observeAyah(ayah: AyahId): Flow<AyahRecency?> =
        log.observeRecency(AyahRange.single(ayah)).map { it.firstOrNull() }

    /** Share of each surah's ayahs ever read, by surah number: the thin bar in the surah index (§5.3). */
    fun observeSurahCoverage(): Flow<Map<Int, Float>> =
        log.observeRecency(AyahRange.ALL).map { surahCoverage(it, index) }

    companion object {
        fun rank(recency: List<AyahRecency>, index: QuranIndex): Revisit {
            val lastRead = arrayOfNulls<LocalDate>(Quran.AYAH_COUNT + 1)
            for (row in recency) lastRead[row.ayahId.value] = row.lastRead
            val pages = (1..Quran.PAGE_COUNT).map { page ->
                val range = index.pageRange(page)
                var neverRead = 0
                var oldest: LocalDate? = null
                var newest: LocalDate? = null
                for (id in range.ids) {
                    val day = lastRead[id]
                    if (day == null) {
                        neverRead++
                    } else {
                        if (oldest == null || day < oldest) oldest = day
                        if (newest == null || day > newest) newest = day
                    }
                }
                PageRecency(
                    page = page,
                    range = range,
                    surahs = (index.ref(range.start).surah..index.ref(range.end).surah).toList(),
                    ayahCount = range.size,
                    neverRead = neverRead,
                    oldest = oldest.takeIf { neverRead == 0 },
                    newest = newest,
                )
            }
            return Revisit(pages)
        }

        fun surahCoverage(recency: List<AyahRecency>, index: QuranIndex): Map<Int, Float> {
            val readPerSurah = IntArray(Quran.SURAH_COUNT + 1)
            for (row in recency) readPerSurah[index.ref(row.ayahId).surah]++
            return index.surahs.associate { it.number to readPerSurah[it.number].toFloat() / it.ayahCount }
        }
    }
}
