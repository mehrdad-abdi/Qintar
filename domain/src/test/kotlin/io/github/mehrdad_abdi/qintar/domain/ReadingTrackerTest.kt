package io.github.mehrdad_abdi.qintar.domain

import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** §4.2, §4.2.1–4.2.3, §4.3: what marking, editing and undoing do to the counts a user sees. */
class ReadingTrackerTest {
    private val app = DomainFixture(start = LocalDateTime.of(2026, 11, 15, 9, 0))
    private val today: LocalDate get() = app.clock.today()
    private val kursi = app.ayah(2, 255)
    private val browse = ReadContext.Browse

    private suspend fun countToday(ayah: AyahId, context: ReadContext) =
        app.log.ayahCounts(context, today, AyahRange.single(ayah))[ayah] ?: 0

    private fun assertFailsWith(expected: Class<out Throwable>, block: suspend () -> Unit) {
        assertThrows(expected) { kotlinx.coroutines.runBlocking { block() } }
    }

    // ---- tapping ----

    @Test
    fun `a tap adds one reading to today and to that ayah in that context`() = runTest {
        val event = app.tracker.mark(kursi, browse, ReadMethod.TAP)

        assertEquals(today, event.day)
        assertEquals(ReadMethod.TAP, event.method)
        assertNull(event.batchId)
        assertEquals(1, app.totals.today())
        assertEquals(1, countToday(kursi, browse))
    }

    @Test
    fun `tapping a read ayah adds one and never removes, set count to n leaves exactly n events for today in that context`() = runTest {
        repeat(3) { app.tracker.mark(kursi, browse, ReadMethod.TAP) }
        assertEquals("every tap is a reading", 3, countToday(kursi, browse))
        assertEquals(3, app.totals.today())

        app.tracker.setCount(kursi, browse, today, 7)
        assertEquals(7, countToday(kursi, browse))
        assertEquals(7, app.totals.today())

        app.tracker.setCount(kursi, browse, today, 2)
        assertEquals(2, countToday(kursi, browse))
        assertEquals(2, app.log.events.size)
        assertEquals(2, app.totals.today())
    }

    @Test
    fun `reading the same ayah in a bookmark after reading it in a khatm adds one and removes none`() = runTest {
        val khatm = ReadContext.Khatm(app.khatms.start("Khatm 1", startFrom = kursi).id)
        val bookmark = ReadContext.Bookmark(42)
        app.tracker.mark(kursi, khatm, ReadMethod.TAP)

        assertEquals("the bookmark starts at zero although the khatm read it this morning", 0, countToday(kursi, bookmark))

        app.tracker.mark(kursi, bookmark, ReadMethod.TAP)

        assertEquals(1, countToday(kursi, bookmark))
        assertEquals(1, countToday(kursi, khatm))
        assertEquals("both readings count toward the day", 2, app.totals.today())
    }

    @Test
    fun `counts are scoped to a context, so two bookmarks on the same ayah do not share readings`() = runTest {
        app.tracker.mark(kursi, ReadContext.Bookmark(1), ReadMethod.TAP)

        assertEquals(1, countToday(kursi, ReadContext.Bookmark(1)))
        assertEquals(0, countToday(kursi, ReadContext.Bookmark(2)))
        assertEquals(0, countToday(kursi, ReadContext.Random))
    }

    @Test
    fun `an ayah finished by audio counts like a tap and is recorded as listened`() = runTest {
        val event = app.tracker.mark(kursi, browse, ReadMethod.AUDIO)

        assertEquals(ReadMethod.AUDIO, event.method)
        assertEquals(1, app.totals.today())
    }

    // ---- the reading day ----

    @Test
    fun `marking after midnight in an open reader counts on the new day and the check reflects it`() = runTest {
        app.clock.setTime(23, 59)
        val yesterday = today
        val first = app.ayah(2, 1)
        val second = app.ayah(2, 2)
        val scope = AyahRange(first, second)

        // What an open reader observes: counts keyed by the reading day, never by a date captured at opening.
        @OptIn(ExperimentalCoroutinesApi::class)
        val checks = app.clock.todayFlow.flatMapLatest { day -> app.log.observeAyahCounts(browse, day, scope) }

        checks.test {
            assertEquals(emptyMap<AyahId, Int>(), awaitItem())

            app.tracker.mark(first, browse, ReadMethod.TAP)
            assertEquals(mapOf(first to 1), awaitItem())

            app.clock.advance(Duration.ofMinutes(2))
            assertEquals("the new day starts with nothing checked", emptyMap<AyahId, Int>(), awaitItem())

            app.tracker.mark(second, browse, ReadMethod.TAP)
            assertEquals("the reading after midnight shows its check without leaving the screen", mapOf(second to 1), awaitItem())
        }

        assertEquals(yesterday.plusDays(1), today)
        assertEquals(1, app.totals.total(yesterday))
        assertEquals(1, app.totals.total(today))
    }

