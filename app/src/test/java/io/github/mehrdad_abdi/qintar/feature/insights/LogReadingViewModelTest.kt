package io.github.mehrdad_abdi.qintar.feature.insights

import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.logReadingViewModel
import io.github.mehrdad_abdi.qintar.testing.todayViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The manual log's use cases through its view model over the in-memory domain. */
class LogReadingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture() // Sunday 15 Nov 2026, 09:00
    private val today = app.clock.today()

    private fun open(day: java.time.LocalDate? = null) = app.logReadingViewModel(main.dispatcher).also { it.start(day) }

    @Test
    fun `the form opens on today with a passage from the first ayah`() {
        val vm = open()
        val s = vm.state.value
        assertTrue(s.ready)
        assertEquals(today, s.day)
        assertTrue(s.isToday)
        assertEquals(LogMode.PASSAGE, s.mode)
        assertEquals(1, s.addCount)
        assertEquals(114, s.surahs.size)
    }

    @Test
    fun `a passage logged for today shows in Today's count and is written as a manual log reading`() = runTest {
        val todayVm = app.todayViewModel(main.dispatcher)
        val vm = open()
        vm.onToAyahText("7")
        assertEquals(7, vm.state.value.addCount)
        vm.onSubmit()

        val result = vm.state.value.result as LogResult.Passage
        assertEquals(7, result.ayahs)
        assertEquals(7, todayVm.state.value.rank.count)
        val events = app.log.allEvents()
        assertEquals(7, events.size)
        assertTrue(events.all { it.context == ReadContext.Log && it.method == ReadMethod.MANUAL && it.day == today })
        assertEquals(ContextKind.LOG, events.first().context.kind)
    }

    @Test
    fun `a whole surah, a page range and a juz resolve to their ayahs`() {
        val vm = open()
        vm.onMode(LogMode.SURAH)
        vm.onSurah(112)
        assertEquals(4, vm.state.value.addCount)

        vm.onMode(LogMode.PAGES)
        vm.onFromPageText("1")
        vm.onToPageText("2")
        val expected = app.index.pageRange(1).size + app.index.pageRange(2).size
        assertEquals(expected, vm.state.value.addCount)
        assertEquals(1, vm.state.value.summary?.firstPage)
        assertEquals(2, vm.state.value.summary?.lastPage)

        vm.onMode(LogMode.JUZ)
        vm.onJuzText("30")
        assertEquals(app.index.juzRange(30).size, vm.state.value.addCount)
    }

    @Test
    fun `bad positions are explained and cannot be submitted`() = runTest {
        val vm = open()
        vm.onFromSurah(1)
        vm.onFromAyahText("9")
        assertEquals(LogProblem.AYAH_OUT_OF_RANGE, vm.state.value.errors[LogField.FROM_AYAH])
        assertFalse(vm.state.value.canSubmit)

        vm.onFromAyahText("5")
        vm.onToAyahText("2")
        assertEquals(LogProblem.END_BEFORE_START, vm.state.value.errors[LogField.TO_AYAH])
        vm.onSubmit()
        assertTrue(app.log.allEvents().isEmpty())

        vm.onMode(LogMode.PAGES)
        vm.onFromPageText("700")
        assertEquals(LogProblem.PAGE_OUT_OF_RANGE, vm.state.value.errors[LogField.FROM_PAGE])
        vm.onMode(LogMode.JUZ)
        vm.onJuzText("")
        assertEquals(LogProblem.NUMBER_MISSING, vm.state.value.errors[LogField.JUZ])
    }

    @Test
    fun `a plain number becomes a tally with its note`() = runTest {
        val vm = open()
        vm.onMode(LogMode.NUMBER)
        assertFalse("nothing typed yet", vm.state.value.canSubmit)
        vm.onNumberText("25")
        vm.onNote("  Fajr in the car ")
        assertEquals(25, vm.state.value.addCount)
        vm.onSubmit()

        val tally = (vm.state.value.result as LogResult.Number).tally
        assertEquals(25, tally.count)
        assertEquals("Fajr in the car", tally.note)
        assertEquals(today, tally.day)
        assertEquals(25, app.totals.today())
        assertTrue("a tally leaves no events", app.log.allEvents().isEmpty())
    }

    @Test
    fun `a number of zero or beyond the limit is refused`() {
        val vm = open()
        vm.onMode(LogMode.NUMBER)
        vm.onNumberText("0")
        assertEquals(LogProblem.COUNT_OUT_OF_RANGE, vm.state.value.errors[LogField.NUMBER])
        vm.onNumberText("100001")
        assertEquals(LogProblem.COUNT_OUT_OF_RANGE, vm.state.value.errors[LogField.NUMBER])
        assertFalse(vm.state.value.canSubmit)
        vm.onNumberText("100000")
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `a past day gets the reading, not today`() = runTest {
        val yesterday = today.minusDays(1)
        val vm = open(yesterday)
        assertTrue(vm.state.value.isYesterday)
        vm.onToAyahText("7")
        vm.onSubmit()
        assertEquals(7, app.totals.total(yesterday))
        assertEquals(0, app.totals.today())
    }

    @Test
    fun `a future day is refused by the domain and the message is shown`() = runTest {
        val vm = open()
        vm.onDay(today.plusDays(1))
        vm.onToAyahText("7")
        vm.onSubmit()
        assertEquals(LogRefusal.FUTURE_DAY, vm.state.value.refusal)
        assertNull(vm.state.value.result)
        assertTrue(app.log.allEvents().isEmpty())

        vm.onMode(LogMode.NUMBER)
        vm.onNumberText("5")
        vm.onSubmit()
        assertEquals(LogRefusal.FUTURE_DAY, vm.state.value.refusal)
        assertTrue(app.log.allTallies().isEmpty())

        // Choosing a valid day clears the message.
        vm.onDay(today)
        assertNull(vm.state.value.refusal)
    }

    @Test
    fun `undo takes back a passage and a tally`() = runTest {
        val vm = open()
        vm.onToAyahText("7")
        vm.onSubmit()
        val passage = checkNotNull(vm.state.value.result)
        vm.onResultHandled()
        assertNull(vm.state.value.result)
        assertEquals(7, app.totals.today())
        vm.onUndo(passage)
        assertEquals(0, app.totals.today())

        vm.onMode(LogMode.NUMBER)
        vm.onNumberText("12")
        vm.onSubmit()
        val tally = checkNotNull(vm.state.value.result)
        assertNotNull(tally)
        assertEquals(12, app.totals.today())
        vm.onUndo(tally)
        assertEquals(0, app.totals.today())
    }

    @Test
    fun `adding enough to a day reaches its rank`() = runTest {
        val vm = open()
        vm.onMode(LogMode.SURAH)
        vm.onSurah(2)
        vm.onSubmit()
        assertEquals(286, app.totals.today())
        assertEquals(Rank.KHASHI, RankCalculator.rankFor(app.totals.today()))
    }
}
