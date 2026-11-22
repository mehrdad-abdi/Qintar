package io.github.mehrdad_abdi.qintar.data.user

import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.model.AyahDayCount
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRecency
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkAyahCount
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.SourceCount
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** One set of stores that share a transaction runner. */
class Stores(
    val log: ReadLogStore,
    val khatms: KhatmStore,
    val bookmarks: BookmarkStore,
    val tx: TransactionRunner,
)

/**
 * What every implementation of the storage ports must do. It runs against the in-memory fakes of
 * `:domain` (the reference) and against the Room stores, so the two cannot drift apart: a behaviour the
 * domain tests rely on is a behaviour the database has.
 */
abstract class StorageContract {

    protected abstract val stores: Stores

    private val log get() = stores.log
    private val khatms get() = stores.khatms
    private val bookmarks get() = stores.bookmarks
    private val tx get() = stores.tx

    // ---- fixtures ----

    private val day1: LocalDate = LocalDate.of(2026, 11, 13)
    private val day2: LocalDate = day1.plusDays(1)
    private val day3: LocalDate = day1.plusDays(2)
    private val t0: Instant = Instant.parse("2026-11-13T06:00:00Z")
    private val all = AyahRange.ALL

    private fun event(
        ayah: Int,
        day: LocalDate = day1,
        context: ReadContext = ReadContext.Browse,
        at: Instant = t0,
        method: ReadMethod = ReadMethod.TAP,
        batch: String? = null,
    ) = NewReadEvent(AyahId(ayah), day, at, context, method, batch)

    private fun bookmark(id: Long = 0, start: Int = 1, end: Int = 7, groupId: Long? = null, schedule: Schedule? = null, sort: Int = 0) =
        Bookmark(
            id = id,
            range = AyahRange.of(start, end),
            kind = BookmarkKind.RANGE,
            label = null,
            description = null,
            repeatTarget = 1,
            groupId = groupId,
            schedule = schedule,
            sortIndex = sort,
            createdAt = t0,
            updatedAt = t0,
        )

    private fun group(id: Long = 0, name: String = "Morning", schedule: Schedule? = null) =
        BookmarkGroup(id, name, description = null, schedule = schedule, sortIndex = 0, createdAt = t0)

    private fun khatm(id: Long = 0, name: String = "Khatm") =
        Khatm(id, name, startedDay = day1, dailyPages = null, assumedCoveredUpTo = 0, completedAt = null)

    private class Boom : RuntimeException("boom")

    // =========================================================== read log: writes

    @Test
    fun insertedEventsGetAscendingIdsInListOrderAndComeBackAsStored() = runTest {
        val inserted = log.insertEvents(listOf(event(5), event(3), event(9, batch = "b")))

        assertEquals(listOf(5, 3, 9), inserted.map { it.ayahId.value })
        assertTrue(inserted[0].id > 0)
        assertTrue(inserted[0].id < inserted[1].id && inserted[1].id < inserted[2].id)
        assertEquals(inserted, log.allEvents())
        assertEquals(inserted[2], log.event(inserted[2].id))
    }

    @Test
    fun everyColumnOfAnEventSurvivesTheRoundTrip() = runTest {
        val contexts = listOf(ReadContext.Khatm(4), ReadContext.Bookmark(9), ReadContext.Browse, ReadContext.Random, ReadContext.Log)
        val written = ReadMethod.entries.flatMap { method ->
            contexts.map { context -> event(6236, day2, context, t0.plusMillis(1), method, batch = "batch-$method") }
        }

        val stored = log.insertEvents(written)

        assertEquals(written, log.allEvents().map { NewReadEvent(it.ayahId, it.day, it.readAt, it.context, it.method, it.batchId) })
        assertEquals(stored, log.allEvents())
    }

    @Test
    fun anEventIdIsNeverGivenOutTwice() = runTest {
        val first = log.insertEvents(listOf(event(1), event(2)))
        log.deleteEvents(listOf(first[1].id))

        val next = log.insertEvents(listOf(event(3))).single()

        assertTrue("id ${next.id} reuses ${first[1].id}", next.id > first[1].id)
    }

    @Test
    fun insertingNothingStoresNothing() = runTest {
        assertEquals(emptyList<Any>(), log.insertEvents(emptyList()))
        assertEquals(emptyList<Any>(), log.allEvents())
    }