    @Test
    fun `today's total follows the reading day across midnight without re-subscribing`() = runTest {
        app.clock.setTime(23, 50)
        app.totals.observeToday().test {
            assertEquals(0, awaitItem())
            app.tracker.mark(kursi, browse, ReadMethod.TAP)
            assertEquals(1, awaitItem())
            app.clock.advanceMinutes(20)
            assertEquals("a new day begins at zero", 0, awaitItem())
            app.tracker.mark(kursi, browse, ReadMethod.TAP)
            assertEquals(1, awaitItem())
        }
    }

    @Test
    fun `with the day starting at 03_00, reading at 01_30 belongs to the evening before`() = runTest {
        app.setDayStart(180)
        app.clock.set(LocalDateTime.of(2026, 11, 16, 1, 30))

        val event = app.tracker.mark(kursi, browse, ReadMethod.TAP)

        assertEquals(LocalDate.of(2026, 11, 15), event.day)
    }

    @Test
    fun `with the day starting at 03_00, reading at exactly 03_00 belongs to the new day`() = runTest {
        app.setDayStart(180)
        app.clock.set(LocalDateTime.of(2026, 11, 16, 2, 59))
        val before = app.tracker.mark(kursi, browse, ReadMethod.TAP)
        app.clock.set(LocalDateTime.of(2026, 11, 16, 3, 0))
        val after = app.tracker.mark(kursi, browse, ReadMethod.TAP)

        assertEquals(LocalDate.of(2026, 11, 15), before.day)
        assertEquals(LocalDate.of(2026, 11, 16), after.day)
    }

    @Test
    fun `changing the day-start setting or the time zone never moves readings already recorded`() = runTest {
        app.clock.set(LocalDateTime.of(2026, 11, 16, 1, 30))
        app.tracker.mark(kursi, browse, ReadMethod.TAP)

        app.setDayStart(240)
        app.clock.setZone(ZoneId.of("America/Los_Angeles"))

        assertEquals(LocalDate.of(2026, 11, 16), app.log.events.single().day)
        assertEquals(1, app.totals.total(LocalDate.of(2026, 11, 16)))
        // 01:30 on the 16th in Tehran is 14:00 on the 15th in Los Angeles.
        assertEquals("only readings from now on use the new rule", LocalDate.of(2026, 11, 15), today)
    }

    // ---- set count ----

    @Test
    fun `set count to 100 records a hundred readings in one undoable step`() = runTest {
        val batch = app.tracker.setCount(kursi, browse, today, 100)

        assertEquals(100, countToday(kursi, browse))
        assertEquals(100, batch!!.added)
        assertTrue("all share the batch", app.log.events.all { it.batchId == batch.id })

        app.tracker.undo(batch)
        assertEquals(0, app.totals.today())
    }

    @Test
    fun `lowering the count removes the newest readings and keeps the oldest`() = runTest {
        val first = app.tracker.mark(kursi, browse, ReadMethod.TAP)
        app.clock.advanceMinutes(1)
        val second = app.tracker.mark(kursi, browse, ReadMethod.AUDIO)
        app.clock.advanceMinutes(1)
        app.tracker.mark(kursi, browse, ReadMethod.TAP)

        app.tracker.setCount(kursi, browse, today, 2)

        assertEquals(listOf(first.id, second.id), app.log.events.map { it.id })
    }

    @Test
    fun `set count to zero removes all of today's readings of that ayah in that context and nothing else`() = runTest {
        val other = app.ayah(2, 256)
        app.tracker.mark(kursi, browse, ReadMethod.TAP)
        app.tracker.mark(kursi, browse, ReadMethod.TAP)
        app.tracker.mark(other, browse, ReadMethod.TAP)
        app.tracker.mark(kursi, ReadContext.Random, ReadMethod.TAP)

        app.tracker.setCount(kursi, browse, today, 0)

        assertEquals(0, countToday(kursi, browse))
        assertEquals(1, countToday(other, browse))
        assertEquals(1, countToday(kursi, ReadContext.Random))
        assertEquals(2, app.totals.today())
    }

