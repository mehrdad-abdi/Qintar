package io.github.mehrdad_abdi.qintar.domain

import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.StarResult
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** §4.7: creating and editing bookmarks and groups, the quick star, titles, and what deletion keeps. */
class BookmarkLibraryTest {
    private val app = DomainFixture()
    private val index = app.index
    private val kursi = app.ayah(2, 255)

    private suspend fun readFully(bookmarkId: Long, range: AyahRange) =
        app.tracker.markRange(range, ReadContext.Bookmark(bookmarkId), ReadMethod.BULK, RangeMode.ADD)

    // ---- what deleting keeps ----

    @Test
    fun `deleting a bookmark keeps the day totals`() = runTest {
        val b = app.library.create(BookmarkDrafts.surah(112, index))
        readFully(b.id, b.range)
        assertEquals(4, app.totals.today())

        app.library.delete(b.id)

        assertEquals(4, app.totals.today())
        assertEquals("history keeps the readings under the deleted bookmark's context", 4, app.log.events.count { it.context == ReadContext.Bookmark(b.id) })
        assertNull(app.availability.board().state(b.id))
    }

    @Test
    fun `editing a bookmark's label does not change any count`() = runTest {
        val b = app.library.create(BookmarkDrafts.surah(112, index))
        readFully(b.id, b.range)

        val edited = app.library.update(b.id, BookmarkDrafts.surah(112, index).copy(label = "Al-Ikhlas"))

        assertEquals("Al-Ikhlas", edited.label)
        assertEquals(4, app.totals.today())
        assertTrue(app.availability.board().state(b.id)!!.progress.done)
    }

    @Test
    fun `undoing a bookmark deletion brings it back with today's progress`() = runTest {
        val b = app.library.create(BookmarkDrafts.surah(112, index))
        readFully(b.id, b.range)

        val deleted = app.library.delete(b.id)
        app.library.restore(deleted)

        assertTrue(app.availability.board().state(b.id)!!.progress.done)
    }

    @Test
    fun `deleting a group can move its bookmarks to ungrouped, keeping bookmarks and readings`() = runTest {
        val group = app.library.createGroup("Morning", schedule = Schedule.daily())
        val b = app.library.create(BookmarkDrafts.surah(112, index).copy(groupId = group.id))
        readFully(b.id, b.range)

        app.library.deleteGroup(group.id, deleteBookmarks = false)

        val board = app.availability.board()
        assertTrue(board.groups.isEmpty())
        assertEquals(listOf(b.id), board.ungrouped.map { it.bookmark.id })
        assertNull("without the group there is no schedule to inherit", board.state(b.id)!!.schedule)
        assertEquals(4, app.totals.today())
    }

    @Test
    fun `deleting a group can delete its bookmarks too, and the readings still count`() = runTest {
        val group = app.library.createGroup("Morning")
        val inside = app.library.create(BookmarkDrafts.surah(112, index).copy(groupId = group.id))
        val outside = app.library.create(BookmarkDrafts.surah(113, index))
        readFully(inside.id, inside.range)

        app.library.deleteGroup(group.id, deleteBookmarks = true)

        assertEquals(listOf(outside.id), app.availability.board().all.map { it.bookmark.id })
        assertEquals(4, app.totals.today())
    }

    // ---- creating and editing ----

    @Test
    fun `a new bookmark defaults to one reading a day, no group and no schedule`() = runTest {
        val b = app.library.create(BookmarkDrafts.ayah(kursi))

        assertEquals(1, b.repeatTarget)
        assertNull(b.groupId)
        assertNull(b.schedule)
        assertEquals(app.clock.now(), b.createdAt)
    }

    @Test
    fun `blank label and description are stored as absent and surrounding spaces are dropped`() = runTest {
        val b = app.library.create(BookmarkDrafts.ayah(kursi).copy(label = "   ", description = "  the narration  "))

        assertNull(b.label)
        assertEquals("the narration", b.description)
    }

    @Test
    fun `a description may be 2000 characters and no longer`() = runTest {
        val ok = app.library.create(BookmarkDrafts.ayah(kursi).copy(description = "x".repeat(2000)))
        assertEquals(2000, ok.description!!.length)

        assertThrows(DomainFailure.InvalidInput::class.java) {
            runBlocking { app.library.create(BookmarkDrafts.ayah(kursi).copy(description = "x".repeat(2001))) }
        }
    }