    @Test
    fun deletingEventsReportsHowManyExistedAndIgnoresUnknownIds() = runTest {
        val ids = log.insertEvents(listOf(event(1), event(2), event(3))).map { it.id }

        assertEquals(2, log.deleteEvents(listOf(ids[0], ids[2], 987_654L)))
        assertEquals(listOf(ids[1]), log.allEvents().map { it.id })
        assertEquals(0, log.deleteEvents(emptyList()))
    }

    @Test
    fun deletingManyEventsAtOnceWorks() = runTest {
        val ids = log.insertEvents((1..2500).map { event(it) }).map { it.id }

        assertEquals(2499, log.deleteEvents(ids.drop(1)))
        assertEquals(listOf(ids.first()), log.allEvents().map { it.id })
    }

    @Test
    fun aBatchIsReadInIdOrderAndDeletedAsAWhole() = runTest {
        log.insertEvents(listOf(event(9, batch = "a", at = t0.plusSeconds(60)), event(1, batch = "other"), event(4, batch = "a"), event(2)))

        assertEquals(listOf(9, 4), log.eventsOfBatch("a").map { it.ayahId.value })
        assertEquals(2, log.deleteBatch("a"))
        assertEquals(0, log.deleteBatch("a"))
        assertEquals(listOf(1, 2), log.allEvents().map { it.ayahId.value })
        assertEquals(emptyList<Any>(), log.eventsOfBatch("a"))
    }

    @Test
    fun talliesAreInsertedUpdatedAndDeletedById() = runTest {
        val a = log.insertTally(NewTally(day1, 40, "mosque", t0))
        val b = log.insertTally(NewTally(day1, 10, null, t0.plusSeconds(1)))
        assertTrue(a.id in 1 until b.id)
        assertEquals(listOf(a, b), log.allTallies())
        assertEquals(a, log.tally(a.id))

        log.updateTally(a.copy(day = day2, count = 55, note = null))
        assertEquals(day2, log.tally(a.id)!!.day)
        assertEquals(55, log.tally(a.id)!!.count)
        assertNull(log.tally(a.id)!!.note)
        assertEquals(t0, log.tally(a.id)!!.createdAt)

        log.updateTally(a.copy(id = 4242, count = 1))
        assertEquals(2, log.allTallies().size)

        assertEquals(1, log.deleteTally(b.id))
        assertEquals(0, log.deleteTally(b.id))
        assertNull(log.tally(b.id))
    }

    @Test
    fun deleteAllEmptiesEventsAndTallies() = runTest {
        log.insertEvents(listOf(event(1), event(2)))
        log.insertTally(NewTally(day1, 5, null, t0))

        log.deleteAll()

        assertEquals(emptyList<Any>(), log.allEvents())
        assertEquals(emptyList<Any>(), log.allTallies())
        assertEquals(emptyMap<LocalDate, Int>(), log.observeEventCountsByDay(null, null).first())
    }

    // =========================================================== read log: queries

    @Test
    fun aContextMatchesOnItsKindAndItsId() = runTest {
        log.insertEvents(
            listOf(
                event(10, context = ReadContext.Bookmark(1)),
                event(11, context = ReadContext.Bookmark(2)),
                event(12, context = ReadContext.Khatm(1)),
                event(13, context = ReadContext.Browse),
                event(14, context = ReadContext.Random),
                event(15, context = ReadContext.Log),
            ),
        )

        suspend fun ayahs(context: ReadContext) = log.distinctAyahs(context, all).map { it.value }

        assertEquals(listOf(10), ayahs(ReadContext.Bookmark(1)))
        assertEquals(listOf(11), ayahs(ReadContext.Bookmark(2)))
        assertEquals(listOf(12), ayahs(ReadContext.Khatm(1)))
        assertEquals(emptyList<Int>(), ayahs(ReadContext.Khatm(2)))
        assertEquals(listOf(13), ayahs(ReadContext.Browse))
        assertEquals(listOf(14), ayahs(ReadContext.Random))
        assertEquals(listOf(15), ayahs(ReadContext.Log))
    }

    @Test
    fun eventsOfAContextOnADayAreOrderedByMomentThenId() = runTest {
        val ctx = ReadContext.Bookmark(7)
        log.insertEvents(
            listOf(
                event(3, context = ctx, at = t0.plusSeconds(30)),
                event(2, context = ctx, at = t0),
                event(4, context = ctx, at = t0),
                event(2, context = ctx, day = day2, at = t0),
                event(2, context = ReadContext.Browse, at = t0),
                event(99, context = ctx, at = t0),
            ),
        )

        val found = log.events(ctx, day1, AyahRange.of(2, 4))

        assertEquals(listOf(2, 4, 3), found.map { it.ayahId.value })
        assertTrue(found[0].id < found[1].id)
    }

