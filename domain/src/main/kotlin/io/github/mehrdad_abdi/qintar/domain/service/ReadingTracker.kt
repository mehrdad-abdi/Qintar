package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Batch
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.model.Tally
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * The only writer of the reading log (§4.2, §7.6). Screens, the playback service and importers all go
 * through it.
 *
 * Failures: [DomainFailure.FutureDay] when a day after today is given, [DomainFailure.CountOutOfRange]
 * for a count outside 0…[MAX_COUNT], a non-positive `times` or tally.
 */
interface ReadingTracker {
    /** +1 for one ayah, on today. */
    suspend fun mark(ayah: AyahId, context: ReadContext, method: ReadMethod): ReadEvent

    /**
     * Make the count of (ayah, context, day) equal to [count]: adds events, or deletes the newest.
     * Returns null when the count was already [count].
     */
    suspend fun setCount(ayah: AyahId, context: ReadContext, day: LocalDate, count: Int): Batch?

    /** [setCount] for every ayah of a passage as one undoable action ("Set count…" on a bookmark). */
    suspend fun setRangeCount(range: AyahRange, context: ReadContext, day: LocalDate, count: Int): Batch?

    /**
     * FILL: one reading for ayahs that have none in scope. ADD: [times] more readings for every ayah.
     * [day] null = today. FILL accepts only `times == 1`.
     *
     * "None in scope" means: in a khatm, not covered (no event of that khatm on any day and outside its
     * assumed prefix); in any other context, no event in that context on [day].
     */
    suspend fun markRange(
        range: AyahRange,
        context: ReadContext,
        method: ReadMethod,
        mode: RangeMode,
        times: Int = 1,
        day: LocalDate? = null,
    ): Batch

    /** [markRange] over several passages as one action with one Undo: a full reading of a group. */
    suspend fun markSegments(
        segments: List<Segment>,
        method: ReadMethod,
        mode: RangeMode,
        times: Int = 1,
        day: LocalDate? = null,
    ): Batch

    /**
     * In a khatm: is a tap on [ayah] out of sequence, and what would filling the gap cover? (§4.2.1)
     * Null means "write immediately": the ayah is covered, is ayah 1, or follows a covered ayah.
     * Otherwise the uncovered run that ends at [ayah]; its size is the *n* of the dialog.
     */
    suspend fun khatmGap(khatmId: Long, ayah: AyahId): AyahRange?

    /** Reverts a bulk action: deletes what it added and restores what it removed. */
    suspend fun undo(batch: Batch)

    suspend fun delete(eventId: Long)

    suspend fun addTally(day: LocalDate, count: Int, note: String?): Tally

    /** Changes a tally's count and note (Day detail → change the count of an entry). */
    suspend fun updateTally(id: Long, count: Int, note: String?)

    suspend fun deleteTally(id: Long)

    companion object {
        /** Upper bound of "Set count…" and of the repeat count of one bulk action. */
        const val MAX_COUNT = 9999
    }
}

/**
 * The write path of backup import and v1 migration. Kept apart from [ReadingTracker] so that screens
 * cannot reach it, yet implemented by the same class so that the log still has one writer.
 */
interface ReadingLogImporter {
    /** Appends records exactly as given: day, moment, context, method and batch id are preserved. */
    suspend fun importRecords(events: List<NewReadEvent>, tallies: List<NewTally>)

    /** Empties the log ("Replace everything"). */
    suspend fun clearLog()
}

fun interface BatchIdFactory {
    fun next(): String
}