    @Test
    fun `a repeat target must be at least one`() = runTest {
        assertThrows(DomainFailure.InvalidInput::class.java) {
            runBlocking { app.library.create(BookmarkDrafts.ayah(kursi).copy(repeatTarget = 0)) }
        }
        assertEquals(0, app.bookmarkStore.bookmarks().size)
    }

    @Test
    fun `a bookmark cannot be put in a group that does not exist`() = runTest {
        assertThrows(DomainFailure.GroupNotFound::class.java) {
            runBlocking { app.library.create(BookmarkDrafts.ayah(kursi).copy(groupId = 77)) }
        }
    }

    @Test
    fun `a group needs a name and cannot be scheduled once`() = runTest {
        assertThrows(DomainFailure.InvalidInput::class.java) { runBlocking { app.library.createGroup("  ") } }
        assertThrows(DomainFailure.InvalidInput::class.java) {
            runBlocking { app.library.createGroup("Morning", schedule = Schedule.once()) }
        }
    }

    @Test
    fun `editing keeps a bookmark's place in the custom order and its creation time`() = runTest {
        app.library.create(BookmarkDrafts.surah(1, index))
        val b = app.library.create(BookmarkDrafts.surah(112, index))
        app.clock.advanceDays(2)

        val edited = app.library.update(b.id, BookmarkDrafts.surah(113, index).copy(repeatTarget = 3))

        assertEquals(b.sortIndex, edited.sortIndex)
        assertEquals(b.createdAt, edited.createdAt)
        assertEquals(app.clock.now(), edited.updatedAt)
        assertEquals(index.surahRange(113), edited.range)
    }

    @Test
    fun `duplicating to another group makes an independent bookmark that starts unread`() = runTest {
        val morning = app.library.createGroup("Morning")
        val night = app.library.createGroup("Night")
        val original = app.library.create(BookmarkDrafts.surah(112, index).copy(groupId = morning.id, label = "Ikhlas", repeatTarget = 3))
        readFully(original.id, original.range)

        val copy = app.library.duplicateToGroup(original.id, night.id)

        assertNotEquals(original.id, copy.id)
        assertEquals(night.id, copy.groupId)
        assertEquals("Ikhlas", copy.label)
        assertEquals(3, copy.repeatTarget)
        val board = app.availability.board()
        assertTrue(board.state(original.id)!!.progress.started)
        assertFalse("read in Morning, it arrives at Night still unread", board.state(copy.id)!!.progress.started)
    }

    @Test
    fun `a bookmark can be moved between groups and back to ungrouped`() = runTest {
        val group = app.library.createGroup("Morning")
        val b = app.library.create(BookmarkDrafts.ayah(kursi))

        app.library.moveToGroup(b.id, group.id)
        assertEquals(listOf(b.id), app.availability.board().groups.single().bookmarks.map { it.bookmark.id })

        app.library.moveToGroup(b.id, null)
        assertEquals(listOf(b.id), app.availability.board().ungrouped.map { it.bookmark.id })
    }

    // ---- quick star ----

    @Test
    fun `the star creates a plain single-ayah bookmark and the ayah shows as starred`() = runTest {
        app.library.observeStarred().test {
            assertEquals(emptySet<AyahId>(), awaitItem())

            val result = app.library.toggleStar(kursi) as StarResult.Added

            assertEquals(setOf(kursi), awaitItem())
            assertEquals(BookmarkKind.AYAH, result.bookmark.kind)
            assertEquals(AyahRange.single(kursi), result.bookmark.range)
            assertEquals(1, result.bookmark.repeatTarget)
            assertNull(result.bookmark.groupId)
            assertNull(result.bookmark.schedule)
        }
    }

    @Test
    fun `tapping the star again removes the bookmark, and undo restores it`() = runTest {
        app.library.toggleStar(kursi)

        val removed = app.library.toggleStar(kursi) as StarResult.Removed
        assertEquals(0, app.bookmarkStore.bookmarks().size)

        app.library.restore(removed.bookmark)
        assertEquals(listOf(removed.bookmark), app.bookmarkStore.bookmarks())
    }

