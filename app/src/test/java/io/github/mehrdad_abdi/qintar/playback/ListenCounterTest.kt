package io.github.mehrdad_abdi.qintar.playback

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Counting by listening (R-TRK-7), driven by the player's events alone: there is no screen, no view
 * model and no controller anywhere in these tests — only the counter the service owns, the tracker
 * and the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenCounterTest {
    private val app = DomainFixture(start = LocalDateTime.of(2026, 11, 15, 23, 58))
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val counter = ListenCounter(app.tracker, app.prefs, scope)

    @After
    fun tearDown() = scope.cancel()

    private fun item(ayah: AyahId, context: ReadContext = ReadContext.Browse) = PlayingItem(ayah, context)
    private fun basmala(context: ReadContext = ReadContext.Browse) = PlayingItem(null, context)
    private val a1 = app.ayah(112, 1)
    private val a2 = app.ayah(112, 2)
    private val a3 = app.ayah(112, 3)
    private fun counted() = app.log.events.map { it.ayahId }

    @Test
    fun `an ayah that plays to its end counts once, as listened, in its context`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        assertTrue("nothing is counted when an ayah starts", app.log.events.isEmpty())

        counter.onTransition(item(a2), TransitionReason.AUTO)

        val event = app.log.events.single()
        assertEquals(a1, event.ayahId)
        assertEquals(ReadMethod.AUDIO, event.method)
        assertEquals(ReadContext.Browse, event.context)
        assertEquals(app.clock.today(), event.day)
    }

    @Test
    fun `the last ayah of the queue counts when playback ends, and only once`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onEnded()
        counter.onEnded()
        // The queue is cleared afterwards (close): that is not another completion.
        counter.onTransition(null, TransitionReason.PLAYLIST_CHANGED)
        counter.onEnded()

        assertEquals(listOf(a1), counted())
    }

    @Test
    fun `skipping to another ayah does not count the one that was left`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onTransition(item(a2), TransitionReason.PLAYLIST_CHANGED) // "next": the service loads a new item
        counter.onTransition(item(a3), TransitionReason.SEEK) // a controller jumped
        assertTrue(app.log.events.isEmpty())

        counter.onTransition(null, TransitionReason.PLAYLIST_CHANGED) // closed in the middle of a3
        assertTrue(app.log.events.isEmpty())
    }

    @Test
    fun `an ayah the listener seeked forward in does not count`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onSeekInItem(fromMs = 1_000, toMs = 9_000)
        counter.onTransition(item(a2), TransitionReason.AUTO)
        assertTrue("seeked past most of a1", app.log.events.isEmpty())

        // The flag belongs to that play only: a2 was heard in full.
        counter.onEnded()
        assertEquals(listOf(a2), counted())
    }

    @Test
    fun `seeking back, or back to the start after a skip, leaves a full listening that counts`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onSeekInItem(fromMs = 4_000, toMs = 1_000)
        counter.onTransition(item(a2), TransitionReason.AUTO)
        assertEquals(listOf(a1), counted())

        counter.onSeekInItem(fromMs = 500, toMs = 5_000)
        counter.onSeekInItem(fromMs = 5_200, toMs = 0)
        counter.onEnded()
        assertEquals(listOf(a1, a2), counted())
    }

    @Test
    fun `a tiny position adjustment by the player is not a skip`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onSeekInItem(fromMs = 1_000, toMs = 1_200)
        counter.onEnded()
        assertEquals(listOf(a1), counted())
    }

    @Test
    fun `an ayah that fails does not count, and counts when it is played through after all`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onError()
        assertTrue(app.log.events.isEmpty())

        // "Try again": the same item goes on and reaches its end.
        counter.onTransition(item(a2), TransitionReason.AUTO)
        assertEquals(listOf(a1), counted())
    }

    @Test
    fun `an ayah repeated three times counts three`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onTransition(item(a1), TransitionReason.AUTO) // second play: its own item in the playlist
        counter.onTransition(item(a1), TransitionReason.AUTO) // third play
        counter.onTransition(item(a2), TransitionReason.AUTO)

        assertEquals(listOf(a1, a1, a1), counted())
        assertEquals(3, runBlocking { app.totals.today() })
    }

    @Test
    fun `the player's own repeat of an item counts each round`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onTransition(item(a1), TransitionReason.REPEAT)
        counter.onEnded()
        assertEquals(listOf(a1, a1), counted())
    }

    @Test
    fun `replaying the finished last ayah from its start counts again`() {
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onEnded()
        counter.onSeekInItem(fromMs = 6_000, toMs = 0)
        counter.onEnded()
        assertEquals(listOf(a1, a1), counted())
    }

    @Test
    fun `the basmala is never counted`() {
        counter.onTransition(basmala(), TransitionReason.PLAYLIST_CHANGED)
        counter.onTransition(item(a1), TransitionReason.AUTO)
        assertTrue(app.log.events.isEmpty())
        counter.onEnded()
        assertEquals(listOf(a1), counted())
    }

    @Test
    fun `nothing is counted while count listened is off, and the setting is read at each completion`() = runBlocking {
        app.prefs.update { it.copy(countListened = false) }
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onTransition(item(a2), TransitionReason.AUTO)
        assertTrue(app.log.events.isEmpty())

        // Turned on while a2 plays: a2 counts, a1 stays uncounted.
        app.prefs.update { it.copy(countListened = true) }
        counter.onTransition(item(a3), TransitionReason.AUTO)
        assertEquals(listOf(a2), counted())

        app.prefs.update { it.copy(countListened = false) }
        counter.onEnded()
        assertEquals(listOf(a2), counted())
    }

    @Test
    fun `playback across the day boundary lands on both reading days`() {
        val first = LocalDate.of(2026, 11, 15)
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onTransition(item(a2), TransitionReason.AUTO) // a1 ends at 23:58
        app.clock.advanceMinutes(5) // a2 ends at 00:03
        counter.onTransition(item(a3), TransitionReason.AUTO)
        counter.onEnded()

        assertEquals(listOf(a1 to first, a2 to first.plusDays(1), a3 to first.plusDays(1)), app.log.events.map { it.ayahId to it.day })
        assertEquals(2, runBlocking { app.totals.today() })
    }

    @Test
    fun `the reading day follows the day-start setting at the moment of completion`() = runBlocking {
        val late = DomainFixture(start = LocalDateTime.of(2026, 11, 16, 2, 30))
        late.setDayStart(4 * 60)
        val counter = ListenCounter(late.tracker, late.prefs, scope)
        counter.onTransition(item(a1), TransitionReason.PLAYLIST_CHANGED)
        counter.onEnded()
        assertEquals("02:30 with the day starting at 04:00 is still yesterday", LocalDate.of(2026, 11, 15), late.log.events.single().day)
    }

    @Test
    fun `a group counts each ayah under the bookmark of its own segment`() = runBlocking {
        val kursi = app.ayah(2, 255)
        val group = app.library.createGroup("Morning")
        val first = app.library.create(BookmarkDrafts.ayah(kursi).copy(groupId = group.id))
        val second = app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = group.id))
        val queue = PlaybackQueue.build(
            listOf(
                Segment(ReadContext.Bookmark(first.id), AyahRange.single(kursi)),
                Segment(ReadContext.Bookmark(second.id), app.index.surahRange(112)),
            ),
            app.index,
        )

        // The whole group plays through, as the service would feed it: every entry, then the end.
        var at: Cursor? = Cursor(0)
        var reason = TransitionReason.PLAYLIST_CHANGED
        while (at != null) {
            val entry = queue[at.index]
            counter.onTransition(PlayingItem(entry.ayah.takeIf { entry.kind == QueueKind.AYAH }, entry.context), reason)
            reason = TransitionReason.AUTO
            at = queue.after(at, RepeatSettings())
        }
        counter.onEnded()

        assertEquals(
            mapOf<ReadContext, Int>(ReadContext.Bookmark(first.id) to 1, ReadContext.Bookmark(second.id) to 4),
            app.log.events.groupingBy { it.context }.eachCount(),
        )
        assertTrue(app.log.events.all { it.method == ReadMethod.AUDIO })
        assertEquals("the basmala of 112 is not among them", 5, app.log.events.size)
    }

    @Test
    fun `a bookmark deleted during playback does not break the counting of what follows`() = runBlocking {
        val bookmark = app.library.create(BookmarkDrafts.surah(112, app.index))
        val context = ReadContext.Bookmark(bookmark.id)
        counter.onTransition(item(a1, context), TransitionReason.PLAYLIST_CHANGED)
        app.library.delete(bookmark.id)
        counter.onTransition(item(a2, ReadContext.Browse), TransitionReason.AUTO)
        counter.onEnded()

        assertEquals(listOf(a2), app.log.events.filter { it.context == ReadContext.Browse }.map { it.ayahId })
    }
}
