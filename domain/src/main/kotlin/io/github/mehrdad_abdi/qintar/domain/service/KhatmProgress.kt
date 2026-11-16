package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahDayCount
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadEvent
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A part of the Quran and how much of it a khatm has covered. */
data class CoverageBar(val covered: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else covered.toFloat() / total
}

/** One reading day of a khatm (R-KHT-3). */
data class KhatmDay(
    val day: LocalDate,
    /** Events in this khatm that day; re-readings count. */
    val ayahs: Int,
    /** Σ over those events of 1 / ayahsOnPage(page of the ayah). */
    val pages: Double,
)

/** Shown on the completion screen. */
data class KhatmCompletion(
    val startedDay: LocalDate,
    val completedDay: LocalDate,
    /** Inclusive of both the first and the last day. */
    val daysTaken: Int,
    val averageAyahsPerDay: Double,
)

/** Everything derived about one khatm at one moment. Nothing here is stored. */
data class KhatmSnapshot(
    val khatm: Khatm,
    val today: LocalDate,
    val coveredAyahs: Int,
    /** The ayah after the most recently recorded event; null when that event was the last ayah of the Quran and nothing is left unread. */
    val resume: AyahId?,
    /** The smallest uncovered ayah; null when complete. */
    val firstUnread: AyahId?,
    val isComplete: Boolean,
    /** Moment of the newest event in this khatm; null before the first. */
    val lastActivity: Instant?,
    val days: List<KhatmDay>,
    /** Surah number and bar for the surah containing the resume position. */
    val currentSurah: Int,
    val surahBar: CoverageBar,
    val currentJuz: Int,
    val juzBar: CoverageBar,
    /** Covered share of each of the 30 juz, for the strip in Khatm detail. */
    val juzCoverage: List<Float>,
    val remainingPages: Double,
    /** Null when complete, or when there is neither a goal nor any reading to estimate a pace from. */
    val projectedFinish: LocalDate?,
    val completion: KhatmCompletion?,
) {
    val wholeBar: CoverageBar get() = CoverageBar(coveredAyahs, Quran.AYAH_COUNT)

    val progress: Float get() = wholeBar.fraction

    val todayAyahs: Int get() = days.lastOrNull { it.day == today }?.ayahs ?: 0

    val todayPages: Double get() = days.lastOrNull { it.day == today }?.pages ?: 0.0

    /** Pages read today ÷ the daily goal. Null (bar hidden) without a goal; may exceed 1. */
    val todayGoalFraction: Float? get() = khatm.dailyPages?.let { (todayPages / it).toFloat() }

    /** "First unread" is offered as a jump only when it is somewhere else than the resume position. */
    val firstUnreadDiffersFromResume: Boolean get() = firstUnread != null && firstUnread != resume
}