    @Test
    fun `un-starring a bookmark the user has elaborated asks first and removes only when confirmed`() = runTest {
        val elaborations = listOf(
            BookmarkDrafts.ayah(kursi).copy(label = "Ayat al-Kursi"),
            BookmarkDrafts.ayah(kursi).copy(description = "after every prayer"),
            BookmarkDrafts.ayah(kursi).copy(repeatTarget = 3),
            BookmarkDrafts.ayah(kursi).copy(schedule = Schedule.daily()),
            BookmarkDrafts.ayah(kursi).copy(groupId = app.library.createGroup("Morning").id),
        )
        for (draft in elaborations) {
            val b = app.library.create(draft)

            val asked = app.library.toggleStar(kursi)
            assertEquals("$draft", StarResult.NeedsConfirmation(b), asked)
            assertEquals("nothing was removed yet", 1, app.bookmarkStore.bookmarks().size)

            assertEquals(StarResult.Removed(b), app.library.toggleStar(kursi, confirmed = true))
            assertEquals(0, app.bookmarkStore.bookmarks().size)
        }
    }

    @Test
    fun `a range or surah bookmark that happens to start at an ayah does not make it starred`() = runTest {
        app.library.create(BookmarkDrafts.range(kursi, app.ayah(2, 257)))

        app.library.observeStarred().test { assertEquals(emptySet<AyahId>(), awaitItem()) }
        assertTrue("the star adds its own bookmark", app.library.toggleStar(kursi) is StarResult.Added)
        assertEquals(2, app.bookmarkStore.bookmarks().size)
    }

    @Test
    fun `every one of the 6236 ayahs can be bookmarked, loaded, formatted and titled`() = runTest {
        // Regression for the v1 reports "At-Taghabun 13 cannot be added; Al-Ma'arij 25 crashes": no ayah
        // may be special. Queueing for audio is exercised in :app.
        for (id in 1..Quran.AYAH_COUNT) {
            val ayah = AyahId(id)
            val draft = BookmarkDrafts.ayah(ayah)
            val title = BookmarkTitles.of(draft, index) as BookmarkTitle.Ayah
            assertEquals(index.ref(ayah).ayah, title.ayah)
            val text = app.text.ayahs(draft.range).single()
            assertEquals(ayah, text.id)
            val shown = io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter.format(text.text, title.ayah, true)
            assertTrue(shown.startsWith(text.text))
        }
        val taghabun13 = app.library.create(BookmarkDrafts.ayah(app.ayah(64, 13)))
        val maarij25 = app.library.create(BookmarkDrafts.ayah(app.ayah(70, 25)))
        assertEquals(2, app.availability.board().all.size)
        assertEquals(AyahRange.single(app.ayah(64, 13)), taghabun13.range)
        assertEquals(AyahRange.single(app.ayah(70, 25)), maarij25.range)
    }

    // ---- titles ----

    @Test
    fun `a label is the title when there is one`() {
        val draft = BookmarkDrafts.ayah(kursi).copy(label = " Ayat al-Kursi ")
        assertEquals(BookmarkTitle.Label("Ayat al-Kursi"), BookmarkTitles.of(draft, index))
    }

    @Test
    fun `without a label the title describes the passage by its kind`() {
        val baqarah = index.surah(2)
        val imran = index.surah(3)
        val kahf = index.surah(18)

        assertEquals(BookmarkTitle.Ayah(baqarah, 255), BookmarkTitles.of(BookmarkDrafts.ayah(kursi), index))
        assertEquals(
            BookmarkTitle.RangeInSurah(kahf, 1, 10),
            BookmarkTitles.of(BookmarkDrafts.range(app.ayah(18, 1), app.ayah(18, 10)), index),
        )
        assertEquals(
            BookmarkTitle.RangeAcrossSurahs(baqarah, 285, imran, 5),
            BookmarkTitles.of(BookmarkDrafts.range(app.ayah(2, 285), app.ayah(3, 5)), index),
        )
        assertEquals(BookmarkTitle.Surah(kahf), BookmarkTitles.of(BookmarkDrafts.surah(18, index), index))
        assertEquals(BookmarkTitle.Page(293), BookmarkTitles.of(BookmarkDrafts.page(293, index), index))
        assertEquals(BookmarkTitle.Juz(30), BookmarkTitles.of(BookmarkDrafts.juz(30, index), index))
    }

    @Test
    fun `a blank label falls back to the passage title`() {
        val draft = BookmarkDrafts.surah(18, index).copy(label = "  ")
        assertEquals(BookmarkTitle.Surah(index.surah(18)), BookmarkTitles.of(draft, index))
    }
}
