package io.github.mehrdad_abdi.qintar.data.user

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.data.quran.AssetQuranTextSource
import io.github.mehrdad_abdi.qintar.data.quran.QuranDatabase
import io.github.mehrdad_abdi.qintar.data.quran.QuranIndexLoader
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.ayahId
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BatchIdFactory
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.DefaultReadingTracker
import io.github.mehrdad_abdi.qintar.domain.service.KhatmManager
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.domain.service.ScheduleStatus
import io.github.mehrdad_abdi.qintar.domain.testing.FakeDayClock
import io.github.mehrdad_abdi.qintar.domain.testing.FakePrefsStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real domain services over the real databases: Room stores on SQLite and the [QuranIndex] built
 * from the bundled `quran.db`. Each test is a thing a user does and the numbers they would then see.
 */
@RunWith(AndroidJUnit4::class)
class DomainOverRoomTest {

    private lateinit var db: QintarDatabase
    private lateinit var quranDb: QuranDatabase
    private lateinit var stores: Stores
    private lateinit var index: QuranIndex

    private val clock = FakeDayClock()
    private val prefs = FakePrefsStore()
    private var batches = 0

    private lateinit var tracker: DefaultReadingTracker
    private lateinit var totals: DayTotals
    private lateinit var khatms: KhatmManager
    private lateinit var khatmProgress: KhatmProgress
    private lateinit var library: BookmarkLibrary
    private lateinit var availability: Availability

    @Before
    fun setUp() = runTest {
        db = QintarDatabase.inMemory(ApplicationProvider.getApplicationContext())
        quranDb = QuranDatabase.create(ApplicationProvider.getApplicationContext())
        stores = roomStores(db)
        index = QuranIndexLoader.load(quranDb.quranDao())

        tracker = DefaultReadingTracker(stores.log, stores.khatms, clock, stores.tx, BatchIdFactory { "batch-${++batches}" })
        totals = DayTotals(stores.log, clock)
        khatms = KhatmManager(stores.khatms, clock, stores.tx)
        khatmProgress = KhatmProgress(stores.log, stores.khatms, index, clock)
        library = BookmarkLibrary(stores.bookmarks, clock, stores.tx)
        availability = Availability(stores.bookmarks, stores.log, prefs, clock, stores.tx)
    }

    @After
    fun tearDown() {
        db.close()
        quranDb.close()
    }

    private fun ayah(surah: Int, number: Int): AyahId = index.ayahId(surah, number)!!

    // ---- the Quran index and text from the bundled asset ----

    @Test
    fun theIndexBuiltFromTheAssetKnowsTheMadinahMushaf() = runTest {
        assertEquals(114, index.surahs.size)
        assertEquals(AyahRange.of(1, 7), index.pageRange(1))
        assertEquals(Quran.AYAH_COUNT, index.pageRange(Quran.PAGE_COUNT).end.value)
        assertEquals(42, index.pageOf(ayah(2, 255)))
        assertEquals(3, index.juzOf(ayah(2, 255)))
        assertEquals(286, index.surah(2).ayahCount)
        assertFalse(index.surah(9).hasBasmala)
        assertEquals(15, AyahRange.ALL.count { index.sajdaOf(it) != SajdaType.NONE })
        assertEquals(Quran.AYAH_COUNT, (1..Quran.PAGE_COUNT).sumOf { index.ayahsOnPage(it) })
    }

    @Test
    fun quranTextIsReadByRangeAndByPageInMushafOrder() = runTest {
        val text = AssetQuranTextSource(quranDb.quranDao())

        val fatihah = text.page(1)
        assertEquals((1..7).map(::AyahId), fatihah.map { it.id })
        assertEquals(fatihah, text.ayahs(index.surahRange(1)))

        val page2 = text.page(2)
        assertEquals(index.pageRange(2), AyahRange(page2.first().id, page2.last().id))
        assertEquals("الٓمٓ", page2.first().text)

        val kursi = text.ayahs(AyahRange.single(ayah(2, 255))).single()
        assertEquals(ayah(2, 255), kursi.id)
        assertEquals(quranDb.quranDao().ayah(kursi.id.value)!!.text, kursi.text)
        assertTrue(kursi.text.length > 100)
        assertEquals(emptyList<Any>(), text.page(605))
    }