    @Test
    fun `set count from the reader leaves an earlier day's readings untouched`() = runTest {
        app.tracker.mark(kursi, browse, ReadMethod.TAP)
        val yesterday = today
        app.clock.advanceDays(1)
        app.tracker.mark(kursi, browse, ReadMethod.TAP)

        app.tracker.setCount(kursi, browse, today, 0)

        assertEquals(1, app.totals.total(yesterday))
        assertEquals(0, app.totals.today())
    }

    @Test
    fun `set count to the number already there changes nothing and offers no undo`() = runTest {
        app.tracker.mark(kursi, browse, ReadMethod.TAP)

        assertNull(app.tracker.setCount(kursi, browse, today, 1))
        assertEquals(1, app.log.events.size)
    }

    @Test
    fun `undoing a lowered count brings the removed readings back as they were`() = runTest {
        app.tracker.mark(kursi, browse, ReadMethod.AUDIO)
        app.clock.advanceMinutes(5)
        val later = app.tracker.mark(kursi, browse, ReadMethod.TAP)

        val batch = app.tracker.setCount(kursi, browse, today, 0)!!
        assertEquals(-2, batch.net)
        app.tracker.undo(batch)

        assertEquals(2, countToday(kursi, browse))
        assertEquals(setOf(ReadMethod.AUDIO, ReadMethod.TAP), app.log.events.map { it.method }.toSet())
        assertTrue(app.log.events.any { it.readAt == later.readAt })
    }

    @Test
    fun `set count accepts 0 to 9999 and refuses anything else without writing`() = runTest {
        assertFailsWith(DomainFailure.CountOutOfRange::class.java) { app.tracker.setCount(kursi, browse, today, -1) }
        assertFailsWith(DomainFailure.CountOutOfRange::class.java) { app.tracker.setCount(kursi, browse, today, 10_000) }
        assertEquals(0, app.log.events.size)

        app.tracker.setCount(kursi, browse, today, 9999)
        assertEquals(9999, countToday(kursi, browse))
    }

    @Test
    fun `set count on a whole bookmark makes every ayah's count equal, raising some and lowering others`() = runTest {
        val range = app.index.surahRange(112)
        val context = ReadContext.Bookmark(5)
        val ayahs = range.toList()
        repeat(5) { app.tracker.mark(ayahs[0], context, ReadMethod.TAP) }
        app.tracker.mark(ayahs[1], context, ReadMethod.TAP)

        val batch = app.tracker.setRangeCount(range, context, today, 3)!!

        assertEquals(ayahs.associateWith { 3 }, app.log.ayahCounts(context, today, range))
        assertEquals(2 + 3 + 3, batch.added)
        assertEquals(2, batch.removed.size)

        app.tracker.undo(batch)
        assertEquals(mapOf(ayahs[0] to 5, ayahs[1] to 1), app.log.ayahCounts(context, today, range))
    }

    // ---- bulk marking ----

    @Test
    fun `FILL gives one reading to unread ayahs and never adds to an ayah that already has a count`() = runTest {
        val range = AyahRange(app.ayah(2, 1), app.ayah(2, 5))
        app.tracker.mark(app.ayah(2, 2), browse, ReadMethod.TAP)
        app.tracker.mark(app.ayah(2, 2), browse, ReadMethod.TAP)

        val batch = app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.FILL)

