package io.github.mehrdad_abdi.qintar.feature.reader

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.countOf
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.testing.row
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahCheckState
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The reader's use cases, driven through its view model over the in-memory domain: what the user
 * does (tick, set a count, mark up to here, answer the khatm dialog) and what then stands in the
 * log and on the screen.
 */
class ReaderViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()

    private fun reader(route: ReaderRoute) = app.readerViewModel(route, main.dispatcher)
    private fun browse(surah: Int = 2, ayah: Int = 1) = reader(ReaderRoute.browse(app.ayah(surah, ayah).value))
    private suspend fun dayTotal() = app.totals.today()

    // ---- Tapping ---------------------------------------------------------------------------------

    @Test
    fun `a tap on the check adds one reading, every time`() = runTest {
        val reader = browse()
        val ayah = app.ayah(2, 3)
        assertEquals(AyahCheckState.Unread, reader.state.value.checkState(reader.row(ayah)))

        reader.onCheck(reader.row(ayah))
        assertEquals(1, reader.countOf(ayah))
        assertEquals(AyahCheckState.ReadToday, reader.state.value.checkState(reader.row(ayah)))

        reader.onCheck(reader.row(ayah))
        reader.onCheck(reader.row(ayah))

        assertEquals("×3 on the row", 3, reader.countOf(ayah))
        assertEquals("the pill follows every tap", 3, reader.state.value.today.count)
        assertEquals(3, dayTotal())
        assertTrue(app.log.events.all { it.context == ReadContext.Browse && it.method == ReadMethod.TAP && it.ayahId == ayah })
    }

    @Test
    fun `set count raises, lowers and clears today's count of one ayah`() = runTest {
        val reader = browse()
        val ayah = app.ayah(2, 255)
        reader.onCheck(reader.row(ayah))

        reader.onSetCountRequested(reader.row(ayah))
        assertEquals("pre-filled with today's count", ReaderDialog.SetCount(reader.row(ayah), 1), reader.state.value.dialog)
        reader.onSetCount(100)

        assertNull(reader.state.value.dialog)
        assertEquals(100, reader.countOf(ayah))
        assertEquals(100, dayTotal())

        reader.onSetCountRequested(reader.row(ayah))
        reader.onSetCount(3)
        assertEquals(3, reader.countOf(ayah))

        reader.onSetCountRequested(reader.row(ayah))
        reader.onSetCount(0)
        assertEquals(0, reader.countOf(ayah))
        assertEquals(0, dayTotal())
    }

    @Test
    fun `dismissing the set count dialog changes nothing`() = runTest {
        val reader = browse()
        reader.onSetCountRequested(reader.row(app.ayah(2, 2)))
        reader.onDialogDismissed()
        reader.onSetCount(50)

        assertEquals(0, dayTotal())
    }

    // ---- Mark up to here -------------------------------------------------------------------------

    @Test
    fun `mark up to here fills the unread run and Undo takes it back`() = runTest {
        val reader = browse(2, 1)
        reader.onCheck(reader.row(app.ayah(2, 2)))

        reader.onMarkUpToHere(reader.row(app.ayah(2, 6)))

        assertEquals("2:3 to 2:6: the run after the last read ayah", 4, reader.state.value.bulk?.added)
        assertEquals(listOf(0, 1, 1, 1, 1, 1, 0), (1..7).map { reader.countOf(app.ayah(2, it)) })
        assertEquals(5, dayTotal())

        reader.onUndoBulk()

        assertNull(reader.state.value.bulk)
        assertEquals("only the tapped reading remains", listOf(0, 1, 0, 0, 0, 0, 0), (1..7).map { reader.countOf(app.ayah(2, it)) })
        assertEquals(1, dayTotal())
    }

    @Test
    fun `mark up to here does not reach back before the surah the reader was opened in`() = runTest {
        val reader = browse(2, 1)

        reader.onMarkUpToHere(reader.row(app.ayah(2, 3)))

        assertEquals(3, dayTotal())
        assertEquals(0, reader.countOf(app.ayah(1, 7)))
    }

    @Test
    fun `when the snackbar goes away the undo offer ends and the readings stay`() = runTest {
        val reader = browse(2, 1)
        reader.onMarkUpToHere(reader.row(app.ayah(2, 3)))
        val batch = reader.state.value.bulk!!

        reader.onBulkNoticeShown(batch)
        reader.onUndoBulk()

        assertNull(reader.state.value.bulk)
        assertEquals(3, dayTotal())
    }

    // ---- Khatm: a tick that skips ahead ----------------------------------------------------------

    private suspend fun khatmReader(): Pair<ReaderViewModel, ReadContext.Khatm> {
        val khatm = app.khatms.start("Ramadan khatm")
        return reader(ReaderRoute.khatm(khatm.id)) to ReadContext.Khatm(khatm.id)
    }

    @Test
    fun `in a khatm, ticking in sequence writes at once`() = runTest {
        val (reader, _) = khatmReader()

        reader.onCheck(reader.row(app.ayah(1, 1)))
        reader.onCheck(reader.row(app.ayah(1, 2)))

        assertNull(reader.state.value.dialog)
        assertEquals(2, dayTotal())
    }

    @Test
    fun `in a khatm, a tick that skips ahead asks first and writes nothing`() = runTest {
        val (reader, _) = khatmReader()
        reader.onCheck(reader.row(app.ayah(1, 1)))

        reader.onCheck(reader.row(app.ayah(1, 5)))

        val dialog = reader.state.value.dialog as ReaderDialog.KhatmSkip
        assertEquals("1:2 to 1:5", AyahRange(app.ayah(1, 2), app.ayah(1, 5)), dialog.gap)
        assertEquals("nothing is written until the user answers", 1, dayTotal())
    }

    @Test
    fun `mark n ayahs fills the gap in one undoable action`() = runTest {
        val (reader, khatm) = khatmReader()
        reader.onCheck(reader.row(app.ayah(1, 1)))
        reader.onCheck(reader.row(app.ayah(1, 5)))

        reader.onSkipMarkAll()

        assertNull(reader.state.value.dialog)
        assertEquals(listOf(1, 1, 1, 1, 1, 0), (1..6).map { reader.countOf(app.ayah(1, it)) })
        assertTrue(app.log.events.all { it.context == khatm })
        assertEquals(4, reader.state.value.bulk?.added)

        reader.onUndoBulk()
        assertEquals(1, dayTotal())
    }

    @Test
    fun `only this ayah marks one, and the following ayahs are in sequence again`() = runTest {
        val (reader, _) = khatmReader()
        reader.onCheck(reader.row(app.ayah(2, 10)))
        assertTrue(reader.state.value.dialog is ReaderDialog.KhatmSkip)

        reader.onSkipOnlyThis()

        assertNull(reader.state.value.dialog)
        assertEquals(1, dayTotal())
        assertEquals(1, reader.countOf(app.ayah(2, 10)))

        reader.onCheck(reader.row(app.ayah(2, 11)))
        assertNull("the next ayah ticks silently", reader.state.value.dialog)
        assertEquals(2, dayTotal())
    }

    @Test
    fun `cancelling the khatm dialog writes nothing`() = runTest {
        val (reader, _) = khatmReader()
        reader.onCheck(reader.row(app.ayah(1, 5)))

        reader.onDialogDismissed()
        reader.onSkipMarkAll()
        reader.onSkipOnlyThis()

        assertNull(reader.state.value.dialog)
        assertEquals(0, dayTotal())
        assertTrue(app.log.events.isEmpty())
    }

    @Test
    fun `a khatm opens at its resume position and shows earlier days as covered`() = runTest {
        val khatm = app.khatms.start("Khatm")
        val context = ReadContext.Khatm(khatm.id)
        app.tracker.markRange(AyahRange.of(1, 20), context, ReadMethod.BULK, io.github.mehrdad_abdi.qintar.domain.model.RangeMode.FILL)
        app.clock.advanceDays(1)

        val reader = reader(ReaderRoute.khatm(khatm.id))
        val state = reader.state.value

        assertEquals(ReaderPhase.READY, state.phase)
        assertEquals(ReaderTitle.Name("Khatm"), state.title)
        assertEquals("opens at ayah 21", AyahId(21), state.openedAyah())
        assertEquals(AyahCheckState.CoveredEarlier, state.checkState(reader.row(AyahId(20))))
        assertEquals("yesterday's reading is not today's count", 0, reader.countOf(AyahId(20)))
        assertEquals(AyahCheckState.Unread, state.checkState(reader.row(AyahId(21))))

        reader.onCheck(reader.row(AyahId(20)))
        assertNull("re-reading a covered ayah needs no dialog", reader.state.value.dialog)
        assertEquals(AyahCheckState.ReadToday, reader.state.value.checkState(reader.row(AyahId(20))))
    }

    // ---- Contexts --------------------------------------------------------------------------------

    @Test
    fun `the same ayah is counted separately in each context`() = runTest {
        val kursi = app.ayah(2, 255)
        val khatm = app.khatms.start("Khatm")
        val bookmark = app.library.create(BookmarkDrafts.ayah(kursi).copy(description = "After every prayer"))
        app.tracker.mark(kursi, ReadContext.Khatm(khatm.id), ReadMethod.TAP)

        val inBookmark = reader(ReaderRoute.bookmark(bookmark.id))
        assertEquals("read in the khatm this morning, still zero here", 0, inBookmark.countOf(kursi))

        inBookmark.onCheck(inBookmark.row(kursi))
        val inBrowse = reader(ReaderRoute.browse(kursi.value))

        assertEquals(1, inBookmark.countOf(kursi))
        assertEquals(0, inBrowse.countOf(kursi))
        assertEquals("both readings count toward the day", 2, dayTotal())
        assertEquals(2, inBrowse.state.value.today.count)
        assertEquals(
            setOf(ReadContext.Khatm(khatm.id), ReadContext.Bookmark(bookmark.id)),
            app.log.events.map { it.context }.toSet(),
        )
    }

    @Test
    fun `a bookmark reader covers the bookmark's passage and the basmala is not counted`() = runTest {
        val ikhlas = app.library.create(BookmarkDrafts.surah(112, app.index))
        val reader = reader(ReaderRoute.bookmark(ikhlas.id))
        val rows = reader.state.value.rows

        assertTrue("the bookmark header, then the surah header and its basmala", rows[0] is ReaderRow.SegmentHeader && rows[1] is ReaderRow.SurahHeader && rows[2] is ReaderRow.Basmala)
        assertEquals(4, rows.count { it is ReaderRow.Ayah })

        reader.onMarkUpToHere(reader.row(app.ayah(112, 4)))

        assertEquals("four ayahs, and nothing for the basmala", 4, dayTotal())
        assertEquals(4, reader.state.value.today.count)
    }

    @Test
    fun `a random ayah is read under the Random context within its own scope`() = runTest {
        val ayah = app.ayah(55, 13)
        val reader = reader(ReaderRoute.random(ayah.value))

        reader.onCheck(reader.row(ayah))

        assertEquals(1, reader.state.value.rows.size)
        assertEquals(ReadContext.Random, app.log.events.single().context)
    }

    @Test
    fun `a group is read as one sequence and each bookmark counts its own readings`() = runTest {
        val kursi = app.ayah(2, 255)
        val group = app.library.createGroup("Morning")
        val first = app.library.create(BookmarkDrafts.ayah(kursi).copy(groupId = group.id))
        val second = app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = group.id))
        val third = app.library.create(BookmarkDraft(AyahRange.single(kursi), BookmarkKind.RANGE, label = "Again", groupId = group.id))
        val reader = reader(ReaderRoute.group(group.id))
        val state = reader.state.value
        val segments = state.model!!.segments

        assertEquals(ReaderTitle.Name("Morning"), state.title)
        assertEquals(setOf(first.id, second.id, third.id).map { ReadContext.Bookmark(it) }.toSet(), segments.map { it.context }.toSet())
        assertEquals(3, state.rows.count { it is ReaderRow.SegmentHeader })
        assertEquals(3, state.segmentTitles.size)
        val ikhlasSegment = segments.indexOfFirst { it.context == ReadContext.Bookmark(second.id) }
        val againSegment = segments.indexOfFirst { it.context == ReadContext.Bookmark(third.id) }
        val kursiSegment = segments.indexOfFirst { it.context == ReadContext.Bookmark(first.id) }

        reader.onCheck(reader.row(app.ayah(112, 2), ikhlasSegment))
        reader.onCheck(reader.row(kursi, againSegment))
        reader.onCheck(reader.row(kursi, againSegment))

        assertEquals(1, reader.countOf(app.ayah(112, 2), ikhlasSegment))
        assertEquals(2, reader.countOf(kursi, againSegment))
        assertEquals("the same ayah under the other bookmark is untouched", 0, reader.countOf(kursi, kursiSegment))
        assertEquals(
            mapOf(ReadContext.Bookmark(second.id) to 1, ReadContext.Bookmark(third.id) to 2),
            app.log.events.groupingBy { it.context }.eachCount(),
        )
    }

    @Test
    fun `something that no longer exists shows the not-found state`() = runTest {
        assertEquals(ReaderPhase.NOT_FOUND, reader(ReaderRoute.khatm(99)).state.value.phase)
        assertEquals(ReaderPhase.NOT_FOUND, reader(ReaderRoute.bookmark(99)).state.value.phase)
        assertEquals(ReaderPhase.NOT_FOUND, reader(ReaderRoute.group(99)).state.value.phase)
    }

    // ---- The reading day -------------------------------------------------------------------------

    @Test
    fun `a reader left open across midnight shows and marks the new day`() = runTest {
        val reader = browse()
        val ayah = app.ayah(2, 1)
        val yesterday = app.clock.today()
        reader.onCheck(reader.row(ayah))
        reader.onCheck(reader.row(ayah))
        assertEquals(2, reader.countOf(ayah))

        app.clock.advanceDays(1)

        assertEquals("the new day starts at zero", 0, reader.countOf(ayah))
        assertEquals(0, reader.state.value.today.count)

        reader.onCheck(reader.row(ayah))

        assertEquals(1, reader.countOf(ayah))
        assertEquals(1, reader.state.value.today.count)
        assertEquals(yesterday.plusDays(1), app.log.events.last().day)
        assertEquals("yesterday keeps its two", 2, app.totals.total(yesterday))
    }

    // ---- Rank ------------------------------------------------------------------------------------

    @Test
    fun `the rank-reached effect fires once, at the threshold, and fades after three seconds`() = runTest {
        val reader = browse()
        val ayah = app.ayah(2, 1)
        repeat(9) { reader.onCheck(reader.row(ayah)) }
        assertNull(reader.state.value.reachedRank)
        assertEquals(Rank.NONE, reader.state.value.today.rank)
        assertEquals(1, reader.state.value.today.toNext)

        reader.onCheck(reader.row(ayah))

        assertEquals(Rank.NOT_HEEDLESS, reader.state.value.reachedRank)
        assertEquals(Rank.NOT_HEEDLESS, reader.state.value.today.rank)

        main.advanceTimeBy(3_000)
        assertNull("the pill returns to the count", reader.state.value.reachedRank)

        reader.onCheck(reader.row(ayah))
        assertNull("not again at 11", reader.state.value.reachedRank)
    }

    @Test
    fun `a rank is not celebrated twice in a day, nor when the reader opens above it`() = runTest {
        app.tracker.setCount(app.ayah(2, 1), ReadContext.Browse, app.clock.today(), 12)
        val reader = browse()
        val ayah = app.ayah(2, 1)
        assertNull("opening at 12 is not an achievement", reader.state.value.reachedRank)

        reader.onSetCountRequested(reader.row(ayah))
        reader.onSetCount(5)
        reader.onSetCountRequested(reader.row(ayah))
        reader.onSetCount(12)

        assertNull(reader.state.value.reachedRank)

        reader.onSetCountRequested(reader.row(ayah))
        reader.onSetCount(120)
        assertEquals("a bulk jump shows the highest rank crossed", Rank.QANIT, reader.state.value.reachedRank)
    }

    @Test
    fun `the pill counts down to the goal rank when one is set`() = runTest {
        app.prefs.update { it.copy(goalRank = Rank.QANIT) }
        val reader = browse()

        reader.onCheck(reader.row(app.ayah(2, 1)))

        val today = reader.state.value.today
        assertTrue(today.captionTargetIsGoal)
        assertEquals(99, today.toCaptionTarget)
    }

    // ---- Stars -----------------------------------------------------------------------------------

    @Test
    fun `the star opens the bookmark editor filled with the ayah`() = runTest {
        val reader = browse()
        val ayah = app.ayah(2, 5)
        assertFalse(ayah in reader.state.value.bookmarkedBy)
        assertEquals(BookmarkEditorRoute.forAyah(ayah.value), reader.starTarget(reader.row(ayah)))
        assertTrue("nothing is saved by the star itself", app.bookmarkStore.bookmarks().isEmpty())
    }

    @Test
    fun `an ayah inside a bookmark has a filled star that opens that bookmark`() = runTest {
        val range = io.github.mehrdad_abdi.qintar.domain.model.AyahRange(app.ayah(2, 255), app.ayah(2, 257))
        val bookmark = app.library.create(
            io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft(range, io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind.RANGE),
        )
        val reader = browse(2, 256)
        val inside = app.ayah(2, 256)
        assertEquals(bookmark.id, reader.state.value.bookmarkedBy[inside])
        assertEquals(BookmarkEditorRoute(bookmarkId = bookmark.id), reader.starTarget(reader.row(inside)))
    }

    // ---- Opening, text and settings ----------------------------------------------------------------

    @Test
    fun `browse opens at the requested ayah and remembers where the user is`() = runTest {
        val start = app.ayah(18, 1)
        val reader = reader(ReaderRoute.browse(start.value))
        val state = reader.state.value

        assertEquals(start, state.openedAyah())
        main.advanceTimeBy(ReaderViewModel.BROWSE_POSITION_DEBOUNCE_MILLIS)
        assertEquals(start, app.prefs.current.lastBrowseAyahId)

        val later = state.model!!.indexOf(0, app.ayah(18, 60))!!
        reader.onVisibleRows(later, later + 3)
        main.advanceTimeBy(ReaderViewModel.BROWSE_POSITION_DEBOUNCE_MILLIS)

        assertEquals(app.ayah(18, 60), app.prefs.current.lastBrowseAyahId)
    }

    @Test
    fun `browse without a position opens where the user last was`() = runTest {
        app.prefs.update { it.copy(lastBrowseAyahId = app.ayah(36, 1)) }
        val reader = reader(ReaderRoute(io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry.BROWSE))
        val state = reader.state.value

        assertEquals(app.ayah(36, 1), state.openedAyah())
    }

    @Test
    fun `a whole-Quran scope knows all its rows but loads text only around what is on screen`() = runTest {
        val reader = browse(2, 1)
        val state = reader.state.value

        assertEquals(6236, state.rows.count { it is ReaderRow.Ayah })
        assertTrue("text of the opening ayah is there", app.ayah(2, 1) in state.texts)
        assertTrue("only a few pages are loaded: ${state.texts.size} ayahs", state.texts.size < 60)
        assertFalse(app.ayah(50, 1) in state.texts)

        val far = state.model!!.indexOf(0, app.ayah(50, 1))!!
        reader.onVisibleRows(far, far + 5)

        assertEquals("ayah 50:1", reader.state.value.texts[app.ayah(50, 1)])
    }

    @Test
    fun `scrolling the whole Quran keeps a bounded number of pages in memory`() = runTest {
        val reader = browse(1, 1)
        val model = reader.state.value.model!!

        for (id in 1..6236 step 9) {
            val row = model.indexOf(0, AyahId(id))!!
            reader.onVisibleRows(row, row + 8)
        }

        val texts = reader.state.value.texts
        assertTrue("${texts.size} ayahs cached", texts.size <= ReaderViewModel.CACHED_PAGES * 11)
        assertTrue("the end of the Quran is loaded", AyahId(6236) in texts)
        assertFalse("the beginning was let go", AyahId(1) in texts)
    }

    @Test
    fun `font size, pause-mark breaks and keep-screen-on come from the settings, live`() = runTest {
        val reader = browse()
        assertEquals(UserPrefs.DEFAULT_FONT_SP, reader.state.value.fontSp)
        assertTrue(reader.state.value.breakAtPauseMarks && reader.state.value.keepScreenOn)

        app.prefs.update { it.copy(quranFontSp = 40, breakAtPauseMarks = false, keepScreenOn = false) }

        val state = reader.state.value
        assertEquals(40, state.fontSp)
        assertFalse(state.breakAtPauseMarks)
        assertFalse(state.keepScreenOn)
    }
}

/**
 * The ayah the list opens at: the first ayah row at or after [ReaderUiState.initialRowIndex]. Only the
 * surah header, basmala and page divider that belong to it may stand between (so that the header of a
 * surah is visible when the reader opens at its first ayah).
 */
private fun ReaderUiState.openedAyah(): AyahId {
    val rest = rows.drop(initialRowIndex)
    val ayah = rest.filterIsInstance<ReaderRow.Ayah>().first()
    val above = rest.takeWhile { it !is ReaderRow.Ayah }
    assertTrue("only headers above the first ayah: $above", above.all { it is ReaderRow.SurahHeader || it is ReaderRow.Basmala || it is ReaderRow.PageDivider || it is ReaderRow.SegmentHeader })
    return ayah.id
}