    @Test
    fun rangeBoundsAreInclusive() = runTest {
        log.insertEvents((8..12).map { event(it) })

        assertEquals(listOf(9, 10, 11), log.events(ReadContext.Browse, day1, AyahRange.of(9, 11)).map { it.ayahId.value })
        assertEquals(setOf(AyahId(9), AyahId(10), AyahId(11)), log.distinctAyahs(ReadContext.Browse, AyahRange.of(9, 11)))
        assertEquals(1, log.distinctAyahCount(ReadContext.Browse, AyahRange.of(12, 12)))
    }

    @Test
    fun ayahCountsCountReadingsPerAyahForOneContextAndDay() = runTest {
        val ctx = ReadContext.Bookmark(1)
        log.insertEvents(
            listOf(
                event(1, context = ctx), event(1, context = ctx), event(1, context = ctx),
                event(2, context = ctx),
                event(3, context = ctx, day = day2),
                event(1, context = ReadContext.Bookmark(2)),
                event(50, context = ctx),
            ),
        )

        assertEquals(mapOf(AyahId(1) to 3, AyahId(2) to 1), log.ayahCounts(ctx, day1, AyahRange.of(1, 7)))
        assertEquals(mapOf(AyahId(3) to 1), log.ayahCounts(ctx, day2, AyahRange.of(1, 7)))
        assertEquals(emptyMap<AyahId, Int>(), log.ayahCounts(ctx, day3, AyahRange.of(1, 7)))
    }

    @Test
    fun distinctAyahsLookAtEveryDayAndCountEachAyahOnce() = runTest {
        val ctx = ReadContext.Khatm(1)
        log.insertEvents(
            listOf(
                event(5, context = ctx, day = day1), event(5, context = ctx, day = day2),
                event(3, context = ctx, day = day3),
                event(4, context = ReadContext.Khatm(2)),
            ),
        )

        assertEquals(setOf(AyahId(3), AyahId(5)), log.distinctAyahs(ctx, all))
        assertEquals(2, log.distinctAyahCount(ctx, all))
        assertEquals(1, log.distinctAyahCount(ctx, AyahRange.of(4, 6)))
        assertEquals(0, log.distinctAyahCount(ReadContext.Khatm(3), all))
    }

    @Test
    fun ayahDayCountsHonourInclusiveAndUnboundedDayBounds() = runTest {
        val ctx = ReadContext.Khatm(1)
        log.insertEvents(
            listOf(
                event(2, day1, ctx), event(1, day1, ctx),
                event(1, day2, ctx), event(1, day2, ctx),
                event(1, day3, ctx),
                event(1, day2, ReadContext.Browse),
            ),
        )
        val d1 = listOf(AyahDayCount(AyahId(1), day1, 1), AyahDayCount(AyahId(2), day1, 1))
        val d2 = listOf(AyahDayCount(AyahId(1), day2, 2))
        val d3 = listOf(AyahDayCount(AyahId(1), day3, 1))

        assertEquals(d1 + d2 + d3, log.ayahDayCounts(ctx, all, null, null))
        assertEquals(d2 + d3, log.ayahDayCounts(ctx, all, day2, null))
        assertEquals(d1 + d2, log.ayahDayCounts(ctx, all, null, day2))
        assertEquals(d2, log.ayahDayCounts(ctx, all, day2, day2))
        assertEquals(d1.take(1) + d2 + d3, log.ayahDayCounts(ctx, AyahRange.of(1, 1), null, null))
        assertEquals(d1 + d2 + d3, log.observeAyahDayCounts(ctx, all, null, null).first())
    }

