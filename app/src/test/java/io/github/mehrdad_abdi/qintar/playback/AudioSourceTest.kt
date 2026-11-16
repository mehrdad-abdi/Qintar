package io.github.mehrdad_abdi.qintar.playback

import android.os.Bundle
import androidx.media3.common.PlaybackException
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Where the audio comes from: the URL of an ayah, and the curated reciters. */
class AudioSourceTest {
    private val source = IslamicNetworkAudioSource()

    @Test
    fun `the URL is the CDN's per-ayah file by bitrate, reciter and global ayah number`() {
        assertEquals("https://cdn.islamic.network/quran/audio/64/ar.alafasy/1.mp3", source.url("ar.alafasy", AyahId(1), 64))
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.husary/6236.mp3", source.url("ar.husary", AyahId.LAST, 128))
    }

    @Test
    fun `the defaults of the preferences give a file the CDN has`() {
        val prefs = UserPrefs()
        assertEquals("https://cdn.islamic.network/quran/audio/64/ar.alafasy/262.mp3", source.url(prefs.reciterId, AyahId(262), prefs.audioQuality))
    }

    @Test
    fun `a bitrate the reciter does not have becomes the nearest one it has`() {
        assertEquals("only 128 exists for Minshawi", "$BASE/128/ar.minshawi/5.mp3", source.url("ar.minshawi", AyahId(5), 64))
        assertEquals("$BASE/192/ar.abdulbasitmurattal/5.mp3", source.url("ar.abdulbasitmurattal", AyahId(5), 128 + 40))
        assertEquals("a tie goes to the lower bitrate", "$BASE/64/ar.abdulbasitmurattal/5.mp3", source.url("ar.abdulbasitmurattal", AyahId(5), 128))
        assertEquals("$BASE/48/ar.parhizgar/5.mp3", source.url("ar.parhizgar", AyahId(5), 64))
    }

    @Test
    fun `an unknown reciter id falls back to the default reciter`() {
        assertEquals("$BASE/64/ar.alafasy/5.mp3", source.url("xx.gone", AyahId(5), 64))
        assertEquals(UserPrefs.DEFAULT_RECITER, Reciters.of("").id)
    }

    @Test
    fun `the URL carries nothing but bitrate, reciter and ayah`() {
        val url = source.url("ar.alafasy", AyahId(300), 64)
        assertFalse(url.contains('?'))
        assertFalse(url.contains('#'))
        assertTrue(Regex("https://cdn\\.islamic\\.network/quran/audio/\\d+/[a-z.]+/\\d+\\.mp3").matches(url))
    }

    @Test
    fun `the reciter list is well formed and begins with the default reciter`() {
        assertEquals(UserPrefs.DEFAULT_RECITER, Reciters.all.first().id)
        assertEquals("ids are unique", Reciters.all.size, Reciters.all.map { it.id }.toSet().size)
        assertEquals("names are distinct", Reciters.all.size, Reciters.all.map { it.name }.toSet().size)
        for (reciter in Reciters.all) {
            assertTrue(reciter.id, reciter.bitrates.isNotEmpty() && reciter.bitrates == reciter.bitrates.sorted())
            assertTrue(reciter.id, reciter.id.matches(Regex("[a-z]{2}\\.[a-z]+")))
        }
        assertTrue(UserPrefs.DEFAULT_AUDIO_QUALITY in Reciters.of(UserPrefs.DEFAULT_RECITER).bitrates)
    }

    private companion object {
        const val BASE = IslamicNetworkAudioSource.BASE
    }
}

/** What crosses the media session as bundles. */
@RunWith(RobolectricTestRunner::class)
class SessionProtocolTest {
    private val group = PlayRequest(
        origin = PlaybackOrigin(ReaderEntry.GROUP, id = 4),
        segments = listOf(
            Segment(ReadContext.Bookmark(11), AyahRange.of(262, 262)),
            Segment(ReadContext.Bookmark(12), AyahRange.of(6222, 6225)),
        ),
        startSegment = 1,
        startAyah = AyahId(6223),
    )

    @Test
    fun `a play request survives the bundle, with every segment's context`() {
        assertEquals(group, SessionProtocol.playRequest(SessionProtocol.playArgs(group)))

        for (context in listOf(ReadContext.Khatm(3), ReadContext.Browse, ReadContext.Random)) {
            val request = PlayRequest(PlaybackOrigin(ReaderEntry.RANDOM, scopeFirstAyahId = 8, scopeLastAyahId = 20), listOf(Segment(context, AyahRange.of(8, 20))), 0, AyahId(9))
            assertEquals(request, SessionProtocol.playRequest(SessionProtocol.playArgs(request)))
        }
    }

