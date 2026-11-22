package io.github.mehrdad_abdi.qintar.domain.testing

import io.github.mehrdad_abdi.qintar.domain.backup.BackupManager
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BatchIdFactory
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.BulkRangeResolver
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.DefaultReadingTracker
import io.github.mehrdad_abdi.qintar.domain.service.KhatmManager
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.domain.service.RevisitRanker
import java.time.LocalDateTime

/**
 * The whole domain wired over in-memory fakes: one line of set-up for a test in any module.
 *
 * ```
 * val app = DomainFixture()
 * app.tracker.mark(app.ayah(2, 255), ReadContext.Browse, ReadMethod.TAP)
 * app.clock.advanceDays(1)
 * ```
 *
 * The fake clock mirrors the "Day starts at" preference only when [setDayStart] is used; changing the
 * preference directly through [prefs] does not move the clock, just as in production the clock reacts
 * through its own subscription.
 */
class DomainFixture(
    start: LocalDateTime = LocalDateTime.of(2026, 11, 15, 9, 0),
    initialPrefs: UserPrefs = UserPrefs(),
) {
    val index: QuranIndex = QuranFixture.index
    val clock = FakeDayClock(start, dayStartMinutes = initialPrefs.dayStartMinutes)

    val log = FakeReadLogStore()
    val khatmStore = FakeKhatmStore()
    val bookmarkStore = FakeBookmarkStore()
    val prefs = FakePrefsStore(initialPrefs)
    val text = FakeQuranTextSource(index)
    val tx = FakeTransactionRunner(log, khatmStore, bookmarkStore)

    private var batchCounter = 0

    /** Predictable batch ids ("batch-1", "batch-2", …) so failures read well. */
    val tracker = DefaultReadingTracker(log, khatmStore, clock, tx, BatchIdFactory { "batch-${++batchCounter}" })
    val totals = DayTotals(log, clock)
    val khatms = KhatmManager(khatmStore, clock, tx)
    val khatmProgress = KhatmProgress(log, khatmStore, index, clock)
    val library = BookmarkLibrary(bookmarkStore, clock, tx)
    val availability = Availability(bookmarkStore, log, prefs, clock, tx)
    val revisit = RevisitRanker(log, index)
    val bulk = BulkRangeResolver(log, khatmStore, index, clock)
    val backup = BackupManager(log, khatmStore, bookmarkStore, prefs, tracker, clock, tx)

    fun ayah(surah: Int, number: Int): AyahId = QuranFixture.ayah(surah, number)

    /** Changes the "Day starts at" setting in both the preferences and the clock. */
    suspend fun setDayStart(minutes: Int) {
        prefs.update { it.copy(dayStartMinutes = minutes) }
        clock.dayStartMinutes = minutes
    }
}