    @Test
    fun bookmarkAyahCountsCoverEveryBookmarkContextOfTheDayAndNothingElse() = runTest {
        log.insertEvents(
            listOf(
                event(20, context = ReadContext.Bookmark(2)),
                event(7, context = ReadContext.Bookmark(1)), event(7, context = ReadContext.Bookmark(1)),
                event(3, context = ReadContext.Bookmark(1)),
                event(7, context = ReadContext.Khatm(1)),
                event(7, context = ReadContext.Browse),
                event(7, day2, ReadContext.Bookmark(1)),
            ),
        )
        val expected = listOf(BookmarkAyahCount(1, AyahId(3), 1), BookmarkAyahCount(1, AyahId(7), 2), BookmarkAyahCount(2, AyahId(20), 1))

        assertEquals(expected, log.bookmarkAyahCounts(day1))
        assertEquals(expected, log.observeBookmarkAyahCounts(day1).first())
        assertEquals(emptyList<Any>(), log.bookmarkAyahCounts(day3))
    }

    @Test
    fun bookmarkLastReadIsTheNewestDayOfEachBookmarkContext() = runTest {
        log.insertEvents(
            listOf(
                event(7, day1, ReadContext.Bookmark(1)),
                event(8, day3, ReadContext.Bookmark(1)),
                event(9, day2, ReadContext.Bookmark(2)),
                event(9, day3, ReadContext.Browse),
            ),
        )
        assertEquals(mapOf(1L to day3, 2L to day2), log.observeBookmarkLastRead().first())
    }

    @Test
    fun dayCountsCountEventsOnlyAndTallySumsCountTalliesOnly() = runTest {
        log.insertEvents(listOf(event(1, day1), event(2, day1), event(1, day3)))
        log.insertTally(NewTally(day1, 40, null, t0))
        log.insertTally(NewTally(day1, 2, null, t0))
        log.insertTally(NewTally(day2, 7, null, t0))

        assertEquals(mapOf(day1 to 2, day3 to 1), log.observeEventCountsByDay(null, null).first())
        assertEquals(mapOf(day1 to 42, day2 to 7), log.observeTallySumsByDay(null, null).first())

        assertEquals(mapOf(day3 to 1), log.observeEventCountsByDay(day2, null).first())
        assertEquals(mapOf(day1 to 2), log.observeEventCountsByDay(null, day2).first())
        assertEquals(mapOf(day1 to 2), log.observeEventCountsByDay(day1, day1).first())
        assertEquals(mapOf(day2 to 7), log.observeTallySumsByDay(day2, day3).first())
        assertEquals(mapOf(day1 to 42), log.observeTallySumsByDay(null, day1).first())
    }

    @Test
    fun sourceCountsGroupByContextKindAndMethodWithinTheDayBounds() = runTest {
        log.insertEvents(
            listOf(
                event(1, context = ReadContext.Khatm(1), method = ReadMethod.TAP),
                event(2, context = ReadContext.Khatm(2), method = ReadMethod.TAP),
                event(3, context = ReadContext.Khatm(1), method = ReadMethod.AUDIO),
                event(4, context = ReadContext.Browse, method = ReadMethod.BULK),
                event(5, context = ReadContext.Bookmark(1), method = ReadMethod.TAP),
                event(6, day3, ReadContext.Log, method = ReadMethod.MANUAL),
            ),
        )

        assertEquals(
            listOf(
                SourceCount(ContextKind.KHATM, ReadMethod.TAP, 2),
                SourceCount(ContextKind.KHATM, ReadMethod.AUDIO, 1),
                SourceCount(ContextKind.BOOKMARK, ReadMethod.TAP, 1),
                SourceCount(ContextKind.BROWSE, ReadMethod.BULK, 1),
            ),
            log.observeSourceCounts(null, day2).first(),
        )
        assertEquals(listOf(SourceCount(ContextKind.LOG, ReadMethod.MANUAL, 1)), log.observeSourceCounts(day3, day3).first())
    }

    @Test
    fun theNewestEventOfAContextIsTheLastByMomentThenId() = runTest {
        val ctx = ReadContext.Khatm(1)
        assertNull(log.observeNewestEvent(ctx).first())

        log.insertEvents(
            listOf(
                event(30, context = ctx, at = t0.plusSeconds(100)),
                event(31, context = ctx, at = t0.plusSeconds(100)),
                event(40, context = ctx, at = t0),
                event(50, context = ReadContext.Khatm(2), at = t0.plusSeconds(999)),
            ),
        )

        assertEquals(31, log.observeNewestEvent(ctx).first()!!.ayahId.value)
        assertEquals(50, log.observeNewestEvent(ReadContext.Khatm(2)).first()!!.ayahId.value)
        assertNull(log.observeNewestEvent(ReadContext.Browse).first())
    }