/** Coverage, position, statistics and pace of khatms (§4.6), always derived from the log. */
class KhatmProgress @Inject constructor(
    private val log: ReadLogStore,
    private val khatms: KhatmStore,
    private val index: QuranIndex,
    private val clock: DayClock,
) {
    /** Emits null while the khatm does not exist (for example after it is deleted). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(khatmId: Long): Flow<KhatmSnapshot?> =
        khatms.observeKhatms().map { list -> list.firstOrNull { it.id == khatmId } }.distinctUntilChanged()
            .flatMapLatest { khatm -> if (khatm == null) flowOf(null) else observe(khatm) }

    private fun observe(khatm: Khatm): Flow<KhatmSnapshot> {
        val context = ReadContext.Khatm(khatm.id)
        return combine(
            log.observeDistinctAyahs(context, AyahRange.ALL),
            log.observeNewestEvent(context),
            log.observeAyahDayCounts(context, AyahRange.ALL, null, null),
            clock.todayFlow,
        ) { covered, newest, dayCounts, today -> compute(khatm, covered, newest, dayCounts, today, index) }
    }

    suspend fun snapshot(khatmId: Long): KhatmSnapshot? = observe(khatmId).first()

    /**
     * Khatms still in progress, most recent activity first. The first one is the "primary" khatm that
     * Today shows in full.
     */
    fun observeActive(): Flow<List<KhatmSnapshot>> = observeAll().map { all ->
        all.filter { it.khatm.completedAt == null }
            .sortedWith(compareByDescending<KhatmSnapshot> { it.lastActivity ?: Instant.MIN }
                .thenByDescending { it.khatm.startedDay }
                .thenByDescending { it.khatm.id })
    }

    /** Finished khatms, newest completion first: read-only history. */
    fun observeCompleted(): Flow<List<KhatmSnapshot>> = observeAll().map { all ->
        all.filter { it.khatm.completedAt != null }.sortedByDescending { it.khatm.completedAt }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAll(): Flow<List<KhatmSnapshot>> = khatms.observeKhatms().flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList()) else combine(list.map(::observe)) { it.toList() }
    }

    companion object {
        /** Days of reading the pace is averaged over when the khatm has no daily goal. */
        const val PACE_WINDOW_DAYS = 7

        /** Σ over pages of (ayah readings on the page ÷ ayahs on the page): §4.6's deterministic "pages". */
        private fun pagesOf(ayahsPerPage: IntArray, index: QuranIndex): Double {
            var pages = 0.0
            for (page in 1..Quran.PAGE_COUNT) {
                if (ayahsPerPage[page] != 0) pages += ayahsPerPage[page].toDouble() / index.ayahsOnPage(page)
            }
            return pages
        }

        /**
         * The derivation itself.
         *
         * @param eventAyahs ayahs with at least one event in this khatm, on any day.
         * @param newest the khatm's most recently recorded event.
         * @param dayCounts events of this khatm per (ayah, day).
         */
        fun compute(
            khatm: Khatm,
            eventAyahs: Set<AyahId>,
            newest: ReadEvent?,
            dayCounts: List<AyahDayCount>,
            today: LocalDate,
            index: QuranIndex,
        ): KhatmSnapshot {
            val covered = BooleanArray(Quran.AYAH_COUNT + 1)
            for (id in 1..khatm.assumedCoveredUpTo) covered[id] = true
            for (ayah in eventAyahs) covered[ayah.value] = true

            var coveredCount = 0
            var firstUnread = 0
            val uncoveredOnPage = IntArray(Quran.PAGE_COUNT + 1)
            for (id in 1..Quran.AYAH_COUNT) {
                if (covered[id]) {
                    coveredCount++
                } else {
                    if (firstUnread == 0) firstUnread = id
                    uncoveredOnPage[index.pageOf(AyahId(id))]++
                }
            }
            // Summed per page rather than per ayah, so that a whole page is exactly 1.0 and day counts
            // derived from it ("151 days left") do not wobble on floating-point dust.
            val remainingPages = pagesOf(uncoveredOnPage, index)
            val complete = coveredCount == Quran.AYAH_COUNT
            val firstUnreadId = AyahId.orNull(firstUnread)

            // Only marking moves the resume position; with no marks yet it is the start chosen for the khatm.
            val resume = if (newest != null) {
                newest.ayahId.next() ?: firstUnreadId
            } else {
                AyahId.orNull(khatm.assumedCoveredUpTo + 1)
            }

            val position = resume ?: AyahId.LAST
            val surah = index.ref(position).surah
            val juz = index.juzOf(position)
            fun bar(range: AyahRange) = CoverageBar(range.ids.count { covered[it] }, range.size)

            val days = dayCounts.groupBy { it.day }.map { (day, rows) ->
                val readOnPage = IntArray(Quran.PAGE_COUNT + 1)
                for (row in rows) readOnPage[index.pageOf(row.ayahId)] += row.count
                KhatmDay(day = day, ayahs = rows.sumOf { it.count }, pages = pagesOf(readOnPage, index))
            }.sortedBy { it.day }

            val pagesPerDay: Double? = khatm.dailyPages?.toDouble()
                ?: days.takeLast(PACE_WINDOW_DAYS).takeIf { it.isNotEmpty() }?.map { it.pages }?.average()
            val projectedFinish = if (complete || pagesPerDay == null || pagesPerDay <= 0.0) {
                null
            } else {
                today.plusDays(ceil(remainingPages / pagesPerDay).toLong())
            }

            val completion = if (complete) {
                // A khatm restored without its completion moment still gets a screen: its last reading day.
                val completedDay = days.lastOrNull()?.day ?: khatm.startedDay
                val daysTaken = (ChronoUnit.DAYS.between(khatm.startedDay, completedDay).toInt() + 1).coerceAtLeast(1)
                KhatmCompletion(
                    startedDay = khatm.startedDay,
                    completedDay = completedDay,
                    daysTaken = daysTaken,
                    averageAyahsPerDay = (Quran.AYAH_COUNT - khatm.assumedCoveredUpTo).toDouble() / daysTaken,
                )
            } else {
                null
            }

            return KhatmSnapshot(
                khatm = khatm,
                today = today,
                coveredAyahs = coveredCount,
                resume = resume,
                firstUnread = firstUnreadId,
                isComplete = complete,
                lastActivity = newest?.readAt,
                days = days,
                currentSurah = surah,
                surahBar = bar(index.surahRange(surah)),
                currentJuz = juz,
                juzBar = bar(index.juzRange(juz)),
                juzCoverage = (1..Quran.JUZ_COUNT).map { bar(index.juzRange(it)).fraction },
                remainingPages = remainingPages,
                projectedFinish = projectedFinish,
                completion = completion,
            )
        }
    }
}
