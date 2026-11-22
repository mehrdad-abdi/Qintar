package io.github.mehrdad_abdi.qintar.feature.bookmarks

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.bookmarkEditorViewModel
import io.github.mehrdad_abdi.qintar.testing.groupEditorViewModel
import io.github.mehrdad_abdi.qintar.ui.common.ScheduleForm
import io.github.mehrdad_abdi.qintar.ui.common.ScheduleProblem
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.GroupEditorRoute
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The bookmark editor and the group editor through their view models over the in-memory domain. */
class EditorViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()

    private fun newEditor(route: BookmarkEditorRoute = BookmarkEditorRoute()) = app.bookmarkEditorViewModel(route, main.dispatcher)

    private suspend fun saved(vm: BookmarkEditorViewModel): Bookmark {
        vm.onSave()
        val id = checkNotNull(vm.state.value.savedId) { "not saved: ${vm.state.value.errors}" }
        return checkNotNull(app.bookmarkStore.bookmark(id))
    }

    // ---- kinds ---------------------------------------------------------------------------------------

    @Test
    fun `an ayah is saved with its range and kind`() = runTest {
        val vm = newEditor()
        vm.onSurah(2)
        vm.onAyahText("255")
        val b = saved(vm)
        assertEquals(BookmarkKind.AYAH, b.kind)
        assertEquals(AyahRange.single(app.ayah(2, 255)), b.range)
    }

    @Test
    fun `a range is one surah and two ayahs`() = runTest {
        val within = newEditor()
        within.onKind(BookmarkKind.RANGE)
        within.onSurah(18)
        within.onAyahText("1")
        within.onToAyahText("10")
        assertEquals("choosing the surah sets both ends", 18, within.state.value.toSurah)
        assertEquals(AyahRange(app.ayah(18, 1), app.ayah(18, 10)), saved(within).range)
        assertTrue("Juz is not offered", BookmarkKind.JUZ !in within.state.value.kinds)
    }

    @Test
    fun `the preview ends with the last ayah of a long range`() = runTest {
        val vm = newEditor()
        vm.onKind(BookmarkKind.RANGE)
        vm.onSurah(2)
        vm.onAyahText("1")
        vm.onToAyahText("100")
        val preview = vm.state.value.preview
        assertEquals(BookmarkEditorViewModel.PREVIEW_AYAHS, preview.ayahs.size)
        assertEquals(app.ayah(2, 100), preview.last?.id)
        assertEquals("between the first ones and the last", 100 - BookmarkEditorViewModel.PREVIEW_AYAHS - 1, preview.more)
    }

    @Test
    fun `a surah, a page and a juz are saved whole`() = runTest {
        val surah = newEditor()
        surah.onKind(BookmarkKind.SURAH)
        surah.onSurah(112)
        assertEquals(app.index.surahRange(112), saved(surah).range)

        val page = newEditor()
        page.onKind(BookmarkKind.PAGE)
        page.onPageText("293")
        val p = saved(page)
        assertEquals(app.index.pageRange(293), p.range)
        assertEquals(BookmarkKind.PAGE, p.kind)

        val juz = newEditor()
        juz.onKind(BookmarkKind.JUZ)
        juz.onJuzText("30")
        assertEquals(app.index.juzRange(30), saved(juz).range)
    }

    @Test
    fun `opened from an ayah the editor is pre-filled`() = runTest {
        val vm = newEditor(BookmarkEditorRoute.forAyah(app.ayah(2, 255).value))
        val state = vm.state.value
        assertEquals(BookmarkKind.AYAH, state.kind)
        assertEquals(2, state.surah)
        assertEquals("255", state.ayahText)
        assertEquals(AyahRange.single(app.ayah(2, 255)), state.range)

        val range = newEditor(BookmarkEditorRoute(draftFirstAyahId = app.ayah(18, 1).value, draftLastAyahId = app.ayah(18, 10).value))
        assertEquals(BookmarkKind.RANGE, range.state.value.kind)
        assertEquals("10", range.state.value.toAyahText)

        val group = app.library.createGroup("Morning")
        assertEquals(group.id, newEditor(BookmarkEditorRoute(draftGroupId = group.id)).state.value.groupId)
    }

    // ---- preview -------------------------------------------------------------------------------------

    @Test
    fun `the preview shows the actual ayahs formatted as in the reader`() = runTest {
        val vm = newEditor()
        vm.onKind(BookmarkKind.RANGE)
        vm.onSurah(18)
        vm.onAyahText("1")
        vm.onToSurah(18)
        vm.onToAyahText("3")
        val preview = vm.state.value.preview
        assertEquals(listOf(1, 2, 3), preview.ayahs.map { it.number })
        assertEquals(app.ayah(18, 1), preview.ayahs.first().id)
        assertEquals(AyahTextFormatter.format("ayah 18:2", 2, true), preview.ayahs[1].text)
        assertTrue(preview.ayahs[1].text.endsWith(AyahTextFormatter.ayahEndMarker(2)))
        assertEquals(0, preview.more)
        assertEquals(3, vm.state.value.summary?.ayahCount)
    }

    @Test
    fun `a long passage previews only its first ayahs and counts the rest`() = runTest {
        val vm = newEditor()
        vm.onKind(BookmarkKind.SURAH)
        vm.onSurah(2)
        val preview = vm.state.value.preview
        assertEquals(BookmarkEditorViewModel.PREVIEW_AYAHS, preview.ayahs.size)
        assertEquals(app.ayah(2, 1), preview.ayahs.first().id)
        assertEquals(286 - BookmarkEditorViewModel.PREVIEW_AYAHS - 1, preview.more)
        assertEquals(app.ayah(2, 286), preview.last?.id)
    }

    // ---- validation ----------------------------------------------------------------------------------

    @Test
    fun `position errors appear on the right field and block saving`() = runTest {
        val vm = newEditor()
        vm.onSurah(1)
        vm.onAyahText("8")
        assertEquals(EditorProblem.AYAH_OUT_OF_RANGE, vm.state.value.errors[EditorField.FROM_AYAH])
        assertNull(vm.state.value.range)
        assertTrue(vm.state.value.preview.ayahs.isEmpty())
        vm.onSave()
        assertNull(vm.state.value.savedId)
        assertTrue(app.bookmarkStore.bookmarks().isEmpty())

        vm.onAyahText("")
        assertEquals(EditorProblem.NUMBER_MISSING, vm.state.value.errors[EditorField.FROM_AYAH])

        vm.onKind(BookmarkKind.RANGE)
        vm.onSurah(2)
        vm.onAyahText("10")
        vm.onToSurah(2)
        vm.onToAyahText("5")
        assertEquals(EditorProblem.END_BEFORE_START, vm.state.value.errors[EditorField.TO_AYAH])

        vm.onKind(BookmarkKind.PAGE)
        vm.onPageText("605")
        assertEquals(EditorProblem.PAGE_OUT_OF_RANGE, vm.state.value.errors[EditorField.PAGE])
        vm.onKind(BookmarkKind.JUZ)
        vm.onJuzText("31")
        assertEquals(EditorProblem.JUZ_OUT_OF_RANGE, vm.state.value.errors[EditorField.JUZ])
        vm.onJuzText("5")
        assertNull(vm.state.value.errors[EditorField.JUZ])
    }

    @Test
    fun `description, repeat target and schedule errors are shown and block saving`() = runTest {
        val vm = newEditor()
        vm.onDescription("x".repeat(Bookmark.MAX_DESCRIPTION + 1))
        assertEquals(EditorProblem.DESCRIPTION_TOO_LONG, vm.state.value.errors[EditorField.DESCRIPTION])
        vm.onRepeatTarget(0)
        assertEquals(EditorProblem.REPEAT_INVALID, vm.state.value.errors[EditorField.REPEAT])
        vm.onSchedule(ScheduleForm(enabled = true, days = emptySet()))
        assertEquals(EditorProblem.SCHEDULE_INVALID, vm.state.value.errors[EditorField.SCHEDULE])
        assertEquals(ScheduleProblem.NO_DAYS, vm.state.value.schedule.problem)
        vm.onSave()
        assertNull(vm.state.value.savedId)

        vm.onSchedule(ScheduleForm(enabled = true, windowOn = true, from = LocalTime.NOON, to = LocalTime.NOON))
        assertEquals(ScheduleProblem.SAME_TIMES, vm.state.value.schedule.problem)

        vm.onDescription("a narration")
        vm.onRepeatTarget(3)
        vm.onSchedule(ScheduleForm())
        assertTrue(vm.state.value.errors.isEmpty())
        assertEquals(3, saved(vm).repeatTarget)
    }

    @Test
    fun `a failure from the domain lands on its field`() = runTest {
        val vm = newEditor()
        // The domain's own limit is checked even if the form's pre-check were bypassed.
        vm.onDescription("y".repeat(Bookmark.MAX_DESCRIPTION))
        vm.onSave()
        assertNotNull(vm.state.value.savedId)
        assertEquals(Bookmark.MAX_DESCRIPTION, app.bookmarkStore.bookmarks().single().description?.length)
    }

    // ---- the rest of the form ------------------------------------------------------------------------

    @Test
    fun `label, description, group and schedule are saved`() = runTest {
        val group = app.library.createGroup("Morning", schedule = Schedule.daily())
        val vm = newEditor()
        vm.onSurah(2)
        vm.onAyahText("255")
        vm.onLabel("  Ayat al-Kursi ")
        vm.onDescription("Whoever reads it after every prayer…")
        vm.onGroup(group.id)
        assertEquals(group.schedule, vm.state.value.inheritedSchedule)
        vm.onSchedule(ScheduleForm(enabled = true, days = setOf(DayOfWeek.FRIDAY), windowOn = true))
        val b = saved(vm)
        assertEquals("Ayat al-Kursi", b.label)
        assertEquals("Whoever reads it after every prayer…", b.description)
        assertEquals(group.id, b.groupId)
        assertEquals(Schedule(setOf(DayOfWeek.FRIDAY), TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0))), b.schedule)
    }

    @Test
    fun `no own schedule saves null so the group's applies, and once is kept`() = runTest {
        val vm = newEditor()
        assertNull(saved(vm).schedule)

        val once = newEditor()
        once.onSchedule(ScheduleForm(enabled = true, once = true))
        assertEquals(Schedule.once(), saved(once).schedule)
    }

    @Test
    fun `editing an existing bookmark loads it and saves changes in place`() = runTest {
        val group = app.library.createGroup("G")
        val b = app.library.create(
            BookmarkDrafts.surah(18, app.index).copy(
                label = "Friday", description = "d", repeatTarget = 2, groupId = group.id,
                schedule = Schedule(setOf(DayOfWeek.FRIDAY), null),
            ),
        )
        val vm = newEditor(BookmarkEditorRoute(bookmarkId = b.id))
        val s = vm.state.value
        assertFalse(s.isNew)
        assertEquals(BookmarkKind.SURAH, s.kind)
        assertEquals(18, s.surah)
        assertEquals("Friday", s.label)
        assertEquals(2, s.repeatTarget)
        assertEquals(group.id, s.groupId)
        assertEquals(setOf(DayOfWeek.FRIDAY), s.schedule.days)

        vm.onLabel("Al-Kahf")
        vm.onKind(BookmarkKind.AYAH)
        vm.onAyahText("10")
        val updated = saved(vm)
        assertEquals(b.id, updated.id)
        assertEquals(b.sortIndex, updated.sortIndex)
        assertEquals("Al-Kahf", updated.label)
        assertEquals(AyahRange.single(app.ayah(18, 10)), updated.range)
        assertEquals(1, app.bookmarkStore.bookmarks().size)
    }

    @Test
    fun `an unknown bookmark id says so`() = runTest {
        assertEquals(EditorPhase.NOT_FOUND, newEditor(BookmarkEditorRoute(bookmarkId = 99)).state.value.phase)
    }

    @Test
    fun `weekdays follow the week-start setting`() = runTest {
        app.prefs.update { it.copy(weekStart = io.github.mehrdad_abdi.qintar.domain.model.WeekStart.SUNDAY) }
        assertEquals(DayOfWeek.SUNDAY, newEditor().state.value.weekDays.first())
        app.prefs.update { it.copy(weekStart = io.github.mehrdad_abdi.qintar.domain.model.WeekStart.SYSTEM) }
        assertEquals(DayOfWeek.SATURDAY, newEditor().state.value.weekDays.first())
    }

    // ---- group editor --------------------------------------------------------------------------------

    @Test
    fun `a group is created with name, description and schedule`() = runTest {
        val vm = app.groupEditorViewModel(GroupEditorRoute(), main.dispatcher)
        vm.onSave()
        assertEquals(GroupProblem.NAME_MISSING, vm.state.value.problem(GroupEditorUiState.NAME))
        assertNull(vm.state.value.savedId)

        vm.onName("Morning")
        vm.onDescription("After fajr")
        vm.onSchedule(ScheduleForm(enabled = true, once = true, windowOn = true))
        vm.onSave()
        val group = app.bookmarkStore.groups().single()
        assertEquals("Morning", group.name)
        assertEquals("After fajr", group.description)
        assertEquals(false, group.schedule?.once)
        assertEquals(TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0)), group.schedule?.window)
        assertEquals(group.id, vm.state.value.savedId)
    }

    @Test
    fun `members can be reordered and removed, applied on save`() = runTest {
        val group = app.library.createGroup("G")
        val a = app.library.create(BookmarkDrafts.ayah(app.ayah(1, 1)).copy(groupId = group.id))
        val outside = app.library.create(BookmarkDrafts.ayah(app.ayah(1, 2)))
        val b = app.library.create(BookmarkDrafts.ayah(app.ayah(1, 3)).copy(groupId = group.id))
        val c = app.library.create(BookmarkDrafts.ayah(app.ayah(1, 4)).copy(groupId = group.id))

        val vm = app.groupEditorViewModel(GroupEditorRoute(group.id), main.dispatcher)
        assertEquals(listOf(a.id, b.id, c.id), vm.state.value.members.map { it.id })

        vm.onMoveDown(0)
        vm.onMoveUp(2)
        assertEquals(listOf(b.id, c.id, a.id), vm.state.value.members.map { it.id })
        vm.onMoveUp(0) // no effect at the edge
        assertEquals(b.id, vm.state.value.members.first().id)
        assertEquals("nothing is written before Save", group.id, app.bookmarkStore.bookmark(a.id)?.groupId)

        vm.onRemove(c.id)
        vm.onSave()
        assertNotNull(vm.state.value.savedId)
        assertNull("removed from the group, not deleted", app.bookmarkStore.bookmark(c.id)?.groupId)
        assertNotNull(app.bookmarkStore.bookmark(c.id))
        val order = io.github.mehrdad_abdi.qintar.domain.service.Availability.sorted(
            app.bookmarkStore.bookmarks().filter { it.groupId == group.id },
            io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.CUSTOM,
        )
        assertEquals(listOf(b.id, a.id), order.map { it.id })
        val everyone = io.github.mehrdad_abdi.qintar.domain.service.Availability.sorted(
            app.bookmarkStore.bookmarks(),
            io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.CUSTOM,
        ).map { it.id }
        assertTrue("a bookmark outside the group keeps its place relative to the others", everyone.indexOf(outside.id) in 0..3)
    }

    @Test
    fun `an unknown group id says so`() = runTest {
        assertEquals(EditorPhase.NOT_FOUND, app.groupEditorViewModel(GroupEditorRoute(77), main.dispatcher).state.value.phase)
    }
}