    @Test
    fun recencyIsTheLatestDayAndNumberOfReadingsPerAyahOverEveryContext() = runTest {
        log.insertEvents(
            listOf(
                event(5, day1, ReadContext.Browse),
                event(5, day3, ReadContext.Khatm(1)),
                event(5, day2, ReadContext.Bookmark(1)),
                event(2, day2),
                event(900, day1),
            ),
        )

        assertEquals(
            listOf(AyahRecency(AyahId(2), day2, 1), AyahRecency(AyahId(5), day3, 3)),
            log.observeRecency(AyahRange.of(1, 100)).first(),
        )
    }

    @Test
    fun eventsAndTalliesOfADayComeInRecordingOrder() = runTest {
        log.insertEvents(
            listOf(
                event(3, day1, at = t0.plusSeconds(5)),
                event(1, day1, ReadContext.Khatm(1), at = t0),
                event(2, day2),
            ),
        )
        val first = log.insertTally(NewTally(day1, 3, "a", t0))
        log.insertTally(NewTally(day2, 4, "b", t0))
        val third = log.insertTally(NewTally(day1, 5, "c", t0))

        assertEquals(listOf(1, 3), log.observeEventsOfDay(day1).first().map { it.ayahId.value })
        assertEquals(listOf(first, third), log.observeTalliesOfDay(day1).first())
        assertEquals(emptyList<Any>(), log.observeEventsOfDay(day3).first())
    }

    // =========================================================== read log: observation

