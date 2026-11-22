package io.github.mehrdad_abdi.qintar.feature.insights

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.dayDetailViewModel
import io.github.mehrdad_abdi.qintar.testing.logReadingViewModel
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Day detail's use cases (R-INS-2, R-TRK-6) through its view model over the in-memory domain. */
class DayDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture() // Sunday 15 Nov 2026, 09:00
    private val today = app.clock.today()
    private val yesterday = today.minusDays(1)

    private suspend fun read(
        day: LocalDate,
        from: Int,
        to: Int,
        context: ReadContext = ReadContext.Browse,
        method: ReadMethod = ReadMethod.BULK,
    ) = app.tracker.markRange(AyahRange.of(from, to), context, method, RangeMode.ADD, day = day)

    private fun open(day: LocalDate) = app.dayDetailViewModel(day.toString(), main.dispatcher)

    @Test
    fun `an unreadable date is reported, not crashed on`() {
        val vm = app.dayDetailViewModel("not-a-date", main.dispatcher)
        assertEquals(DayPhase.INVALID, vm.state.value.phase)
        assertFalse(vm.state.value.canEdit)
    }

    @Test
    fun `a day shows its total and rank`() = runTest {
        read(yesterday, 1, 60)
        val s = open(yesterday).state.value
        assertEquals(DayPhase.READY, s.phase)
        assertEquals(60, s.total)
        assertEquals(Rank.DHAKIR, s.rank)
        assertTrue(s.canEdit)
        assertFalse(s.isToday)
    }

    @Test
    fun `entries are grouped by context with names and ayah runs collapsed`() = runTest {
        val khatm = app.khatms.start("Ramadan")
        val bookmark = app.library.create(BookmarkDrafts.surah(112, app.index))
        read(yesterday, 1, 7, ReadContext.Khatm(khatm.id), ReadMethod.TAP)
        read(yesterday, 8, 8, ReadContext.Khatm(khatm.id), ReadMethod.TAP)
        read(yesterday, 8, 8, ReadContext.Khatm(khatm.id), ReadMethod.TAP) // ayah 8 twice
        read(yesterday, 20, 22, ReadContext.Khatm(khatm.id), ReadMethod.AUDIO) // a gap: a second run
        read(yesterday, app.ayah(112, 1).value, app.ayah(112, 4).value, ReadContext.Bookmark(bookmark.id))
        read(yesterday, 100, 101, ReadContext.Browse, ReadMethod.TAP)
        read(yesterday, 300, 300, ReadContext.Random, ReadMethod.TAP)
        read(yesterday, 400, 402, ReadContext.Log, ReadMethod.MANUAL)

        val s = open(yesterday).state.value
        assertEquals(7 + 2 + 3 + 4 + 2 + 1 + 3, s.total)
        assertEquals(
            listOf(ReadContext.Khatm(khatm.id), ReadContext.Bookmark(bookmark.id), ReadContext.Browse, ReadContext.Random, ReadContext.Log),
            s.groups.map { it.context },
        )

        val k = s.groups[0]
        assertEquals(EntryLabel.Khatm("Ramadan"), k.label)
        assertEquals(12, k.total)
        assertEquals(setOf(ReadMethod.TAP, ReadMethod.AUDIO), k.methods)
        // 1..7 once, then 8 twice (a new run because the count differs), then the gap 20..22.
        assertEquals(listOf(1 to 7, 8 to 8, 20 to 22), k.runs.map { it.range.start.value to it.range.end.value })
        assertEquals(listOf(1, 2, 1), k.runs.map { it.readings })
        assertEquals(listOf(7, 2, 3), k.runs.map { it.total })
        assertEquals(1, k.runs[0].firstSurah.number)
        assertEquals(7, k.runs[0].lastAyah)

        val b = s.groups[1]
        assertTrue((b.label as EntryLabel.Bookmark).title is BookmarkTitle.Surah)
        assertEquals(1, b.runs.size)
        assertEquals(112, b.runs.single().firstSurah.number)
        assertEquals(EntryLabel.Browse, s.groups[2].label)
        assertEquals(EntryLabel.Random, s.groups[3].label)
        assertEquals(EntryLabel.Manual, s.groups[4].label)
    }

    @Test
    fun `a run crossing a surah boundary names both ends`() = runTest {
        // Al-Fatihah 7 is ayah 7; Al-Baqarah 1 is ayah 8.
        read(yesterday, 6, 9)
        val run = open(yesterday).state.value.groups.single().runs.single()
        assertEquals(1, run.firstSurah.number)
        assertEquals(6, run.firstAyah)
        assertEquals(2, run.lastSurah.number)
        assertEquals(2, run.lastAyah)
    }

    @Test
    fun `a deleted khatm keeps its entries without a name`() = runTest {
        val khatm = app.khatms.start("Old")
        read(yesterday, 1, 3, ReadContext.Khatm(khatm.id), ReadMethod.TAP)
        app.khatms.delete(khatm.id)
        assertEquals(EntryLabel.Khatm(null), open(yesterday).state.value.groups.single().label)
    }

    @Test
    fun `tallies are listed with their notes and count toward the total`() = runTest {
        app.tracker.addTally(yesterday, 30, "Tarawih")
        app.tracker.addTally(yesterday, 5, null)
        read(yesterday, 1, 7)
        val s = open(yesterday).state.value
        assertEquals(42, s.total)
        assertEquals(listOf(30 to "Tarawih", 5 to null), s.tallies.map { it.count to it.note })
        assertEquals(Rank.NOT_HEEDLESS, s.rank)
    }

    @Test
    fun `adding a passage to a past day raises its rank and repairs a broken streak`() = runTest {
        // Streak days: 12, 13, [14 missing], 15. Without 14 the current streak is 1.
        read(today.minusDays(3), 1, 10)
        read(today.minusDays(2), 1, 10)
        read(today, 1, 10)
        val day = open(yesterday)
        assertEquals(0, day.state.value.total)
        assertEquals(Rank.NONE, day.state.value.rank)
        assertEquals(1, day.state.value.streak.current)
        assertEquals(2, day.state.value.streak.longest)
        assertFalse(day.state.value.qualifiesForStreak)

        val log = app.logReadingViewModel(main.dispatcher)
        log.start(yesterday)
        log.onToAyahText("7")
        log.onSubmit()
        assertEquals(7, day.state.value.total)
        assertEquals("7 is below the first rank", Rank.NONE, day.state.value.rank)
        assertEquals(1, day.state.value.streak.current)

        // The same passage once more: 14 ayahs, past the first rank.
        log.onResultHandled()
        log.onSubmit()
        assertEquals(14, day.state.value.total)
        assertEquals(Rank.NOT_HEEDLESS, day.state.value.rank)
        assertTrue(day.state.value.qualifiesForStreak)
        assertEquals("the broken streak is repaired", 4, day.state.value.streak.current)
        assertEquals(4, day.state.value.streak.longest)
    }

    @Test
    fun `adding a tally to a past day counts for its rank`() = runTest {
        val day = open(yesterday)
        val log = app.logReadingViewModel(main.dispatcher)
        log.start(yesterday)
        log.onMode(LogMode.NUMBER)
        log.onNumberText("120")
        log.onNote("Group recitation")
        log.onSubmit()
        assertEquals(120, day.state.value.total)
        assertEquals(Rank.QANIT, day.state.value.rank)
        assertEquals("Group recitation", day.state.value.tallies.single().note)
        assertEquals(1, day.state.value.streak.current)
    }

    @Test
    fun `one ayah of a run can be deleted, which splits the run, and Undo restores it`() = runTest {
        // Owner, 2026-11-22: an unwanted check is taken back here, by deleting it; "Set count" is gone.
        read(yesterday, 1, 10, ReadContext.Browse, ReadMethod.TAP)
        val vm = open(yesterday)
        val run = vm.state.value.groups.single().runs.single()
        assertEquals("a run lists its single ayahs", (1..10).toList(), run.ayahs.map { it.id.value })

        vm.onDeleteRun(ReadContext.Browse, AyahRange.single(run.ayahs[4].id))
        assertEquals(9, vm.state.value.total)
        assertEquals(listOf(4, 5), vm.state.value.groups.single().runs.map { it.range.size })
        assertEquals(UndoKind.DELETED, vm.state.value.notice?.kind)

        vm.onUndo()
        assertEquals(10, vm.state.value.total)
        assertEquals(1, vm.state.value.groups.single().runs.size)
    }

    @Test
    fun `deleting a run removes it, and Undo puts it back`() = runTest {
        read(yesterday, 1, 7, ReadContext.Browse, ReadMethod.TAP)
        read(yesterday, 20, 29, ReadContext.Browse, ReadMethod.TAP)
        val vm = open(yesterday)
        assertEquals(17, vm.state.value.total)
        assertEquals(Rank.NOT_HEEDLESS, vm.state.value.rank)

        vm.onDeleteRun(ReadContext.Browse, AyahRange.of(20, 29))
        assertEquals(7, vm.state.value.total)
        assertEquals(Rank.NONE, vm.state.value.rank)
        assertEquals(UndoKind.DELETED, vm.state.value.notice?.kind)
        assertEquals(1, vm.state.value.groups.single().runs.size)

        vm.onUndo()
        assertEquals(17, vm.state.value.total)
        assertEquals(Rank.NOT_HEEDLESS, vm.state.value.rank)
        assertEquals(2, vm.state.value.groups.single().runs.size)
    }

    @Test
    fun `deleting a whole group is one Undo`() = runTest {
        read(yesterday, 1, 7, ReadContext.Browse, ReadMethod.TAP)
        read(yesterday, 20, 29, ReadContext.Browse, ReadMethod.TAP)
        read(yesterday, 40, 45, ReadContext.Random, ReadMethod.TAP)
        val vm = open(yesterday)

        vm.onDeleteGroup(ReadContext.Browse)
        assertEquals(6, vm.state.value.total)
        assertEquals(listOf<ReadContext>(ReadContext.Random), vm.state.value.groups.map { it.context })

        vm.onUndo()
        assertEquals(23, vm.state.value.total)
        assertEquals(2, vm.state.value.groups.size)
    }

    @Test
    fun `a tally can be changed, deleted and each change undone`() = runTest {
        val tally = app.tracker.addTally(yesterday, 20, "Morning")
        val vm = open(yesterday)

        vm.onUpdateTally(tally.id, 60, "Morning, more")
        assertEquals(60, vm.state.value.total)
        assertEquals("Morning, more", vm.state.value.tallies.single().note)
        assertEquals(Rank.DHAKIR, vm.state.value.rank)
        vm.onUndo()
        assertEquals(20, vm.state.value.total)
        assertEquals("Morning", vm.state.value.tallies.single().note)

        vm.onDeleteTally(tally.id)
        assertEquals(0, vm.state.value.total)
        assertTrue(vm.state.value.tallies.isEmpty())
        assertEquals(UndoKind.TALLY_DELETED, vm.state.value.notice?.kind)
        vm.onUndo()
        assertEquals(20, vm.state.value.total)
        assertEquals("Morning", vm.state.value.tallies.single().note)
    }

    @Test
    fun `a tally changed to zero is refused with a message`() = runTest {
        val tally = app.tracker.addTally(yesterday, 20, null)
        val vm = open(yesterday)
        vm.onUpdateTally(tally.id, 0, null)
        assertEquals(DayMessage.COUNT_OUT_OF_RANGE, vm.state.value.message)
        assertEquals(20, vm.state.value.total)
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `a future day cannot be edited and a reading for it is refused`() = runTest {
        val tomorrow = today.plusDays(1)
        val vm = open(tomorrow)
        assertTrue(vm.state.value.isFuture)
        assertFalse(vm.state.value.canEdit)
        assertTrue(vm.state.value.isEmpty)

        val log = app.logReadingViewModel(main.dispatcher)
        log.start(tomorrow) // clamped to today by the form
        assertEquals(today, log.state.value.day)
        log.onDay(tomorrow)
        log.onSubmit()
        assertEquals(LogRefusal.FUTURE_DAY, log.state.value.refusal)
        assertEquals(0, vm.state.value.total)
    }

    @Test
    fun `today can be edited and follows the reading day`() = runTest {
        read(today, 1, 10)
        val vm = open(today)
        assertTrue(vm.state.value.isToday)
        assertTrue(vm.state.value.canEdit)
        assertEquals(1, vm.state.value.streak.current)
        app.clock.advanceDays(1)
        assertFalse(vm.state.value.isToday)
        assertEquals(today.plusDays(1), vm.state.value.today)
        assertNotNull(vm.state.value.groups.singleOrNull())
        assertTrue("yesterday's streak is at risk", vm.state.value.streak.atRisk)
    }
}
