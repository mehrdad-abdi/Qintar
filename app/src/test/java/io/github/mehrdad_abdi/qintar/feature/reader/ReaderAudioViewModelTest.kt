package io.github.mehrdad_abdi.qintar.feature.reader

import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.playback.DownloadState
import io.github.mehrdad_abdi.qintar.playback.PlayRequest
import io.github.mehrdad_abdi.qintar.playback.PlayerState
import io.github.mehrdad_abdi.qintar.playback.RepeatSettings
import io.github.mehrdad_abdi.qintar.playback.PlaybackProblem
import io.github.mehrdad_abdi.qintar.playback.origin
import io.github.mehrdad_abdi.qintar.testing.FakeAudioDownloads
import io.github.mehrdad_abdi.qintar.testing.FakePlayerController
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.playerBarViewModel
import io.github.mehrdad_abdi.qintar.testing.readerViewModel
import io.github.mehrdad_abdi.qintar.testing.row
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Audio in the reader (§5.4, §7.8), through the view models over a player that plays nothing: what
 * the player is asked to do, and what the reader shows of what the player reports.
 */
class ReaderAudioViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()
    private val player = FakePlayerController()
    private val downloads = FakeAudioDownloads()

    private fun reader(route: ReaderRoute) = app.readerViewModel(route, main.dispatcher, player = player, downloads = downloads)
    private val browseRoute = ReaderRoute.browse(app.ayah(2, 1).value)

    // ---- Starting --------------------------------------------------------------------------------

    @Test
    fun `play on an ayah asks for the reader's whole scope from that ayah`() {
        val reader = reader(browseRoute)
        val ayah = app.ayah(2, 5)

        reader.onPlay(reader.row(ayah))

        assertEquals(
            PlayRequest(browseRoute.origin(), listOf(Segment(ReadContext.Browse, AyahRange.ALL)), startSegment = 0, startAyah = ayah),
            player.requests.single(),
        )
        assertTrue("nothing is counted by starting", app.log.events.isEmpty())
    }

    @Test
    fun `a group reader hands over every bookmark with its own context, starting in the tapped one`() = runTest {
        val kursi = app.ayah(2, 255)
        val group = app.library.createGroup("Morning")
        val first = app.library.create(BookmarkDrafts.ayah(kursi).copy(groupId = group.id))
        val second = app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = group.id))
        val reader = reader(ReaderRoute.group(group.id))

        reader.onPlay(reader.row(app.ayah(112, 2), segment = 1))

        val request = player.requests.single()
        assertEquals(
            listOf(
                Segment(ReadContext.Bookmark(first.id), AyahRange.single(kursi)),
                Segment(ReadContext.Bookmark(second.id), app.index.surahRange(112)),
            ),
            request.segments,
        )
        assertEquals(1, request.startSegment)
        assertEquals(app.ayah(112, 2), request.startAyah)
        assertEquals(ReaderRoute.group(group.id).origin(), request.origin)
    }

    @Test
    fun `a khatm reader plays under the khatm`() = runTest {
        val khatm = app.khatms.start("Ramadan")
        val reader = reader(ReaderRoute.khatm(khatm.id))
        reader.onPlay(reader.row(app.ayah(1, 1)))
        assertEquals(listOf(Segment(ReadContext.Khatm(khatm.id), AyahRange.ALL)), player.requests.single().segments)
    }

    @Test
    fun `play from here starts at the first ayah on screen`() {
        val reader = reader(browseRoute)
        val model = reader.state.value.model!!
        val row = model.indexOf(0, app.ayah(2, 30))!!
        reader.onVisibleRows(row, row + 3)

        reader.onPlayFromHere()

        assertEquals(app.ayah(2, 30), player.requests.single().startAyah)
    }

    // ---- What the player reports -----------------------------------------------------------------

    @Test
    fun `the ayah being played is highlighted, and its button pauses and resumes instead of starting again`() {
        val reader = reader(browseRoute)
        val ayah = app.ayah(2, 5)
        val other = app.ayah(2, 6)
        assertFalse(reader.state.value.isPlaying(reader.row(ayah)))

        player.nowAt(browseRoute.origin(), 0, ayah)

        assertTrue(reader.state.value.isPlaying(reader.row(ayah)))
        assertFalse(reader.state.value.isPlaying(reader.row(other)))

        reader.onPlay(reader.row(ayah))
        assertEquals(listOf("toggle"), player.calls)
        assertTrue("no new queue for the ayah that is loaded", player.requests.isEmpty())

        // Paused: the row is not shown as playing, and its button resumes.
        player.nowAt(browseRoute.origin(), 0, ayah, playing = false)
        assertFalse(reader.state.value.isPlaying(reader.row(ayah)))
        reader.onPlay(reader.row(ayah))
        assertEquals(listOf("toggle", "toggle"), player.calls)

        // Another ayah: a new queue from there.
        reader.onPlay(reader.row(other))
        assertEquals(other, player.requests.single().startAyah)
    }

    @Test
    fun `an ayah that is loading shows as playing`() {
        val reader = reader(browseRoute)
        val ayah = app.ayah(2, 5)
        player.nowAt(browseRoute.origin(), 0, ayah, playing = false)
        player.state.value = player.state.value.copy(loading = true)
        assertTrue(reader.state.value.isPlaying(reader.row(ayah)))
    }

    @Test
    fun `audio that another reader started is not shown in this one`() {
        val reader = reader(browseRoute)
        val ayah = app.ayah(2, 5)
        player.nowAt(ReaderRoute.random(ayah.value, ayah.value + 3).origin(), 0, ayah, ReadContext.Random)

        assertNull(reader.state.value.audioAt)
        assertFalse(reader.state.value.isPlaying(reader.row(ayah)))

        // Its play button starts this reader's own queue.
        reader.onPlay(reader.row(ayah))
        assertEquals(browseRoute.origin(), player.requests.single().origin)
    }

    @Test
    fun `in a group the highlight is on the segment that plays, not on the same ayah elsewhere`() = runTest {
        val kursi = app.ayah(2, 255)
        val group = app.library.createGroup("Morning")
        app.library.create(BookmarkDrafts.ayah(kursi).copy(groupId = group.id))
        app.library.create(BookmarkDrafts.range(kursi, app.ayah(2, 256)).copy(groupId = group.id))
        val route = ReaderRoute.group(group.id)
        val reader = reader(route)

        player.nowAt(route.origin(), 1, kursi)

        assertFalse(reader.state.value.isPlaying(reader.row(kursi, segment = 0)))
        assertTrue(reader.state.value.isPlaying(reader.row(kursi, segment = 1)))
    }

    @Test
    fun `closing the player clears the highlight`() {
        val reader = reader(browseRoute)
        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 5))
        player.close()
        assertNull(reader.state.value.audioAt)
        assertFalse(reader.state.value.audioPlaying)
    }

    // ---- Following the audio ---------------------------------------------------------------------

    @Test
    fun `the list follows the audio when it leaves the screen, while the listener stays with it`() {
        val reader = reader(browseRoute)
        val model = reader.state.value.model!!
        fun rowOf(n: Int) = model.indexOf(0, app.ayah(2, n))!!
        reader.onVisibleRows(rowOf(1), rowOf(4))

        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 2))
        assertNull("the first ayah was tapped where it stands", reader.state.value.scrollRequest)

        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 3))
        assertNull("still fully on screen", reader.state.value.scrollRequest)

        // The next one is the last row on screen (cut off by the pill) and then beyond it.
        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 4))
        val request = reader.state.value.scrollRequest
        assertNotNull(request)
        assertEquals(model.blockStart(rowOf(4)), request!!.row)
    }

    @Test
    fun `scrolling away stops the following, and the player's title brings the ayah back`() {
        val reader = reader(browseRoute)
        val model = reader.state.value.model!!
        fun rowOf(n: Int) = model.indexOf(0, app.ayah(2, n))!!
        reader.onVisibleRows(rowOf(1), rowOf(4))
        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 2))

        // The reader scrolls far away while the audio goes on.
        reader.onVisibleRows(rowOf(100), rowOf(104))
        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 3))
        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 4))
        assertNull(reader.state.value.scrollRequest)

        reader.onShowPlaying()
        assertEquals(model.blockStart(rowOf(4)), reader.state.value.scrollRequest!!.row)
    }

    @Test
    fun `a reader opened from the mini player opens at the ayah being played`() {
        val playing = app.ayah(2, 200)
        player.nowAt(browseRoute.origin(), 0, playing)

        val back = reader(browseRoute.origin().route())

        val model = back.state.value.model!!
        assertEquals(model.blockStart(model.indexOf(0, playing)!!), back.state.value.initialRowIndex)
        assertTrue(back.state.value.isPlaying(back.row(playing)))
    }

    @Test
    fun `a reader opened at a requested ayah stays there even while its audio plays elsewhere`() {
        player.nowAt(browseRoute.origin(), 0, app.ayah(2, 200))
        val reader = reader(browseRoute)
        val model = reader.state.value.model!!
        assertEquals(model.blockStart(model.indexOf(0, app.ayah(2, 1))!!), reader.state.value.initialRowIndex)
    }

    // ---- Download for offline --------------------------------------------------------------------

    @Test
    fun `download this surah asks for the whole surah on screen`() {
        val reader = reader(ReaderRoute.browse(app.ayah(2, 255).value))
        reader.onDownloadSurah()
        assertEquals(listOf(listOf(app.index.surahRange(2))), downloads.requested)
    }

    @Test
    fun `download this passage is for a bookmark or a group, not for the whole Quran`() = runTest {
        val browse = reader(browseRoute)
        assertFalse(browse.state.value.canDownloadPassage)
        browse.onDownloadPassage()
        assertTrue(downloads.requested.isEmpty())

        val bookmark = app.library.create(BookmarkDrafts.surah(112, app.index))
        val reader = reader(ReaderRoute.bookmark(bookmark.id))
        assertTrue(reader.state.value.canDownloadPassage)
        reader.onDownloadPassage()
        assertEquals(listOf(listOf(app.index.surahRange(112))), downloads.requested)
    }

    @Test
    fun `the reader shows the download's progress and outcome, and can cancel it`() {
        val reader = reader(browseRoute)
        downloads.state.value = DownloadState.Running(3, 10)
        assertEquals(DownloadState.Running(3, 10), reader.state.value.download)

        reader.onDownloadCancel()
        assertEquals(1, downloads.cancelled)

        downloads.state.value = DownloadState.Blocked(DownloadState.Blocked.Reason.WIFI_ONLY)
        assertEquals(DownloadState.Blocked(DownloadState.Blocked.Reason.WIFI_ONLY), reader.state.value.download)
        reader.onDownloadNoticeShown()
        assertEquals(DownloadState.Idle, reader.state.value.download)
    }

    // ---- The mini player -------------------------------------------------------------------------

    @Test
    fun `the mini player is hidden until something is loaded and then names the surah, ayah and reciter`() {
        val bar = app.playerBarViewModel(player, main.dispatcher)
        assertFalse(bar.state.value.visible)
        assertNull(bar.state.value.returnRoute)

        player.nowAt(browseRoute.origin(), 0, app.ayah(8, 2))
        player.state.value = player.state.value.copy(reciterId = "ar.alafasy")

        val s = bar.state.value
        assertTrue(s.visible)
        assertTrue(s.playing)
        assertEquals(8, s.surah?.number)
        assertEquals(2, s.ayahNumber)
        assertEquals("ar.alafasy", s.reciterId)
        assertEquals("a tap goes back to the reader the audio came from", browseRoute.origin().route(), s.returnRoute)
    }

    @Test
    fun `the mini player's buttons reach the player`() {
        val bar = app.playerBarViewModel(player, main.dispatcher)
        player.nowAt(browseRoute.origin(), 0, app.ayah(8, 2))

        bar.onPlayPause()
        bar.onNext()
        bar.onPrevious()
        assertEquals(listOf("toggle", "next", "previous"), player.calls)

        bar.onRepeatAyah(3)
        assertEquals(RepeatSettings(ayahTimes = 3), bar.state.value.repeat)
        bar.onRepeatPassage(true)
        assertEquals("the ayah repeat is kept", RepeatSettings(ayahTimes = 3, passage = true), player.repeats.last())

        bar.onClose()
        assertFalse(bar.state.value.visible)
    }

    @Test
    fun `the speed button steps through the speeds, stores each and starts over`() {
        val bar = app.playerBarViewModel(player, main.dispatcher)
        assertEquals(1f, bar.state.value.speed)

        val seen = (1..5).map {
            bar.onSpeed()
            assertEquals("the speed is a setting", bar.state.value.speed, app.prefs.current.playbackSpeed)
            bar.state.value.speed
        }
        assertEquals(listOf(1.25f, 1.5f, 2f, 0.75f, 1f), seen)
    }

    @Test
    fun `the speed set in Settings is the one the mini player shows`() = runBlocking {
        val bar = app.playerBarViewModel(player, main.dispatcher)
        app.prefs.update { it.copy(playbackSpeed = 1.5f) }
        assertEquals(1.5f, bar.state.value.speed)
    }

    @Test
    fun `no connection is shown on the mini player, which stays to try again`() {
        val bar = app.playerBarViewModel(player, main.dispatcher)
        player.nowAt(browseRoute.origin(), 0, app.ayah(8, 2), playing = false)
        player.state.value = player.state.value.copy(problem = PlaybackProblem.OFFLINE)

        assertEquals(PlaybackProblem.OFFLINE, bar.state.value.problem)
        assertTrue(bar.state.value.visible)
        bar.onPlayPause()
        assertEquals(listOf("toggle"), player.calls)

        player.state.value = PlayerState()
        assertNull(bar.state.value.problem)
    }

    @Test
    fun `the basmala before a surah is named as such`() {
        val bar = app.playerBarViewModel(player, main.dispatcher)
        player.nowAt(browseRoute.origin(), 0, app.ayah(8, 1), basmala = true)
        assertTrue(bar.state.value.basmala)
        assertEquals(8, bar.state.value.surah?.number)
    }
}