    @Test
    fun observedCountsEmitTheCurrentValueThenEveryChange() = runTest {
        val ctx = ReadContext.Bookmark(1)
        log.observeAyahCounts(ctx, day1, AyahRange.of(1, 7)).test {
            assertEquals(emptyMap<AyahId, Int>(), awaitItem())

            val inserted = log.insertEvents(listOf(event(2, context = ctx)))
            assertEquals(mapOf(AyahId(2) to 1), awaitItem())

            log.insertEvents(listOf(event(2, context = ctx), event(3, context = ctx)))
            assertEquals(mapOf(AyahId(2) to 2, AyahId(3) to 1), awaitItem())

            log.deleteEvents(inserted.map { it.id })
            assertEquals(mapOf(AyahId(2) to 1, AyahId(3) to 1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anObserverIsNotToldAboutChangesThatDoNotAffectItsValue() = runTest {
        val ctx = ReadContext.Bookmark(1)
        log.observeAyahCounts(ctx, day1, AyahRange.of(1, 7)).test {
            assertEquals(emptyMap<AyahId, Int>(), awaitItem())

            // Another context, another day, outside the range, and a tally: none of them is this value.
            log.insertEvents(listOf(event(2, context = ReadContext.Bookmark(2)), event(2, day2, ctx), event(100, context = ctx)))
            log.insertTally(NewTally(day1, 9, null, t0))
            log.insertEvents(listOf(event(5, context = ctx)))

            assertEquals(mapOf(AyahId(5) to 1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dayTotalsInputsReEmitWhenEventsAndTalliesChange() = runTest {
        log.observeEventCountsByDay(day1, day2).test {
            assertEquals(emptyMap<LocalDate, Int>(), awaitItem())
            log.insertEvents(listOf(event(1, day1), event(2, day2)))
            assertEquals(mapOf(day1 to 1, day2 to 1), awaitItem())
            log.deleteAll()
            assertEquals(emptyMap<LocalDate, Int>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        log.observeTallySumsByDay(null, null).test {
            assertEquals(emptyMap<LocalDate, Int>(), awaitItem())
            val tally = log.insertTally(NewTally(day1, 40, null, t0))
            assertEquals(mapOf(day1 to 40), awaitItem())
            log.updateTally(tally.copy(count = 41))
            assertEquals(mapOf(day1 to 41), awaitItem())
            log.deleteTally(tally.id)
            assertEquals(emptyMap<LocalDate, Int>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theNewestEventAndCoverageFollowTheLog() = runTest {
        val ctx = ReadContext.Khatm(1)
        log.observeNewestEvent(ctx).test {
            assertNull(awaitItem())
            val first = log.insertEvents(listOf(event(1, context = ctx, at = t0))).single()
            assertEquals(first, awaitItem())
            val second = log.insertEvents(listOf(event(2, context = ctx, at = t0.plusSeconds(1)))).single()
            assertEquals(second, awaitItem())
            log.deleteEvents(listOf(second.id))
            assertEquals(first, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        log.observeDistinctAyahs(ctx, all).test {
            assertEquals(setOf(AyahId(1)), awaitItem())
            log.insertEvents(listOf(event(9, day2, ctx)))
            assertEquals(setOf(AyahId(1), AyahId(9)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================== khatms

    @Test
    fun aKhatmGetsAGeneratedIdOrKeepsItsOwn() = runTest {
        val generated = khatms.insert(khatm(name = "first"), t0)
        val kept = khatms.insert(khatm(id = 40, name = "restored"), t0)
        val next = khatms.insert(khatm(name = "after"), t0)

        assertTrue(generated > 0)
        assertEquals(40L, kept)
        assertTrue("generated ids continue after the largest: $next", next > 40)
        assertEquals(listOf(generated, 40L, next), khatms.khatms().map { it.id })
        assertEquals("restored", khatms.khatm(40)!!.name)
        assertNull(khatms.khatm(41_000))
    }

    @Test
    fun insertingAKhatmUnderATakenIdFails() = runTest {
        val id = khatms.insert(khatm(), t0)
        try {
            khatms.insert(khatm(id = id, name = "clash"), t0)
            fail("a second khatm with id $id was accepted")
        } catch (_: Exception) {
        }
        assertEquals(listOf("Khatm"), khatms.khatms().map { it.name })
    }

    @Test
    fun everyFieldOfAKhatmSurvivesInsertAndUpdate() = runTest {
        val original = Khatm(0, "Ramadan", day1, dailyPages = 4.5f, assumedCoveredUpTo = 293, completedAt = null)
        val id = khatms.insert(original, t0)
        assertEquals(original.copy(id = id), khatms.khatm(id))

        val changed = Khatm(id, "Ramadan 1448", day2, dailyPages = null, assumedCoveredUpTo = 0, completedAt = t0.plusSeconds(77))
        khatms.update(changed)
        assertEquals(changed, khatms.khatm(id))

        khatms.update(changed.copy(id = 999, name = "nobody"))
        assertEquals(listOf(changed), khatms.khatms())
    }

    @Test
    fun khatmsAreObservedInIdOrderAndDeletedWithoutTouchingTheLog() = runTest {
        khatms.observeKhatms().test {
            assertEquals(emptyList<Khatm>(), awaitItem())
            val a = khatms.insert(khatm(name = "a"), t0)
            assertEquals(listOf("a"), awaitItem().map { it.name })
            val b = khatms.insert(khatm(name = "b"), t0)
            assertEquals(listOf(a, b), awaitItem().map { it.id })

            log.insertEvents(listOf(event(1, context = ReadContext.Khatm(a))))
            khatms.delete(a)
            assertEquals(listOf(b), awaitItem().map { it.id })
            assertEquals(1, log.distinctAyahCount(ReadContext.Khatm(a), all))

            khatms.deleteAll()
            assertEquals(emptyList<Khatm>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================== bookmarks and groups

    @Test
    fun everyFieldOfABookmarkSurvivesIncludingItsSchedule() = runTest {
        val groupId = bookmarks.insertGroup(group())
        val schedules = listOf(
            null,
            Schedule(setOf(DayOfWeek.MONDAY), window = null),
            Schedule(setOf(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY), TimeWindow(LocalTime.of(5, 30), LocalTime.of(7, 0))),
            // A window that crosses midnight.
            Schedule(Schedule.EVERY_DAY, TimeWindow(LocalTime.of(22, 0), LocalTime.of(1, 15))),
            Schedule.once(TimeWindow(LocalTime.MIDNIGHT, LocalTime.NOON)),
        )
        for (schedule in schedules) {
            val original = Bookmark(
                id = 0,
                range = AyahRange.of(2158, 2267),
                kind = BookmarkKind.SURAH,
                label = "Kahf",
                description = "Fridays\nwith the family",
                repeatTarget = 3,
                groupId = groupId,
                schedule = schedule,
                sortIndex = 4,
                createdAt = t0,
                updatedAt = t0.plusSeconds(9),
            )
            val id = bookmarks.insertBookmark(original)
            assertEquals(original.copy(id = id), bookmarks.bookmark(id))
        }
        assertEquals(BookmarkKind.entries.toSet(), BookmarkKind.entries.map { kind ->
            bookmarks.bookmark(bookmarks.insertBookmark(bookmark().copy(kind = kind)))!!.kind
        }.toSet())
    }

    @Test
    fun aGroupKeepsItsScheduleAndBothKindsOfIdRule() = runTest {
        val scheduled = group(name = "Friday", schedule = Schedule(setOf(DayOfWeek.FRIDAY), TimeWindow(LocalTime.of(11, 0), LocalTime.of(13, 0))))
            .copy(description = "after prayer", sortIndex = 2)
        val generated = bookmarks.insertGroup(scheduled)
        val kept = bookmarks.insertGroup(group(id = 30, name = "restored"))
        val next = bookmarks.insertGroup(group(name = "after"))

        assertEquals(scheduled.copy(id = generated), bookmarks.group(generated))
        assertEquals(30L, kept)
        assertTrue(next > 30)
        assertEquals(listOf(generated, 30L, next), bookmarks.groups().map { it.id })
        assertEquals(bookmarks.groups(), bookmarks.observeGroups().first())

        val renamed = scheduled.copy(id = generated, name = "Jumu'ah", schedule = null)
        bookmarks.updateGroup(renamed)
        assertEquals(renamed, bookmarks.group(generated))
        assertNull(bookmarks.group(31_000))
    }

    @Test
    fun bookmarksAreListedInIdOrderWhateverTheirSortIndex() = runTest {
        val a = bookmarks.insertBookmark(bookmark(sort = 9))
        val kept = bookmarks.insertBookmark(bookmark(id = 50, sort = 0))
        val c = bookmarks.insertBookmark(bookmark(sort = 5))

        assertEquals(listOf(a, 50L, c), bookmarks.bookmarks().map { it.id })
        assertTrue(c > 50)
        assertEquals(bookmarks.bookmarks(), bookmarks.observeBookmarks().first())
    }

    @Test
    fun aBookmarkCannotPointAtAGroupThatDoesNotExist() = runTest {
        try {
            bookmarks.insertBookmark(bookmark(groupId = 77))
            fail("a bookmark in a missing group was accepted")
        } catch (_: Exception) {
        }
        val id = bookmarks.insertBookmark(bookmark())
        try {
            bookmarks.updateBookmark(bookmark(id = id, groupId = 77))
            fail("a bookmark was moved to a missing group")
        } catch (_: Exception) {
        }
        assertNull(bookmarks.bookmark(id)!!.groupId)
        assertEquals(1, bookmarks.bookmarks().size)
    }

    @Test
    fun deletingAGroupUngroupsItsBookmarksInsteadOfDeletingThem() = runTest {
        val doomed = bookmarks.insertGroup(group(name = "doomed"))
        val other = bookmarks.insertGroup(group(name = "other"))
        val inDoomed = bookmarks.insertBookmark(bookmark(groupId = doomed))
        val inOther = bookmarks.insertBookmark(bookmark(groupId = other))
        val loose = bookmarks.insertBookmark(bookmark())

        bookmarks.observeBookmarks().test {
            assertEquals(listOf(doomed, other, null), awaitItem().map { it.groupId })

            bookmarks.deleteGroup(doomed)

            assertEquals(listOf(null, other, null), awaitItem().map { it.groupId })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(inDoomed, inOther, loose), bookmarks.bookmarks().map { it.id })
        assertEquals(listOf(other), bookmarks.groups().map { it.id })
    }

    @Test
    fun bookmarksAreUpdatedDeletedAndClearedWithoutTouchingTheLog() = runTest {
        val groupId = bookmarks.insertGroup(group())
        val id = bookmarks.insertBookmark(bookmark(groupId = groupId))
        log.insertEvents(listOf(event(3, context = ReadContext.Bookmark(id))))

        bookmarks.observeBookmarks().test {
            assertEquals(1, awaitItem().size)

            val edited = bookmark(id = id, start = 10, end = 20, schedule = Schedule.daily()).copy(label = "edited", repeatTarget = 2)
            bookmarks.updateBookmark(edited)
            assertEquals(listOf(edited), awaitItem())

            bookmarks.deleteBookmark(id)
            assertEquals(emptyList<Bookmark>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(bookmarks.bookmark(id))
        assertEquals(1, log.bookmarkAyahCounts(day1).size)

        bookmarks.insertBookmark(bookmark(groupId = groupId))
        bookmarks.deleteAll()
        assertEquals(emptyList<Any>(), bookmarks.bookmarks())
        assertEquals(emptyList<Any>(), bookmarks.groups())
    }

    // =========================================================== transactions

    @Test
    fun aTransactionReturnsItsResultAndKeepsItsWrites() = runTest {
        val id = tx.inTransaction {
            log.insertEvents(listOf(event(1)))
            khatms.insert(khatm(), t0)
        }

        assertNotNull(khatms.khatm(id))
        assertEquals(1, log.allEvents().size)
    }

    @Test
    fun readsInsideATransactionSeeItsOwnWrites() = runTest {
        tx.inTransaction {
            val inserted = log.insertEvents(listOf(event(4, context = ReadContext.Khatm(1)), event(4, context = ReadContext.Khatm(1))))
            assertEquals(mapOf(AyahId(4) to 2), log.ayahCounts(ReadContext.Khatm(1), day1, all))
            assertEquals(inserted, log.allEvents())
            val groupId = bookmarks.insertGroup(group())
            val bookmarkId = bookmarks.insertBookmark(bookmark(groupId = groupId))
            assertEquals(groupId, bookmarks.bookmark(bookmarkId)!!.groupId)
        }
    }

    @Test
    fun aFailingTransactionLeavesEveryStoreAsItWas() = runTest {
        val keptEvent = log.insertEvents(listOf(event(1))).single()
        val keptTally = log.insertTally(NewTally(day1, 5, null, t0))
        val keptKhatm = khatms.insert(khatm(name = "kept"), t0)
        val keptGroup = bookmarks.insertGroup(group(name = "kept"))
        val keptBookmark = bookmarks.insertBookmark(bookmark(groupId = keptGroup))

        try {
            tx.inTransaction {
                log.insertEvents(listOf(event(2), event(3)))
                log.deleteEvents(listOf(keptEvent.id))
                log.updateTally(keptTally.copy(count = 500))
                log.insertTally(NewTally(day2, 9, null, t0))
                khatms.insert(khatm(name = "lost"), t0)
                khatms.update(khatm(id = keptKhatm, name = "renamed"))
                bookmarks.deleteGroup(keptGroup)
                bookmarks.insertBookmark(bookmark())
                throw Boom()
            }
        } catch (_: Boom) {
        }

        assertEquals(listOf(keptEvent), log.allEvents())
        assertEquals(listOf(keptTally), log.allTallies())
        assertEquals(listOf("kept"), khatms.khatms().map { it.name })
        assertEquals(listOf(keptGroup), bookmarks.groups().map { it.id })
        assertEquals(listOf(keptBookmark), bookmarks.bookmarks().map { it.id })
        assertEquals(keptGroup, bookmarks.bookmark(keptBookmark)!!.groupId)
        assertEquals(mapOf(day1 to 1), log.observeEventCountsByDay(null, null).first())
    }

    @Test
    fun nestedTransactionsCommitTogether() = runTest {
        tx.inTransaction {
            log.insertEvents(listOf(event(1)))
            tx.inTransaction {
                log.insertEvents(listOf(event(2)))
                tx.inTransaction { khatms.insert(khatm(), t0) }
            }
            assertEquals(2, log.allEvents().size)
        }

        assertEquals(listOf(1, 2), log.allEvents().map { it.ayahId.value })
        assertEquals(1, khatms.khatms().size)
    }

    @Test
    fun aFailureInANestedTransactionUndoesTheOuterOneToo() = runTest {
        try {
            tx.inTransaction {
                log.insertEvents(listOf(event(1)))
                tx.inTransaction {
                    khatms.insert(khatm(), t0)
                    throw Boom()
                }
            }
        } catch (_: Boom) {
        }

        assertEquals(emptyList<Any>(), log.allEvents())
        assertEquals(emptyList<Any>(), khatms.khatms())
    }

    @Test
    fun aFailureAfterANestedTransactionFinishedUndoesTheNestedWritesToo() = runTest {
        try {
            tx.inTransaction {
                tx.inTransaction { log.insertEvents(listOf(event(1), event(2))) }
                bookmarks.insertGroup(group())
                throw Boom()
            }
        } catch (_: Boom) {
        }

        assertEquals(emptyList<Any>(), log.allEvents())
        assertEquals(emptyList<Any>(), bookmarks.groups())

        // The runner is usable again afterwards.
        tx.inTransaction { log.insertEvents(listOf(event(3))) }
        assertEquals(listOf(3), log.allEvents().map { it.ayahId.value })
    }
}
