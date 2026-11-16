package io.github.mehrdad_abdi.qintar.playback

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.testing.QuranFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The queue (§7.8): what plays, in which order, under which context, and what follows what. */
class PlaybackQueueTest {
    private val index = QuranFixture.index
    private fun ayah(surah: Int, number: Int) = QuranFixture.ayah(surah, number)
    private fun queue(vararg segments: Segment, basmala: Boolean = true) = PlaybackQueue.build(segments.toList(), index, basmala)
    private fun PlaybackQueue.ids() = entries.map { it.mediaId }

    private val ikhlas = Segment(ReadContext.Bookmark(7), index.surahRange(112))

    @Test
    fun `a surah is its basmala and then its ayahs in order, each with the segment's context`() {
        val q = queue(ikhlas)
        val first = ayah(112, 1).value

        assertEquals(listOf("basmala:112", "ayah:$first", "ayah:${first + 1}", "ayah:${first + 2}", "ayah:${first + 3}"), q.ids())
        assertEquals(setOf<ReadContext>(ReadContext.Bookmark(7)), q.entries.map { it.context }.toSet())
        assertEquals("the basmala is the recitation of 1:1", AyahId.FIRST, q[0].audioAyah)
        assertEquals(ayah(112, 2), q[2].audioAyah)
    }

    @Test
    fun `al-Fatiha and at-Tawba have no basmala item, and a passage inside a surah has none`() {
        assertEquals(7, queue(Segment(ReadContext.Browse, index.surahRange(1))).size)
        assertEquals(index.surahRange(9).size, queue(Segment(ReadContext.Browse, index.surahRange(9))).size)
        val inside = queue(Segment(ReadContext.Random, AyahRange(ayah(2, 255), ayah(2, 257))))
        assertEquals(List(3) { QueueKind.AYAH }, inside.entries.map { it.kind })
    }

    @Test
    fun `a passage across surahs gets the basmala of each surah it enters`() {
        val q = queue(Segment(ReadContext.Khatm(1), AyahRange(ayah(112, 4), ayah(114, 1))))
        assertEquals(
            listOf(
                "ayah:${ayah(112, 4).value}", "basmala:113",
                "ayah:${ayah(113, 1).value}", "ayah:${ayah(113, 2).value}", "ayah:${ayah(113, 3).value}",
                "ayah:${ayah(113, 4).value}", "ayah:${ayah(113, 5).value}", "basmala:114", "ayah:${ayah(114, 1).value}",
            ),
            q.ids(),
        )
    }

    @Test
    fun `a reciter whose files carry the basmala gets no basmala items`() {
        assertEquals(4, queue(ikhlas, basmala = false).size)
    }

    @Test
    fun `the whole Quran is every ayah once plus 112 basmalas`() {
        val q = queue(Segment(ReadContext.Browse, AyahRange.ALL))
        assertEquals(6236, q.entries.count { it.kind == QueueKind.AYAH })
        assertEquals(112, q.entries.count { it.kind == QueueKind.BASMALA })
    }

    @Test
    fun `a group keeps each bookmark's context and segment, also for an ayah that is in two bookmarks`() {
        val kursi = ayah(2, 255)
        val q = queue(
            Segment(ReadContext.Bookmark(1), AyahRange.single(kursi)),
            ikhlas,
            Segment(ReadContext.Bookmark(3), AyahRange.single(kursi)),
        )

        assertEquals(listOf(0, 1, 1, 1, 1, 1, 2), q.entries.map { it.segment })
        assertEquals(ReadContext.Bookmark(1), q[0].context)
        assertEquals(ReadContext.Bookmark(7), q[3].context)
        assertEquals(ReadContext.Bookmark(3), q[6].context)
        assertEquals(0, q.indexOf(0, kursi))
        assertEquals(6, q.indexOf(2, kursi))
        assertNull(q.indexOf(1, kursi))
    }

    @Test
    fun `playback from ayah 1 of a surah starts at its basmala, from any other ayah at the ayah`() {
        val q = queue(ikhlas)
        assertEquals(Cursor(0), q.startAt(0, ayah(112, 1)))
        assertEquals(Cursor(3), q.startAt(0, ayah(112, 3)))
        assertNull("not in the scope", q.startAt(0, ayah(2, 1)))
        assertNull(q.startAt(4, ayah(112, 1)))
    }

    @Test
    fun `without repeat the queue advances entry by entry and stops at the end of the scope`() {
        val q = queue(ikhlas)
        val once = RepeatSettings()
        var at: Cursor? = q.startAt(0, ayah(112, 3))
        val played = ArrayList<Int>()
        while (at != null) {
            played += at.index
            at = q.after(at, once)
        }
        assertEquals(listOf(3, 4), played)
        assertNull(q.after(Cursor(4), once))
    }

    @Test
    fun `repeat ayah plays each ayah n times and the basmala once`() {
        val q = queue(ikhlas)
        val thrice = RepeatSettings(ayahTimes = 3)
        var at: Cursor? = Cursor(0)
        val played = ArrayList<Cursor>()
        while (at != null && played.size < 8) {
            played += at
            at = q.after(at, thrice)
        }
        assertEquals(
            listOf(Cursor(0, 1), Cursor(1, 1), Cursor(1, 2), Cursor(1, 3), Cursor(2, 1), Cursor(2, 2), Cursor(2, 3), Cursor(3, 1)),
            played,
        )
        assertNull("the last ayah's third play ends the scope", q.after(Cursor(4, 3), thrice))
        assertEquals(Cursor(4, 3), q.after(Cursor(4, 2), thrice))
    }

    @Test
    fun `repeat passage starts again from the beginning of the scope after its last ayah`() {
        val q = queue(ikhlas)
        assertEquals(Cursor(0), q.after(Cursor(4), RepeatSettings(passage = true)))
        assertEquals("the ayah's repeats come first", Cursor(4, 2), q.after(Cursor(4), RepeatSettings(ayahTimes = 2, passage = true)))
        assertEquals(Cursor(0), q.after(Cursor(4, 2), RepeatSettings(ayahTimes = 2, passage = true)))
    }

    @Test
    fun `next leaves the repeats behind and ends at the end of the scope`() {
        val q = queue(ikhlas)
        assertEquals(Cursor(3), q.next(Cursor(2, 2)))
        assertNull(q.next(Cursor(4)))
    }

    @Test
    fun `previous goes back one ayah and steps over the basmala it stands behind`() {
        val q = queue(Segment(ReadContext.Browse, AyahRange(ayah(112, 3), ayah(113, 2))))
        // 0: 112:3, 1: 112:4, 2: basmala 113, 3: 113:1, 4: 113:2
        assertEquals(Cursor(3), q.previous(Cursor(4)))
        assertEquals("from 113:1 to 112:4, not to its own basmala", Cursor(1), q.previous(Cursor(3)))
        assertEquals(Cursor(1), q.previous(Cursor(2)))
        assertNull(q.previous(Cursor(0)))
        assertEquals("a basmala at the very start is where it ends", Cursor(0), queue(ikhlas).previous(Cursor(1)))
    }

    @Test
    fun `an ayah is never repeated zero times`() {
        assertThrows(IllegalArgumentException::class.java) { RepeatSettings(ayahTimes = 0) }
    }
}