    // ---- marking ----

    @Test
    fun markingAnAyahCountsItTodayAndAgainWhenReadAgain() = runTest {
        totals.observeToday().test {
            assertEquals(0, awaitItem())

            val event = tracker.mark(ayah(2, 255), ReadContext.Browse, ReadMethod.TAP)
            assertEquals(1, awaitItem())
            assertEquals(clock.today(), event.day)
            assertEquals(event, stores.log.event(event.id))

            tracker.mark(ayah(2, 255), ReadContext.Browse, ReadMethod.TAP)
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(mapOf(ayah(2, 255) to 2), stores.log.ayahCounts(ReadContext.Browse, clock.today(), AyahRange.ALL))
    }

    @Test
    fun setCountRaisesAndLowersTheDayTotalAndUndoPutsItBack() = runTest {
        val today = clock.today()
        val kursi = ayah(2, 255)
        tracker.mark(kursi, ReadContext.Browse, ReadMethod.TAP)

        val raised = tracker.setCount(kursi, ReadContext.Browse, today, 5)!!
        assertEquals(4, raised.net)
        assertEquals(5, totals.today())

        val lowered = tracker.setCount(kursi, ReadContext.Browse, today, 2)!!
        assertEquals(-3, lowered.net)
        assertEquals(2, totals.today())
        assertNull(tracker.setCount(kursi, ReadContext.Browse, today, 2))

        tracker.undo(lowered)
        assertEquals(5, totals.today())
        assertEquals(mapOf(kursi to 5), stores.log.ayahCounts(ReadContext.Browse, today, AyahRange.ALL))

        tracker.undo(raised)
        assertEquals(1, totals.today())
    }

    @Test
    fun fillingARangeAddsOnlyTheAyahsNotYetReadTodayAndUndoRemovesExactlyThose() = runTest {
        val page = index.pageRange(50)
        val context = ReadContext.Browse
        tracker.mark(page.start, context, ReadMethod.TAP)
        tracker.mark(page.end, context, ReadMethod.TAP)

        val batch = tracker.markRange(page, context, ReadMethod.BULK, RangeMode.FILL)

        assertEquals(page.size - 2, batch.added)
        assertEquals(page.size, totals.today())
        assertTrue(stores.log.ayahCounts(context, clock.today(), page).values.all { it == 1 })
        assertEquals(page.size - 2, stores.log.eventsOfBatch(batch.id).size)

        // Filling again finds nothing to add.
        assertEquals(0, tracker.markRange(page, context, ReadMethod.BULK, RangeMode.FILL).added)

        tracker.undo(batch)
        assertEquals(2, totals.today())
        assertEquals(setOf(page.start, page.end), stores.log.distinctAyahs(context, page))
    }

    @Test
    fun addingARangeTwiceOverCountsEveryAyahEachTime() = runTest {
        val surah = index.surahRange(112)

        val batch = tracker.markRange(surah, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD, times = 3)

        assertEquals(surah.size * 3, batch.added)
        assertEquals(surah.size * 3, totals.today())
        assertTrue(stores.log.ayahCounts(ReadContext.Browse, clock.today(), surah).values.all { it == 3 })
    }

    @Test
    fun aRejectedActionWritesNothing() = runTest {
        tracker.mark(ayah(1, 1), ReadContext.Browse, ReadMethod.TAP)
        try {
            tracker.markRange(index.surahRange(2), ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD, day = clock.today().plusDays(1))
            fail("a reading in the future was accepted")
        } catch (_: DomainFailure.FutureDay) {
        }
        assertEquals(1, stores.log.allEvents().size)
    }

    // ---- day totals ----

    @Test
    fun aDayTotalIsItsEventsPlusItsTalliesAndEachDayStandsAlone() = runTest {
        val yesterday = clock.today()
        tracker.markRange(index.surahRange(36), ReadContext.Browse, ReadMethod.BULK, RangeMode.FILL)
        val tally = tracker.addTally(yesterday, 40, "mosque")
        assertEquals(83 + 40, totals.today())

        clock.advanceDays(1)
        val today = clock.today()
        tracker.mark(ayah(1, 1), ReadContext.Random, ReadMethod.AUDIO)
        tracker.addTally(today, 9, null)

        assertEquals(10, totals.today())
        assertEquals(mapOf(yesterday to 123, today to 10), totals.totals(null, null))
        assertEquals(mapOf(today to 10), totals.totals(today, today))

        tracker.updateTally(tally.id, 60, "mosque")
        assertEquals(143, totals.total(yesterday))
        tracker.deleteTally(tally.id)
        assertEquals(83, totals.total(yesterday))
    }

    // ---- khatm ----

    @Test
    fun aKhatmCoversThePagesReadAndResumesAfterTheLastOne() = runTest {
        val khatm = khatms.start("First khatm", dailyPages = 2f)
        val context = ReadContext.Khatm(khatm.id)
        val firstTwoPages = AyahRange(index.pageRange(1).start, index.pageRange(2).end)

        tracker.markRange(firstTwoPages, context, ReadMethod.BULK, RangeMode.FILL)

        val snapshot = khatmProgress.snapshot(khatm.id)!!
        assertEquals(firstTwoPages.size, snapshot.coveredAyahs)
        assertEquals(index.pageRange(3).start, snapshot.resume)
        assertEquals(index.pageRange(3).start, snapshot.firstUnread)
        assertFalse(snapshot.isComplete)
        assertEquals(firstTwoPages.size, snapshot.todayAyahs)
        assertEquals(2.0, snapshot.todayPages, 1e-9)
        assertEquals(1.0f, snapshot.todayGoalFraction!!, 1e-6f)
        assertEquals((Quran.PAGE_COUNT - 2).toDouble(), snapshot.remainingPages, 1e-6)
        assertEquals(2, snapshot.currentSurah)
        assertEquals(1, snapshot.currentJuz)
        assertEquals(clock.now(), snapshot.lastActivity)
    }

    @Test
    fun rereadingInAKhatmCountsForTheDayButNotForCoverage() = runTest {
        val khatm = khatms.start("Khatm")
        val context = ReadContext.Khatm(khatm.id)
        val page1 = index.pageRange(1)
        tracker.markRange(page1, context, ReadMethod.BULK, RangeMode.FILL)

        clock.advanceDays(1)
        // Covered already, so FILL adds nothing; ADD is a deliberate re-reading.
        assertEquals(0, tracker.markRange(page1, context, ReadMethod.BULK, RangeMode.FILL).added)
        tracker.markRange(page1, context, ReadMethod.BULK, RangeMode.ADD)

        val snapshot = khatmProgress.snapshot(khatm.id)!!
        assertEquals(7, snapshot.coveredAyahs)
        assertEquals(listOf(7, 7), snapshot.days.map { it.ayahs })
        assertEquals(7, totals.today())
        assertEquals(14, totals.totals(null, null).values.sum())
    }

    @Test
    fun aKhatmStartedMidwayAssumesTheEarlierPagesAndCompletesOnTheLastAyah() = runTest {
        val lastPage = index.pageRange(Quran.PAGE_COUNT)
        val khatm = khatms.start("Almost done", startFrom = lastPage.start)
        val context = ReadContext.Khatm(khatm.id)
        assertEquals(lastPage.start.value - 1, khatmProgress.snapshot(khatm.id)!!.coveredAyahs)
        assertNull(tracker.khatmGap(khatm.id, lastPage.start))
        assertEquals(lastPage, tracker.khatmGap(khatm.id, lastPage.end))

        val batch = tracker.markRange(lastPage, context, ReadMethod.BULK, RangeMode.FILL)

        val done = khatmProgress.snapshot(khatm.id)!!
        assertTrue(done.isComplete)
        assertEquals(Quran.AYAH_COUNT, done.coveredAyahs)
        assertEquals(clock.now(), stores.khatms.khatm(khatm.id)!!.completedAt)
        assertEquals(lastPage.size, totals.today())

        tracker.undo(batch)
        assertNull(stores.khatms.khatm(khatm.id)!!.completedAt)
        assertFalse(khatmProgress.snapshot(khatm.id)!!.isComplete)
    }

    @Test
    fun deletingAKhatmKeepsWhatWasReadInIt() = runTest {
        val khatm = khatms.start("Khatm")
        tracker.markRange(index.pageRange(1), ReadContext.Khatm(khatm.id), ReadMethod.BULK, RangeMode.FILL)

        khatms.delete(khatm.id)

        assertNull(khatmProgress.snapshot(khatm.id))
        assertEquals(7, totals.today())
    }

    // ---- bookmarks ----

    @Test
    fun aDailyBookmarkBecomesDoneWhenEveryAyahHasBeenReadToday() = runTest {
        val ikhlas = index.surahRange(112)
        val bookmark = library.create(BookmarkDraft(ikhlas, BookmarkKind.SURAH, repeatTarget = 2, schedule = Schedule.daily()))
        val context = ReadContext.Bookmark(bookmark.id)

        var state = availability.board().state(bookmark.id)!!
        assertEquals(ScheduleStatus.AVAILABLE_NOW, state.status)
        assertEquals(0, state.progress.readings)
        assertEquals(ikhlas.size * 2, state.progress.required)
        assertEquals(1, availability.board().remainingNow)

        tracker.markRange(ikhlas, context, ReadMethod.BULK, RangeMode.ADD)
        state = availability.board().state(bookmark.id)!!
        assertEquals(1, state.progress.fullReadings)
        assertFalse(state.progress.done)

        tracker.markRange(ikhlas, context, ReadMethod.BULK, RangeMode.ADD)
        // Reading it in another context does not count towards the bookmark.
        tracker.markRange(ikhlas, ReadContext.Browse, ReadMethod.BULK, RangeMode.ADD)
        assertTrue(availability.board().state(bookmark.id)!!.progress.done)
        assertEquals(0, availability.board().remainingNow)
        assertEquals(ikhlas.size * 3, totals.today())

        clock.advanceDays(1)
        assertFalse("a new day starts from zero", availability.board().state(bookmark.id)!!.progress.done)
    }

    @Test
    fun aOnceBookmarkLosesItsScheduleTheDayAfterItWasDone() = runTest {
        val page = index.pageRange(604)
        val bookmark = library.create(BookmarkDraft(page, BookmarkKind.PAGE, schedule = Schedule.once()))
        assertEquals(Schedule.once(), stores.bookmarks.bookmark(bookmark.id)!!.schedule)

        tracker.markRange(page, ReadContext.Bookmark(bookmark.id), ReadMethod.BULK, RangeMode.FILL)
        assertEquals("not before the day is over", emptyList<Long>(), availability.rollOverOnce())

        clock.advanceDays(1)
        assertEquals(listOf(bookmark.id), availability.rollOverOnce())
        assertNull(stores.bookmarks.bookmark(bookmark.id)!!.schedule)
        assertEquals(emptyList<Long>(), availability.rollOverOnce())
    }

    @Test
    fun deletingAGroupLeavesItsBookmarksOnTheBoardUngrouped() = runTest {
        val group = library.createGroup("Morning", schedule = Schedule.daily())
        val bookmark = library.create(BookmarkDraft(index.surahRange(36), BookmarkKind.SURAH, groupId = group.id))
        assertEquals(group.id, availability.board().state(bookmark.id)!!.group!!.id)
        assertEquals(ScheduleStatus.AVAILABLE_NOW, availability.board().state(bookmark.id)!!.status)

        library.deleteGroup(group.id, deleteBookmarks = false)

        val board = availability.board()
        assertEquals(emptyList<Any>(), board.groups)
        assertEquals(listOf(bookmark.id), board.ungrouped.map { it.bookmark.id })
        assertNull(board.state(bookmark.id)!!.group)
    }

    @Test
    fun theBoardFollowsReadingsAsTheyAreWritten() = runTest {
        val bookmark = library.create(BookmarkDraft(AyahRange.single(ayah(2, 255)), BookmarkKind.AYAH, schedule = Schedule.daily()))

        availability.observe().test {
            assertFalse(awaitItem().state(bookmark.id)!!.progress.done)
            tracker.mark(ayah(2, 255), ReadContext.Bookmark(bookmark.id), ReadMethod.TAP)
            assertTrue(awaitItem().state(bookmark.id)!!.progress.done)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