class DefaultReadingTracker(
    private val log: ReadLogStore,
    private val khatms: KhatmStore,
    private val clock: DayClock,
    private val tx: TransactionRunner,
    private val batchIds: BatchIdFactory,
) : ReadingTracker, ReadingLogImporter {

    @Inject
    constructor(log: ReadLogStore, khatms: KhatmStore, clock: DayClock, tx: TransactionRunner) :
        this(log, khatms, clock, tx, BatchIdFactory { UUID.randomUUID().toString() })

    override suspend fun mark(ayah: AyahId, context: ReadContext, method: ReadMethod): ReadEvent =
        tx.inTransaction {
            // today() is asked here, at the moment of writing, never taken from the caller (R-TRK-3).
            val event = NewReadEvent(ayah, clock.today(), clock.now(), context, method, batchId = null)
            log.insertEvents(listOf(event)).single().also { syncKhatmCompletion(listOf(context)) }
        }

    override suspend fun setCount(ayah: AyahId, context: ReadContext, day: LocalDate, count: Int): Batch? =
        setRangeCount(AyahRange.single(ayah), context, day, count)

    override suspend fun setRangeCount(range: AyahRange, context: ReadContext, day: LocalDate, count: Int): Batch? {
        if (count !in 0..ReadingTracker.MAX_COUNT) throw DomainFailure.CountOutOfRange(count)
        requireNotFuture(day)
        return tx.inTransaction {
            val existing = log.events(context, day, range).groupBy { it.ayahId.value }
            val now = clock.now()
            val batchId = batchIds.next()
            val toAdd = ArrayList<NewReadEvent>()
            val toRemove = ArrayList<ReadEvent>()
            for (ayah in range.ids) {
                val events = existing[ayah].orEmpty()
                if (events.size < count) {
                    repeat(count - events.size) {
                        toAdd += NewReadEvent(AyahId(ayah), day, now, context, ReadMethod.BULK, batchId)
                    }
                } else if (events.size > count) {
                    // events are oldest first, so the tail is "the newest of that day's events".
                    toRemove += events.subList(count, events.size)
                }
            }
            if (toAdd.isEmpty() && toRemove.isEmpty()) return@inTransaction null
            if (toAdd.isNotEmpty()) log.insertEvents(toAdd)
            if (toRemove.isNotEmpty()) log.deleteEvents(toRemove.map { it.id })
            syncKhatmCompletion(listOf(context))
            Batch(batchId, day, added = toAdd.size, removed = toRemove)
        }
    }

    override suspend fun markRange(
        range: AyahRange,
        context: ReadContext,
        method: ReadMethod,
        mode: RangeMode,
        times: Int,
        day: LocalDate?,
    ): Batch = markSegments(listOf(Segment(context, range)), method, mode, times, day)

    override suspend fun markSegments(
        segments: List<Segment>,
        method: ReadMethod,
        mode: RangeMode,
        times: Int,
        day: LocalDate?,
    ): Batch {
        if (times !in 1..ReadingTracker.MAX_COUNT) throw DomainFailure.CountOutOfRange(times)
        // "Fill" gives an unread ayah its one reading; repeating it would contradict what it means.
        if (mode == RangeMode.FILL && times != 1) throw DomainFailure.CountOutOfRange(times)
        day?.let { requireNotFuture(it) }
        return tx.inTransaction {
            val target = day ?: clock.today()
            val now = clock.now()
            val batchId = batchIds.next()
            var added = 0
            for (segment in segments) {
                val ayahs = when (mode) {
                    RangeMode.ADD -> segment.range.ids.toList()
                    RangeMode.FILL -> unreadIn(segment, target)
                }
                val events = ArrayList<NewReadEvent>(ayahs.size * times)
                // Whole passes in mushaf order: the newest event is then the passage's last ayah, which
                // is what puts a khatm's resume position after a bulk mark.
                repeat(times) {
                    for (ayah in ayahs) events += NewReadEvent(AyahId(ayah), target, now, segment.context, method, batchId)
                }
                // Written per segment so that a later segment of the same context sees these as read.
                if (events.isNotEmpty()) log.insertEvents(events)
                added += events.size
            }
            syncKhatmCompletion(segments.map { it.context })
            Batch(batchId, target, added)
        }
    }

    private suspend fun unreadIn(segment: Segment, day: LocalDate): List<Int> {
        val context = segment.context
        val read: Set<Int> = if (context is ReadContext.Khatm) {
            log.distinctAyahs(context, segment.range).mapTo(HashSet()) { it.value }
        } else {
            log.ayahCounts(context, day, segment.range).keys.mapTo(HashSet()) { it.value }
        }
        val assumed = if (context is ReadContext.Khatm) khatms.khatm(context.id)?.assumedCoveredUpTo ?: 0 else 0
        return segment.range.ids.filter { it > assumed && it !in read }
    }

    override suspend fun khatmGap(khatmId: Long, ayah: AyahId): AyahRange? {
        val khatm = khatms.khatm(khatmId) ?: return null
        val assumed = khatm.assumedCoveredUpTo
        if (ayah.value == 1 || ayah.value <= assumed) return null
        val coveredUpToHere = log.distinctAyahs(ReadContext.Khatm(khatmId), AyahRange(AyahId.FIRST, ayah))
        if (ayah in coveredUpToHere) return null
        val nearestCovered = maxOf(assumed, coveredUpToHere.maxOfOrNull { it.value } ?: 0)
        if (nearestCovered == ayah.value - 1) return null
        return AyahRange.of(nearestCovered + 1, ayah.value)
    }

    override suspend fun undo(batch: Batch) {
        tx.inTransaction {
            val contexts = (log.eventsOfBatch(batch.id) + batch.removed).map { it.context }
            log.deleteBatch(batch.id)
            if (batch.removed.isNotEmpty()) {
                log.insertEvents(batch.removed.map { NewReadEvent(it.ayahId, it.day, it.readAt, it.context, it.method, it.batchId) })
            }
            syncKhatmCompletion(contexts)
        }
    }

    override suspend fun delete(eventId: Long) {
        tx.inTransaction {
            val event = log.event(eventId) ?: return@inTransaction
            log.deleteEvents(listOf(eventId))
            syncKhatmCompletion(listOf(event.context))
        }
    }

    override suspend fun addTally(day: LocalDate, count: Int, note: String?): Tally {
        if (count !in 1..MAX_TALLY) throw DomainFailure.CountOutOfRange(count)
        requireNotFuture(day)
        return log.insertTally(NewTally(day, count, note.normalized(), clock.now()))
    }

    override suspend fun updateTally(id: Long, count: Int, note: String?) {
        if (count !in 1..MAX_TALLY) throw DomainFailure.CountOutOfRange(count)
        tx.inTransaction {
            val tally = log.tally(id) ?: return@inTransaction
            log.updateTally(tally.copy(count = count, note = note.normalized()))
        }
    }

    override suspend fun deleteTally(id: Long) {
        log.deleteTally(id)
    }

    override suspend fun importRecords(events: List<NewReadEvent>, tallies: List<NewTally>) {
        tx.inTransaction {
            if (events.isNotEmpty()) log.insertEvents(events)
            tallies.forEach { log.insertTally(it) }
            syncKhatmCompletion(events.map { it.context })
        }
    }

    override suspend fun clearLog() {
        log.deleteAll()
    }

    private fun requireNotFuture(day: LocalDate) {
        val today = clock.today()
        if (day > today) throw DomainFailure.FutureDay(day, today)
    }

    /**
     * `completedAt` is the one khatm fact that is a moment rather than a derivation, so it is kept in
     * step with coverage here, in the only place coverage can change: set when the last ayah becomes
     * covered, cleared again if a correction (Undo, Set count, delete) uncovers one.
     */
    private suspend fun syncKhatmCompletion(contexts: List<ReadContext>) {
        for (context in contexts.filterIsInstance<ReadContext.Khatm>().distinct()) {
            val khatm = khatms.khatm(context.id) ?: continue
            val assumed = khatm.assumedCoveredUpTo
            val covered = if (assumed >= Quran.AYAH_COUNT) {
                Quran.AYAH_COUNT
            } else {
                assumed + log.distinctAyahCount(context, AyahRange.of(assumed + 1, Quran.AYAH_COUNT))
            }
            val complete = covered == Quran.AYAH_COUNT
            if (complete && khatm.completedAt == null) {
                khatms.update(khatm.copy(completedAt = clock.now()))
            } else if (!complete && khatm.completedAt != null) {
                khatms.update(khatm.copy(completedAt = null))
            }
        }
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        /** A day cannot hold more than a few readings of the whole Quran; this only stops typos. */
        const val MAX_TALLY = 100_000
    }
}