    @Test
    fun `arguments that do not describe a request are refused`() {
        assertNull(SessionProtocol.playRequest(Bundle()))
        assertNull("start outside its segment", SessionProtocol.playRequest(SessionProtocol.playArgs(group.copy(startAyah = AyahId(262)))))
        assertNull("no such segment", SessionProtocol.playRequest(SessionProtocol.playArgs(group.copy(startSegment = 2))))
        val broken = SessionProtocol.playArgs(group).apply { putIntArray("segmentEnds", intArrayOf(262, 9_999)) }
        assertNull("an ayah that does not exist", SessionProtocol.playRequest(broken))
    }

    @Test
    fun `the session extras carry origin, repeat and reciter`() {
        val origin = PlaybackOrigin(ReaderEntry.KHATM, id = 9)
        val info = SessionProtocol.sessionInfo(SessionProtocol.sessionExtras(origin, RepeatSettings(3, passage = true), "ar.husary"))
        assertEquals(SessionProtocol.SessionInfo(origin, RepeatSettings(3, true), "ar.husary", closed = false), info)

        val closed = SessionProtocol.sessionInfo(SessionProtocol.sessionExtras(null, RepeatSettings(), null, closed = true))
        assertTrue(closed.closed)
        assertNull(closed.origin)
        assertEquals("an empty bundle is a session that plays nothing", SessionProtocol.SessionInfo(null, RepeatSettings(), null, false), SessionProtocol.sessionInfo(Bundle()))
    }

    @Test
    fun `repeat arguments from outside are kept within bounds`() {
        assertEquals(RepeatSettings(1), SessionProtocol.repeat(Bundle().apply { putInt("repeatAyah", -4) }))
        assertEquals(RepeatSettings(SessionProtocol.MAX_AYAH_REPEAT), SessionProtocol.repeat(Bundle().apply { putInt("repeatAyah", 1_000_000) }))
        assertEquals(RepeatSettings(5, true), SessionProtocol.repeat(SessionProtocol.repeatArgs(RepeatSettings(5, true))))
    }

    @Test
    fun `a media item says which ayah of which segment and context it is, and where it is in the queue`() {
        val entry = QueueEntry(QueueKind.AYAH, AyahId(6223), surah = 112, segment = 1, context = ReadContext.Bookmark(12))
        val extras = SessionProtocol.itemExtras(entry, Cursor(index = 3, play = 2))

        assertEquals(NowPlaying(AyahId(6223), 112, 1, basmala = false, context = ReadContext.Bookmark(12), play = 2), SessionProtocol.nowPlaying(extras))
        assertEquals(Cursor(3, 2), SessionProtocol.cursor(extras))
        assertEquals(PlayingItem(AyahId(6223), ReadContext.Bookmark(12)), SessionProtocol.nowPlaying(extras)!!.playingItem())

        val basmala = SessionProtocol.nowPlaying(SessionProtocol.itemExtras(entry.copy(kind = QueueKind.BASMALA), Cursor(0)))!!
        assertTrue(basmala.basmala)
        assertNull("the basmala has no ayah to count", basmala.playingItem().ayah)

        assertNull(SessionProtocol.nowPlaying(null))
        assertNull(SessionProtocol.nowPlaying(Bundle()))
        assertNull(SessionProtocol.cursor(Bundle()))
    }

    @Test
    fun `the origin opens the same reader again, and routes into one reader share their origin`() {
        val route = ReaderRoute.random(firstAyahId = 8, lastAyahId = 20)
        assertEquals(route.origin(), route.copy(startAyahId = 15).origin())
        assertEquals(ReaderRoute(ReaderEntry.RANDOM, scopeFirstAyahId = 8, scopeLastAyahId = 20), route.origin().route())
        assertEquals(ReaderRoute.group(4), ReaderRoute.group(4).origin().route())
        assertFalse(ReaderRoute.bookmark(4).origin() == ReaderRoute.group(4).origin())
    }

    @Test
    fun `no connection is told apart from other playback failures`() {
        fun error(code: Int) = PlaybackException("x", null, code)
        assertEquals(PlaybackProblem.OFFLINE, MediaPlayerController.problemOf(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
        assertEquals(PlaybackProblem.OFFLINE, MediaPlayerController.problemOf(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)))
        assertEquals(PlaybackProblem.FAILED, MediaPlayerController.problemOf(error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)))
        assertEquals(PlaybackProblem.FAILED, MediaPlayerController.problemOf(error(PlaybackException.ERROR_CODE_DECODING_FAILED)))
    }
}
