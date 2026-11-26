package io.github.mehrdad_abdi.qintar.feature.reader

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.countOf
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.ui.common.GoToProblem
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The reader's second round of use cases: opening on a surah header, the jump sheet, "Mark page",
 * "First unread", the bookmark header and "Log a full reading", and the Aa panel.
 */
class ReaderGapsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()

    private fun reader(route: ReaderRoute) = app.readerViewModel(route, main.dispatcher)

    private fun ReaderViewModel.dividerBefore(page: Int): Int =
        state.value.rows.indexOfFirst { it is ReaderRow.PageDivider && it.page == page }

    // ---- Opening on a surah's first ayah -----------------------------------------------------------

    @Test
    fun `opening at the first ayah of a surah shows its header and basmala above it`() = runTest {
        val reader = reader(ReaderRoute.browse(app.ayah(8, 1).value))
        val state = reader.state.value
        val shown = state.rows.drop(state.initialRowIndex)

        val above = shown.takeWhile { it !is ReaderRow.Ayah }
        assertTrue("the surah header is on screen: $above", above.any { it is ReaderRow.SurahHeader && it.surah.number == 8 })
        assertTrue("and its basmala", above.any { it is ReaderRow.Basmala })
        assertEquals(app.ayah(8, 1), (shown[above.size] as ReaderRow.Ayah).id)
    }

    @Test
    fun `opening inside a surah starts at the ayah itself`() = runTest {
        val reader = reader(ReaderRoute.browse(app.ayah(8, 5).value))
        val state = reader.state.value
        val first = state.rows[state.initialRowIndex]
        assertTrue(first is ReaderRow.Ayah && first.id == app.ayah(8, 5) || first is ReaderRow.PageDivider)
    }

    @Test
    fun `a khatm resuming at the start of a surah also shows the header`() = runTest {
        val khatm = app.khatms.start("K", startFrom = app.ayah(8, 1))
        val state = reader(ReaderRoute.khatm(khatm.id)).state.value
        assertTrue(state.rows[state.initialRowIndex].let { it is ReaderRow.SurahHeader || it is ReaderRow.PageDivider })
    }

    // ---- Jump ------------------------------------------------------------------------------------

    @Test
    fun `the jump sheet takes a surah and ayah, a page, and a juz, and asks the list to scroll there`() = runTest {
        val reader = reader(ReaderRoute.browse(app.ayah(1, 1).value))
        reader.onSheet(ReaderSheet.JUMP)
        assertEquals(ReaderSheet.JUMP, reader.state.value.sheet)
        val model = reader.state.value.model!!

        reader.onJumpText("2:255")
        reader.onJumpSubmit()
        val request = checkNotNull(reader.state.value.scrollRequest)
        assertEquals(model.indexOf(0, app.ayah(2, 255)), request.row)
        assertNull("the sheet closes", reader.state.value.sheet)

        reader.onScrollHandled(request)
        assertNull(reader.state.value.scrollRequest)

        reader.onSheet(ReaderSheet.JUMP)
        reader.onJumpText("p۱۲۳")
        reader.onJumpSubmit()
        val page = (model.rows[reader.state.value.scrollRequest!!.row].let { r ->
            model.ayahAtOrAfter(reader.state.value.scrollRequest!!.row)!!.page
        })
        assertTrue("lands at the start of page 123 (or its divider)", page == 123)

        reader.onSheet(ReaderSheet.JUMP)
        reader.onJumpText("j12")
        reader.onJumpSubmit()
        assertEquals(12, model.ayahAtOrAfter(reader.state.value.scrollRequest!!.row)!!.juz)
    }

    @Test
    fun `a bare number in the jump sheet is a surah, as everywhere in Go to`() = runTest {
        // Owner, 2026-11-25: a bare number is a surah; "kahf 10" or 18:10 is an ayah.
        val reader = reader(ReaderRoute.browse(app.ayah(18, 1).value))
        reader.onJumpText("20")
        reader.onJumpSubmit()
        val row = reader.state.value.scrollRequest!!.row
        assertEquals(app.ayah(20, 1), reader.state.value.model!!.ayahAtOrAfter(row)!!.id)
    }

    @Test
    fun `a place outside the reader's scope is refused with a reason, and so is nonsense`() = runTest {
        val reader = reader(ReaderRoute.random(app.ayah(2, 255).value, app.ayah(2, 255).value))
        reader.onJumpText("2:1")
        reader.onJumpSubmit()
        assertEquals(GoToProblem.OUTSIDE_SCOPE, reader.state.value.jumpProblem)
        assertNull(reader.state.value.scrollRequest)

        reader.onJumpText("2:999")
        reader.onJumpSubmit()
        assertEquals(GoToProblem.AYAH_OUT_OF_RANGE, reader.state.value.jumpProblem)
        reader.onJumpText("x")
        reader.onJumpSubmit()
        assertEquals(GoToProblem.UNRECOGNIZED, reader.state.value.jumpProblem)
        reader.onJumpText("")
        assertNull("typing clears it", reader.state.value.jumpProblem)
    }

    @Test
    fun `the sheet offers only the surahs and juz the scope touches`() = runTest {
        val page = app.index.pageRange(300)
        val reader = reader(ReaderRoute.random(page.start.value, page.end.value))
        val surahs = reader.state.value.scopeSurahs.map { it.number }
        assertEquals((app.index.ref(page.start).surah..app.index.ref(page.end).surah).toList(), surahs)
        assertEquals(listOf(app.index.juzOf(page.start)), reader.state.value.scopeJuz)

        reader.onJumpToAyah(app.ayah(1, 1))
        assertNull("not in scope: nothing happens", reader.state.value.scrollRequest)

        val whole = reader(ReaderRoute.browse(app.ayah(1, 1).value))
        assertEquals(114, whole.state.value.scopeSurahs.size)
        assertEquals(30, whole.state.value.scopeJuz.size)
        whole.onJumpToSurah(36)
        assertEquals(app.ayah(36, 1), whole.state.value.model!!.ayahAtOrAfter(whole.state.value.scrollRequest!!.row)!!.id)
        whole.onJumpToJuz(30)
        assertEquals(30, whole.state.value.model!!.ayahAtOrAfter(whole.state.value.scrollRequest!!.row)!!.juz)
    }

    // ---- Mark page -------------------------------------------------------------------------------

    @Test
    fun `mark page reads the unread ayahs of the page above a divider, once, and Undo takes them back`() = runTest {
        val reader = reader(ReaderRoute.browse(app.ayah(2, 1).value))
        val divider = reader.state.value.model!!.rows.first { it is ReaderRow.PageDivider } as ReaderRow.PageDivider
        val page = divider.page - 1
        val range = app.index.pageRange(page)
        assertTrue(reader.state.value.pageAboveHasUnread(reader.dividerBefore(divider.page)))

        reader.onMarkPage(page, 0)

        val onPage = app.log.events.filter { it.ayahId in range }
        assertEquals(range.size, onPage.size)
        assertTrue(app.log.events.all { it.method == ReadMethod.BULK && it.context == ReadContext.Browse })
        assertEquals(range.size, reader.state.value.bulk?.added)
        assertFalse("nothing unread above the divider now", reader.state.value.pageAboveHasUnread(reader.dividerBefore(divider.page)))
        assertEquals(1, reader.countOf(range.start))

        reader.onMarkPage(page, 0)
        assertEquals("marking again adds nothing", range.size, app.log.events.size)

        reader.onUndoBulk()
        assertTrue(app.log.events.isEmpty())
    }

    @Test
    fun `mark page in a khatm counts under the khatm, and only inside the scope in a bookmark`() = runTest {
        val khatm = app.khatms.start("K")
        val inKhatm = reader(ReaderRoute.khatm(khatm.id, startAyahId = app.ayah(1, 1).value))
        val divider = inKhatm.state.value.model!!.rows.first { it is ReaderRow.PageDivider } as ReaderRow.PageDivider
        inKhatm.onMarkPage(divider.page - 1, 0)
        assertTrue(app.log.events.isNotEmpty() && app.log.events.all { it.context == ReadContext.Khatm(khatm.id) })

        val earlier = app.log.events.size
        val page = app.index.pageRange(divider.page)
        // A bookmark that begins in the middle of a page: "mark page" stops at its edge.
        val from = AyahId(page.start.value + 3)
        val bookmark = app.library.create(BookmarkDraft(AyahRange(from, AyahId(page.end.value + 2)), BookmarkKind.RANGE))
        val inBookmark = reader(ReaderRoute.bookmark(bookmark.id))
        inBookmark.onMarkPage(divider.page, 0)
        val marked = app.log.events.drop(earlier)
        assertEquals(page.end.value - from.value + 1, marked.size)
        assertTrue(marked.all { it.ayahId >= from && it.ayahId <= page.end && it.context == ReadContext.Bookmark(bookmark.id) })
    }

    // ---- Mark read at the end of a bookmark, Mark surah (owner, 2026-11-22) ----------------------

    @Test
    fun `a bookmark ends with one Mark read and offers no Mark page`() = runTest {
        val page = app.index.pageRange(3)
        val bookmark = app.library.create(BookmarkDraft(AyahRange(page.start, AyahId(page.end.value + 4)), BookmarkKind.RANGE))
        val reader = reader(ReaderRoute.bookmark(bookmark.id))
        val rows = reader.state.value.rows
        assertEquals(ReaderRow.SegmentEnd(0), rows.last())
        assertTrue("no surah end inside a bookmark", rows.none { it is ReaderRow.SurahEnd })
        val divider = rows.indexOfFirst { it is ReaderRow.PageDivider }
        assertFalse("Mark page is not offered in a bookmark", reader.state.value.pageMarkOffered(divider))
        assertTrue(reader.state.value.segmentHasUnread(0))

        reader.onMarkSegment(0)
        assertEquals(bookmark.range.size, app.log.events.size)
        assertTrue(app.log.events.all { it.context == ReadContext.Bookmark(bookmark.id) })
        assertFalse(reader.state.value.segmentHasUnread(0))
        reader.onUndoBulk()
        assertTrue(app.log.events.isEmpty())
    }

    @Test
    fun `free reading and a khatm have Mark surah at the end of each surah, besides Mark page`() = runTest {
        val reader = reader(ReaderRoute.browse(app.ayah(1, 1).value))
        val rows = reader.state.value.rows
        val end = rows.indexOfFirst { it is ReaderRow.SurahEnd && it.surah == 1 }
        assertEquals("right after the last ayah of al-Fatiha", app.ayah(1, 7), (rows[end - 1] as ReaderRow.Ayah).id)
        val divider = rows.indexOfFirst { it is ReaderRow.PageDivider }
        assertTrue(reader.state.value.pageMarkOffered(divider))
        assertTrue(reader.state.value.surahHasUnread(end))

        reader.onMarkSurah(1, 0)
        assertEquals(7, app.log.events.size)
        assertTrue(app.log.events.all { it.context == ReadContext.Browse && it.ayahId in app.index.surahRange(1) })
        assertFalse(reader.state.value.surahHasUnread(end))

        val khatm = app.khatms.start("K")
        val inKhatm = reader(ReaderRoute.khatm(khatm.id, startAyahId = app.ayah(1, 1).value))
        assertTrue(inKhatm.state.value.rows.any { it is ReaderRow.SurahEnd && it.surah == 114 })
    }

    // ---- First unread ----------------------------------------------------------------------------

    @Test
    fun `a khatm reader offers first unread only when it is elsewhere than the resume position`() = runTest {
        val khatm = app.khatms.start("K")
        val context = ReadContext.Khatm(khatm.id)
        val reader = reader(ReaderRoute.khatm(khatm.id))
        assertNull(reader.state.value.firstUnread)

        app.tracker.markRange(AyahRange.of(1, 5), context, ReadMethod.BULK, RangeMode.FILL)
        assertNull("resume is right after the last read ayah: same place", reader.state.value.firstUnread)

        app.tracker.mark(AyahId(50), context, ReadMethod.TAP)
        assertEquals(AyahId(6), reader.state.value.firstUnread)
        assertEquals(AyahId(51), reader.state.value.resume)

        reader.onJumpToAyah(AyahId(6))
        val request = checkNotNull(reader.state.value.scrollRequest)
        assertEquals(AyahId(6), reader.state.value.model!!.ayahAtOrAfter(request.row)!!.id)

        app.tracker.markRange(AyahRange.of(6, 49), context, ReadMethod.BULK, RangeMode.FILL)
        assertEquals("the gap is filled: first unread moves past it", AyahId(51), reader.state.value.firstUnread)
    }

    @Test
    fun `other readers have no first unread chip`() = runTest {
        assertNull(reader(ReaderRoute.browse(1)).state.value.firstUnread)
    }

    // ---- Bookmark and group headers, log a full reading --------------------------------------------

    @Test
    fun `a bookmark reader has a header with its description and its progress against the target`() = runTest {
        val bookmark = app.library.create(
            BookmarkDrafts.surah(112, app.index).copy(label = "Ikhlas", description = "Narrated: it equals a third of the Quran", repeatTarget = 3),
        )
        val reader = reader(ReaderRoute.bookmark(bookmark.id))
        val state = reader.state.value

        assertTrue(state.rows.first() is ReaderRow.SegmentHeader)
        assertEquals(0, state.initialRowIndex)
        val header = checkNotNull(state.headers.single())
        assertEquals("Narrated: it equals a third of the Quran", header.description)
        assertEquals(3, header.repeatTarget)
        assertEquals(0, header.fullReadings)

        reader.onMarkSegment(0)
        assertEquals(1, reader.state.value.headers.single()!!.fullReadings)
        assertFalse(reader.state.value.headers.single()!!.done)
    }

    @Test
    fun `in a group each bookmark can be logged on its own, or the whole group at once with one Undo`() = runTest {
        val group = app.library.createGroup("Morning")
        val first = app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = group.id))
        val second = app.library.create(BookmarkDrafts.surah(113, app.index).copy(groupId = group.id))
        val reader = reader(ReaderRoute.group(group.id))
        assertEquals(2, reader.state.value.headers.size)

        reader.onMarkSegment(1)
        assertEquals(5, app.log.events.size)
        assertTrue(app.log.events.all { it.context == ReadContext.Bookmark(second.id) })

        reader.onLogGroupReading()
        assertEquals(5 + 4 + 5, app.log.events.size)
        assertEquals(2, app.log.events.count { it.ayahId == app.ayah(113, 1) })
        assertEquals(1, app.log.events.count { it.ayahId == app.ayah(112, 1) && it.context == ReadContext.Bookmark(first.id) })

        reader.onUndoBulk()
        assertEquals("one Undo removes the whole group reading", 5, app.log.events.size)
    }

    // ---- Aa panel --------------------------------------------------------------------------------

    @Test
    fun `the Aa panel changes the font size live and keeps it`() = runTest {
        val reader = reader(ReaderRoute.browse(1))
        assertEquals(UserPrefs.DEFAULT_FONT_SP, reader.state.value.fontSp)

        reader.onFontSize(36)
        assertEquals(36, reader.state.value.fontSp)
        assertEquals("persisted", 36, app.prefs.current.quranFontSp)

        reader.onFontSize(100)
        assertEquals(UserPrefs.MAX_FONT_SP, app.prefs.current.quranFontSp)
        reader.onFontSize(1)
        assertEquals(UserPrefs.MIN_FONT_SP, app.prefs.current.quranFontSp)

        assertEquals("a new reader starts at the stored size", UserPrefs.MIN_FONT_SP, reader(ReaderRoute.browse(1)).state.value.fontSp)
    }

    @Test
    fun `the Aa panel switches line breaks and keep screen on, and keeps them`() = runTest {
        val reader = reader(ReaderRoute.browse(1))
        assertTrue(reader.state.value.breakAtPauseMarks)
        assertTrue(reader.state.value.keepScreenOn)

        reader.onBreakAtPauseMarks(false)
        reader.onKeepScreenOn(false)

        assertFalse(reader.state.value.breakAtPauseMarks)
        assertFalse(reader.state.value.keepScreenOn)
        assertFalse(app.prefs.current.breakAtPauseMarks)
        assertFalse(app.prefs.current.keepScreenOn)
        assertNotNull(reader(ReaderRoute.browse(1)).state.value.model)
        assertFalse("a new reader keeps the choice", reader(ReaderRoute.browse(1)).state.value.breakAtPauseMarks)
    }

    private fun ReaderViewModel.row112(n: Int): ReaderRow.Ayah {
        val model = state.value.model!!
        return model.rows[model.indexOf(0, app.ayah(112, n))!!] as ReaderRow.Ayah
    }
}
