package io.github.mehrdad_abdi.qintar.feature.khatm

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.khatmDetailViewModel
import io.github.mehrdad_abdi.qintar.testing.khatmListViewModel
import io.github.mehrdad_abdi.qintar.testing.newKhatmViewModel
import io.github.mehrdad_abdi.qintar.ui.common.GoToProblem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The khatm screens' use cases through their view models over the in-memory domain. */
class KhatmViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()

    private suspend fun read(khatmId: Long, range: AyahRange) =
        app.tracker.markRange(range, ReadContext.Khatm(khatmId), ReadMethod.BULK, RangeMode.FILL)

    // ---- New khatm -------------------------------------------------------------------------------

    @Test
    fun `the form proposes the next ordinal and starts a khatm with just a name`() = runTest {
        val form = app.newKhatmViewModel(main.dispatcher)
        assertEquals(1, form.state.value.ordinal)
        form.onDefaultName("Khatm 1")
        assertEquals("Khatm 1", form.state.value.name)

        form.onStart()

        val khatm = app.khatmStore.khatms().single()
        assertEquals("Khatm 1", khatm.name)
        assertNull("no goal unless asked for", khatm.dailyPages)
        assertEquals(0, khatm.assumedCoveredUpTo)
        assertEquals(khatm.id, form.state.value.createdId)

        assertEquals("the next form proposes 2", 2, app.newKhatmViewModel(main.dispatcher).state.value.ordinal)
    }

    @Test
    fun `a name the user typed is never replaced by the default`() = runTest {
        val form = app.newKhatmViewModel(main.dispatcher)
        form.onName("Ramadan")
        form.onDefaultName("Khatm 1")
        assertEquals("Ramadan", form.state.value.name)
    }

    @Test
    fun `a start position and a daily goal are optional and are kept`() = runTest {
        val form = app.newKhatmViewModel(main.dispatcher)
        form.onName("Mid-way")
        form.onStartText("2:255")
        form.onDailyText("2.5")
        assertEquals(app.ayah(2, 255), form.state.value.startFrom)
        assertEquals(2.5f, form.state.value.dailyPages)
        assertNotNull("projected finish is shown live", form.state.value.projectedFinish)

        form.onStart()

        val khatm = app.khatmStore.khatms().single()
        assertEquals(app.ayah(2, 255).value - 1, khatm.assumedCoveredUpTo)
        assertEquals(2.5f, khatm.dailyPages)
    }

    @Test
    fun `the start position takes pages and juz, and Persian digits, and says why it cannot`() = runTest {
        val form = app.newKhatmViewModel(main.dispatcher)
        form.onStartText("p300")
        assertEquals(app.index.pageRange(300).start, form.state.value.startFrom)
        form.onStartText("j۱۲")
        assertEquals(app.index.juzRange(12).start, form.state.value.startFrom)

        form.onStartText("255")
        assertEquals(GoToProblem.SURAH_OUT_OF_RANGE, form.state.value.startProblem)
        assertNull(form.state.value.startFrom)
        form.onStartText("p999")
        assertEquals(GoToProblem.PAGE_OUT_OF_RANGE, form.state.value.startProblem)

        form.onName("x")
        form.onStart()
        assertTrue("a bad position does not start a khatm", app.khatmStore.khatms().isEmpty())

        form.onStartText("")
        assertNull(form.state.value.startProblem)
        assertNull("empty = from the beginning", form.state.value.startFrom)
    }

    @Test
    fun `the daily goal is a guide that must be a number above zero`() = runTest {
        assertEquals(4f, NewKhatmViewModel.parsePages("4"))
        assertEquals(2.5f, NewKhatmViewModel.parsePages("2,5"))
        assertEquals("Persian digits and decimal point", 2.5f, NewKhatmViewModel.parsePages("۲٫۵"))
        assertEquals(10f, NewKhatmViewModel.parsePages("١٠"))
        assertNull(NewKhatmViewModel.parsePages("0"))
        assertNull(NewKhatmViewModel.parsePages("-1"))
        assertNull(NewKhatmViewModel.parsePages("abc"))

        val form = app.newKhatmViewModel(main.dispatcher)
        form.onName("x")
        form.onDailyText("0")
        assertTrue(form.state.value.dailyInvalid)
        form.onStart()
        assertTrue(app.khatmStore.khatms().isEmpty())
        form.onDailyText("")
        assertFalse(form.state.value.dailyInvalid)
        assertNull(form.state.value.projectedFinish)
    }

    @Test
    fun `the projected finish follows the goal and the start position`() = runTest {
        val form = app.newKhatmViewModel(main.dispatcher)
        form.onDailyText("604")
        assertEquals("a whole Quran at 604 pages a day is one day", app.clock.today().plusDays(1), form.state.value.projectedFinish)
        form.onDailyText("1")
        assertEquals(app.clock.today().plusDays(604), form.state.value.projectedFinish)
        form.onStartText("p301")
        assertEquals("half is already behind", app.clock.today().plusDays(304), form.state.value.projectedFinish)
    }

    @Test
    fun `a blank name is refused`() = runTest {
        val form = app.newKhatmViewModel(main.dispatcher)
        form.onName("  ")
        form.onStart()
        assertTrue(form.state.value.nameMissing)
        assertTrue(app.khatmStore.khatms().isEmpty())
        assertNull(form.state.value.createdId)
    }

    // ---- Several khatms --------------------------------------------------------------------------

    @Test
    fun `two khatms progress independently and are listed together`() = runTest {
        val list = app.khatmListViewModel(main.dispatcher)
        val personal = app.khatms.start("Personal")
        val ramadan = app.khatms.start("Ramadan", startFrom = app.ayah(8, 1), dailyPages = 4f)
        assertEquals(setOf("Personal", "Ramadan"), list.state.value.active.map { it.name }.toSet())

        read(personal.id, AyahRange.of(1, 100))
        app.clock.advanceMinutes(5)
        read(ramadan.id, AyahRange(app.ayah(8, 1), app.ayah(8, 20)))

        val items = list.state.value.active.associateBy { it.name }
        assertEquals(100, items.getValue("Personal").whole.covered)
        assertEquals(app.ayah(8, 1).value - 1 + 20, items.getValue("Ramadan").whole.covered)
        assertEquals("the one read last is first", "Ramadan", list.state.value.active.first().name)
        assertEquals(4f, items.getValue("Ramadan").dailyPages)
        assertNull(items.getValue("Personal").dailyPages)

        app.clock.advanceMinutes(5)
        read(personal.id, AyahRange.of(101, 150))
        val after = list.state.value.active.associateBy { it.name }
        assertEquals(150, after.getValue("Personal").whole.covered)
        assertEquals("Ramadan did not move", items.getValue("Ramadan").whole.covered, after.getValue("Ramadan").whole.covered)
        assertEquals("Personal", list.state.value.active.first().name)
    }

    @Test
    fun `a finished khatm moves to the completed section`() = runTest {
        val list = app.khatmListViewModel(main.dispatcher)
        val done = app.khatms.start("Done")
        app.khatms.start("Going")
        read(done.id, AyahRange.ALL)

        assertEquals(listOf("Going"), list.state.value.active.map { it.name })
        val completed = list.state.value.completed.single()
        assertEquals("Done", completed.name)
        assertEquals(app.clock.today(), completed.completedDay)
        assertEquals(1, completed.daysTaken)
    }

    // ---- Detail ----------------------------------------------------------------------------------

    @Test
    fun `the daily goal bar exists only with a goal`() = runTest {
        val plain = app.khatms.start("Plain")
        val goal = app.khatms.start("Goal", dailyPages = 4f)
        val withoutGoal = app.khatmDetailViewModel(plain.id, main.dispatcher)
        val withGoal = app.khatmDetailViewModel(goal.id, main.dispatcher)

        assertFalse(withoutGoal.state.value.showTodayBar)
        assertTrue(withGoal.state.value.showTodayBar)
        assertEquals(0f, withGoal.state.value.snapshot!!.todayGoalFraction)

        read(goal.id, AyahRange.of(1, 40))
        assertTrue("pages read today count toward the goal", withGoal.state.value.snapshot!!.todayGoalFraction!! > 0f)
        assertEquals(1, withGoal.state.value.recentDays.size)
    }

    @Test
    fun `the detail shows the position, the juz strip and where first unread is`() = runTest {
        val khatm = app.khatms.start("K")
        val detail = app.khatmDetailViewModel(khatm.id, main.dispatcher)
        assertEquals(1, detail.state.value.page)
        assertNull("nothing to jump to yet", detail.state.value.firstUnread)

        read(khatm.id, AyahRange.of(50, 60))

        val state = detail.state.value
        assertEquals(app.ayah(1, 1).value, state.snapshot!!.firstUnread!!.value)
        assertEquals(app.index.surahRange(1).start.value, state.firstUnread!!.value)
        assertEquals(app.index.pageOf(state.resume!!), state.page)
        assertEquals(30, state.snapshot!!.juzCoverage.size)
        assertTrue(state.snapshot!!.juzCoverage.any { it > 0f })
        assertTrue(state.snapshot!!.remainingPages > 0)
    }

    @Test
    fun `rename and the daily goal change the khatm, and a goal can be removed`() = runTest {
        val khatm = app.khatms.start("Old")
        val detail = app.khatmDetailViewModel(khatm.id, main.dispatcher)

        detail.onDialog(KhatmDetailDialog.RENAME)
        detail.onRename("  New name ")
        assertEquals("New name", detail.state.value.name)
        assertNull("the dialog closes", detail.state.value.dialog)

        detail.onRename("   ")
        assertEquals("a blank name is refused", "New name", detail.state.value.name)

        detail.onSetDailyPages("3")
        assertEquals(3f, detail.state.value.dailyPages)
        assertTrue(detail.state.value.showTodayBar)

        detail.onSetDailyPages("nonsense")
        assertTrue(detail.state.value.goalInvalid)
        assertEquals(3f, detail.state.value.dailyPages)

        detail.onSetDailyPages("")
        assertNull(detail.state.value.dailyPages)
        assertFalse(detail.state.value.showTodayBar)
    }

    @Test
    fun `deleting a khatm removes it and keeps the readings`() = runTest {
        val khatm = app.khatms.start("Bye")
        read(khatm.id, AyahRange.of(1, 30))
        val detail = app.khatmDetailViewModel(khatm.id, main.dispatcher)
        val list = app.khatmListViewModel(main.dispatcher)
        assertEquals(KhatmDetailPhase.READY, detail.state.value.phase)

        detail.onDialog(KhatmDetailDialog.DELETE)
        assertEquals(KhatmDetailDialog.DELETE, detail.state.value.dialog)
        detail.onDelete()

        assertEquals(KhatmDetailPhase.NOT_FOUND, detail.state.value.phase)
        assertTrue(list.state.value.active.isEmpty())
        assertEquals("history and day totals stay", 30, app.totals.today())
    }

    @Test
    fun `reading the whole Quran completes the khatm and gives it a summary`() = runTest {
        val khatm = app.khatms.start("Whole", startFrom = app.ayah(2, 1))
        val detail = app.khatmDetailViewModel(khatm.id, main.dispatcher)
        assertFalse(detail.state.value.isComplete)

        read(khatm.id, AyahRange(app.ayah(2, 1), AyahId.LAST))
        assertTrue("the Fatiha is assumed read, the rest was just read", detail.state.value.isComplete)

        val completion = checkNotNull(detail.state.value.completion)
        assertEquals(app.clock.today(), completion.startedDay)
        assertEquals(1, completion.daysTaken)
        assertTrue(completion.averageAyahsPerDay > 6000)
        assertFalse("no today bar once complete", detail.state.value.showTodayBar)
        assertNull(detail.state.value.firstUnread)
    }
}
