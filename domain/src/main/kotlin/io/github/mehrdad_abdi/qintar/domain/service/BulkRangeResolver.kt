package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.surahOf
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import javax.inject.Inject

/**
 * Resolves the passage of "mark up to here" (§4.2.2). The result is handed to
 * `ReadingTracker.markRange(…, FILL)`, which skips whatever in it is already read.
 */
class BulkRangeResolver @Inject constructor(
    private val log: ReadLogStore,
    private val khatms: KhatmStore,
    private val index: QuranIndex,
    private val clock: DayClock,
) {
    /**
     * The scope start for browse and khatm readers: the start of the surah containing the first
     * on-screen ayah when the reader was opened, or the resume position, whichever is later.
     * A bookmark's scope start is simply its range start.
     */
    fun scopeStart(firstOnScreenAtOpen: AyahId, resume: AyahId? = null): AyahId {
        val surahStart = index.surahOf(firstOnScreenAtOpen).firstAyahId
        return if (resume != null && resume > surahStart) resume else surahStart
    }

    /**
     * From the start of the unread run through [tapped]: the first ayah after the nearest *earlier* ayah
     * that is read in scope, but not before [scopeStart].
     *
     * "Read in scope": in a khatm, covered; elsewhere, counted today in this context.
     *
     * When the user has scrolled above the scope start and taps there, the bound no longer describes
     * where they are, so the run is bounded by the start of the tapped ayah's surah instead.
     */
    suspend fun upToHere(tapped: AyahId, context: ReadContext, scopeStart: AyahId): AyahRange {
        val bound = if (scopeStart <= tapped) scopeStart else index.surahOf(tapped).firstAyahId
        val before = tapped.previous()?.takeIf { it >= bound } ?: return AyahRange.single(tapped)
        val earlier = AyahRange(bound, before)

        var nearestRead = if (context is ReadContext.Khatm) {
            val assumed = khatms.khatm(context.id)?.assumedCoveredUpTo ?: 0
            val events = log.distinctAyahs(context, earlier).maxOfOrNull { it.value } ?: 0
            maxOf(events, minOf(assumed, before.value))
        } else {
            log.ayahCounts(context, clock.today(), earlier).keys.maxOfOrNull { it.value } ?: 0
        }
        if (nearestRead < bound.value) nearestRead = bound.value - 1
        return AyahRange.of(nearestRead + 1, tapped.value)
    }

    /** "Mark page": the part of a page that lies inside the reader's scope. */
    fun page(page: Int, scope: AyahRange = AyahRange.ALL): AyahRange? = index.pageRange(page).intersect(scope)
}