        assertEquals(4, batch.added)
        val counts = app.log.ayahCounts(browse, today, range)
        assertEquals(2, counts[app.ayah(2, 2)])
        assertEquals(listOf(1, 1, 1, 1), listOf(1, 3, 4, 5).map { counts[app.ayah(2, it)] })
    }

    @Test
    fun `FILL on a passage that is already read writes nothing`() = runTest {
        val range = AyahRange(app.ayah(2, 1), app.ayah(2, 5))
        app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.FILL)

        val again = app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.FILL)

        assertEquals(0, again.added)
        assertEquals(5, app.totals.today())
    }

    @Test
    fun `FILL in a non-khatm context looks only at today, so yesterday's readings do not block it`() = runTest {
        val range = AyahRange(app.ayah(2, 1), app.ayah(2, 5))
        app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.FILL)
        app.clock.advanceDays(1)

        val batch = app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.FILL)

        assertEquals(5, batch.added)
        assertEquals(5, app.totals.today())
    }

    @Test
    fun `ADD records one more full reading of every ayah each time it is used`() = runTest {
        val range = app.index.surahRange(112)
        val context = ReadContext.Bookmark(9)

        repeat(3) { app.tracker.markRange(range, context, ReadMethod.BULK, RangeMode.ADD) }

        assertEquals(range.associateWith { 3 }, app.log.ayahCounts(context, today, range))
        assertEquals(3 * range.size, app.totals.today())
    }

    @Test
    fun `ADD with times records that many full readings in one batch`() = runTest {
        val range = app.index.surahRange(112)
        val batch = app.tracker.markRange(range, ReadContext.Bookmark(9), ReadMethod.MANUAL, RangeMode.ADD, times = 11)

        assertEquals(11 * range.size, batch.added)
        assertEquals(11 * range.size, app.totals.today())
        assertTrue(app.log.events.all { it.batchId == batch.id && it.method == ReadMethod.MANUAL })
    }

    @Test
    fun `all events of one bulk action share a batch and undo removes exactly that action`() = runTest {
        app.tracker.mark(app.ayah(2, 1), browse, ReadMethod.TAP)
        val range = AyahRange(app.ayah(2, 1), app.ayah(2, 10))

        val batch = app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.ADD)
        assertEquals(11, app.totals.today())
        assertEquals(10, app.log.eventsOfBatch(batch.id).size)
        assertTrue(app.log.eventsOfBatch(batch.id).all { it.method == ReadMethod.BULK })

        app.tracker.undo(batch)

        assertEquals("the single tap made before the bulk action survives", 1, app.totals.today())
    }

    @Test
    fun `a full reading of a group counts each bookmark under its own context and undoes as one`() = runTest {
        val ikhlas = Segment(ReadContext.Bookmark(1), app.index.surahRange(112))
        val falaq = Segment(ReadContext.Bookmark(2), app.index.surahRange(113))

        val batch = app.tracker.markSegments(listOf(ikhlas, falaq), ReadMethod.BULK, RangeMode.ADD)

        assertEquals(4, app.log.ayahCounts(ikhlas.context, today, ikhlas.range).size)
        assertEquals(5, app.log.ayahCounts(falaq.context, today, falaq.range).size)
        assertEquals(9, batch.added)

        app.tracker.undo(batch)
        assertEquals(0, app.totals.today())
    }

    @Test
    fun `fill cannot be repeated and a repeat count must be positive`() = runTest {
        val range = app.index.surahRange(112)
        assertFailsWith(DomainFailure.CountOutOfRange::class.java) {
            app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.FILL, times = 2)
        }
        assertFailsWith(DomainFailure.CountOutOfRange::class.java) {
            app.tracker.markRange(range, browse, ReadMethod.BULK, RangeMode.ADD, times = 0)
        }
        assertEquals(0, app.log.events.size)
    }

    // ---- manual log and other days ----

    @Test
    fun `a passage logged for a past day lands on that day, recorded now, as manual entries of the log`() = runTest {
        val threeDaysAgo = today.minusDays(3)
        val range = AyahRange(app.ayah(2, 1), app.ayah(2, 141))

        val batch = app.tracker.markRange(range, ReadContext.Log, ReadMethod.MANUAL, RangeMode.ADD, day = threeDaysAgo)

        assertEquals(141, batch.added)
        assertEquals(141, app.totals.total(threeDaysAgo))
        assertEquals(0, app.totals.today())
        assertTrue(app.log.events.all { it.readAt == app.clock.now() && it.context == ReadContext.Log && it.method == ReadMethod.MANUAL })
    }

    @Test
    fun `a bookmark's full reading logged for a past day shows as read in that bookmark on that day only`() = runTest {
        val context = ReadContext.Bookmark(3)
        val range = app.index.surahRange(112)
        val yesterday = today.minusDays(1)

        app.tracker.markRange(range, context, ReadMethod.MANUAL, RangeMode.ADD, times = 2, day = yesterday)

        assertEquals(range.associateWith { 2 }, app.log.ayahCounts(context, yesterday, range))
        assertEquals(emptyMap<AyahId, Int>(), app.log.ayahCounts(context, today, range))
    }

    @Test
    fun `a bare number is a tally that counts toward its day and has no ayah`() = runTest {
        val tally = app.tracker.addTally(today, 40, " mosque ")

        assertEquals(40, app.totals.today())
        assertEquals("mosque", tally.note)
        assertEquals(0, app.log.events.size)
    }

    @Test
    fun `a day total is its events plus its tallies`() = runTest {
        app.tracker.addTally(today, 40, null)
        app.tracker.addTally(today, 5, null)
        repeat(3) { app.tracker.mark(kursi, browse, ReadMethod.TAP) }

        assertEquals(48, app.totals.today())
    }

    @Test
    fun `a tally can be changed and deleted, and the day total follows`() = runTest {
        val tally = app.tracker.addTally(today, 40, null)

        app.tracker.updateTally(tally.id, 25, "corrected")
        assertEquals(25, app.totals.today())
        assertEquals("corrected", app.log.tallies.single().note)

        app.tracker.deleteTally(tally.id)
        assertEquals(0, app.totals.today())
    }

    @Test
    fun `a tally must be a positive number`() = runTest {
        assertFailsWith(DomainFailure.CountOutOfRange::class.java) { app.tracker.addTally(today, 0, null) }
        assertFailsWith(DomainFailure.CountOutOfRange::class.java) { app.tracker.addTally(today, -3, null) }
    }

    @Test
    fun `nothing can be recorded for a future day`() = runTest {
        val tomorrow = today.plusDays(1)
        val range = app.index.surahRange(112)

        assertFailsWith(DomainFailure.FutureDay::class.java) {
            app.tracker.markRange(range, ReadContext.Log, ReadMethod.MANUAL, RangeMode.ADD, day = tomorrow)
        }
        assertFailsWith(DomainFailure.FutureDay::class.java) { app.tracker.setCount(kursi, browse, tomorrow, 1) }
        assertFailsWith(DomainFailure.FutureDay::class.java) { app.tracker.addTally(tomorrow, 10, null) }
        assertEquals(0, app.log.events.size)
        assertEquals(0, app.log.tallies.size)
    }

    @Test
    fun `any past day's entry can be set to another count or deleted`() = runTest {
        val yesterday = today.minusDays(1)
        app.tracker.markRange(AyahRange.single(kursi), ReadContext.Log, ReadMethod.MANUAL, RangeMode.ADD, times = 3, day = yesterday)

        app.tracker.setCount(kursi, ReadContext.Log, yesterday, 1)
        assertEquals(1, app.totals.total(yesterday))

        app.tracker.delete(app.log.events.single().id)
        assertEquals(0, app.totals.total(yesterday))
    }

    @Test
    fun `deleting one reading removes only that reading`() = runTest {
        val first = app.tracker.mark(kursi, browse, ReadMethod.TAP)
        val second = app.tracker.mark(kursi, browse, ReadMethod.TAP)

        app.tracker.delete(first.id)

        assertEquals(listOf(second.id), app.log.events.map { it.id })
        assertNotNull(app.log.event(second.id))
    }

    // ---- observation ----

    @Test
    fun `totals for a range of days list only days with readings and update as readings arrive`() = runTest {
        val from = today.minusDays(6)
        app.totals.observeTotals(from, today).test {
            assertEquals(emptyMap<LocalDate, Int>(), awaitItem())

            app.tracker.addTally(today.minusDays(2), 12, null)
            assertEquals(mapOf(today.minusDays(2) to 12), awaitItem())

            app.tracker.mark(kursi, browse, ReadMethod.TAP)
            assertEquals(mapOf(today.minusDays(2) to 12, today to 1), awaitItem())

            app.tracker.addTally(today.minusDays(30), 99, "outside the range")
            expectNoEvents()
        }
    }

    @Test
    fun `readings are split by where and how they were made`() = runTest {
        val khatm = ReadContext.Khatm(app.khatms.start("Khatm 1").id)
        app.tracker.mark(AyahId(1), khatm, ReadMethod.TAP)
        app.tracker.mark(AyahId(2), khatm, ReadMethod.AUDIO)
        app.tracker.mark(kursi, ReadContext.Bookmark(1), ReadMethod.AUDIO)
        app.tracker.addTally(today, 40, null)

        app.totals.observeSourceSplit(today, today).test {
            val split = awaitItem()
            assertEquals(2, split.byContext[io.github.mehrdad_abdi.qintar.domain.model.ContextKind.KHATM])
            assertEquals(1, split.byContext[io.github.mehrdad_abdi.qintar.domain.model.ContextKind.BOOKMARK])
            assertEquals(2, split.byMethod[ReadMethod.AUDIO])
            assertEquals(1, split.byMethod[ReadMethod.TAP])
            assertEquals(40, split.tallied)
            assertEquals("the split accounts for the whole day total", app.totals.today(), split.total)
        }
    }
}
