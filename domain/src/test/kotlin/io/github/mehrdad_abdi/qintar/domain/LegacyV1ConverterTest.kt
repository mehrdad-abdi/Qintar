package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.backup.ImportMode
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyConversion
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyV1Converter
import io.github.mehrdad_abdi.qintar.domain.backup.V1Bookmark
import io.github.mehrdad_abdi.qintar.domain.backup.V1Data
import io.github.mehrdad_abdi.qintar.domain.backup.V1ReadingActivity
import io.github.mehrdad_abdi.qintar.domain.backup.V1Settings
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §7.11: every row of the v1 → v2 conversion table, and that the result imports to the same day totals. */
class LegacyV1ConverterTest {
    private val app = DomainFixture()
    private val index = app.index
    private val today: LocalDate = app.clock.today()

    private fun v1Bookmark(
        id: Long,
        type: String,
        startSurah: Int = 1,
        startAyah: Int = 1,
        endSurah: Int = startSurah,
        endAyah: Int = startAyah,
        description: String? = "",
        tags: List<String> = emptyList(),
        createdAt: Long = 1_700_000_000_000 + id,
    ) = V1Bookmark(id, type, startSurah, startAyah, endSurah, endAyah, description, tags, createdAt, createdAt + 5)

    private fun convert(data: V1Data): LegacyConversion =
        LegacyV1Converter.convert(data, index, app.clock.zone(), today, app.clock.now(), khatmName = "Khatm 1")

    private fun day(daysAgo: Long) = today.minusDays(daysAgo).toString()

    // ---- bookmarks ----

    @Test
    fun `an AYAH bookmark becomes a single-ayah bookmark`() {
        val b = convert(V1Data(bookmarks = listOf(v1Bookmark(15, "AYAH", 2, 255)))).data.bookmarks.single()

        assertEquals(BookmarkKind.AYAH, b.kind)
        assertEquals(AyahRange.single(app.ayah(2, 255)), b.range)
        assertEquals(15, b.id)
    }

    @Test
    fun `a RANGE bookmark keeps its start and end, also across surahs`() {
        val b = convert(V1Data(bookmarks = listOf(v1Bookmark(1, "RANGE", 2, 285, 3, 5)))).data.bookmarks.single()

        assertEquals(BookmarkKind.RANGE, b.kind)
        assertEquals(AyahRange(app.ayah(2, 285), app.ayah(3, 5)), b.range)
    }

    @Test
    fun `a SURAH bookmark covers the whole surah`() {
        val b = convert(V1Data(bookmarks = listOf(v1Bookmark(1, "SURAH", 18, 1, 18, 110)))).data.bookmarks.single()

        assertEquals(BookmarkKind.SURAH, b.kind)
        assertEquals(index.surahRange(18), b.range)
    }

    @Test
    fun `a PAGE bookmark takes its page number from startAyah`() {
        val b = convert(V1Data(bookmarks = listOf(v1Bookmark(1, "PAGE", startSurah = 18, startAyah = 293)))).data.bookmarks.single()

        assertEquals(BookmarkKind.PAGE, b.kind)
        assertEquals(index.pageRange(293), b.range)
        assertEquals(BookmarkTitle.Page(293), BookmarkTitles.of(b, index))
    }

    @Test
    fun `description and timestamps carry over, with no schedule, target one and no label`() {
        val b = convert(V1Data(bookmarks = listOf(v1Bookmark(1, "AYAH", 2, 255, description = "  after every prayer ", createdAt = 1_700_000_000_000)))).data.bookmarks.single()

        assertEquals("after every prayer", b.description)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), b.createdAt)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_005), b.updatedAt)
        assertNull(b.schedule)
        assertNull(b.label)
        assertEquals(1, b.repeatTarget)
    }

    @Test
    fun `an empty v1 description becomes no description`() {
        assertNull(convert(V1Data(bookmarks = listOf(v1Bookmark(1, "AYAH", 2, 255, description = "")))).data.bookmarks.single().description)
    }

    @Test
    fun `sort order follows creation time`() {
        val converted = convert(
            V1Data(
                bookmarks = listOf(
                    v1Bookmark(1, "AYAH", 2, 255, createdAt = 3000),
                    v1Bookmark(2, "AYAH", 2, 256, createdAt = 1000),
                    v1Bookmark(3, "AYAH", 2, 257, createdAt = 2000),
                ),
            ),
        ).data.bookmarks

        assertEquals(listOf(2L, 3L, 1L), converted.sortedBy { it.sortIndex }.map { it.id })
        assertEquals(listOf(0, 1, 2), converted.map { it.sortIndex }.sorted())
    }

    @Test
    fun `each distinct first tag becomes a group and the bookmark joins it`() {
        val data = convert(
            V1Data(
                bookmarks = listOf(
                    v1Bookmark(1, "AYAH", 2, 255, tags = listOf("Morning")),
                    v1Bookmark(2, "SURAH", 112, tags = listOf("Night")),
                    v1Bookmark(3, "SURAH", 113, tags = listOf("Morning")),
                    v1Bookmark(4, "SURAH", 114),
                ),
            ),
        ).data

        assertEquals(listOf("Morning", "Night"), data.groups.map { it.name })
        val morning = data.groups.first { it.name == "Morning" }.id
        val night = data.groups.first { it.name == "Night" }.id
        assertEquals(listOf(morning, night, morning, null), data.bookmarks.sortedBy { it.id }.map { it.groupId })
        assertTrue("groups carry no schedule: v1 had none", data.groups.all { it.schedule == null })
    }

    @Test
    fun `further tags are appended to the description so nothing is lost`() {
        val withDescription = convert(V1Data(bookmarks = listOf(v1Bookmark(1, "AYAH", 2, 255, description = "protection", tags = listOf("Night", "Daily", "Short")))))
        val b = withDescription.data.bookmarks.single()

        assertEquals("protection\nTags: Daily, Short", b.description)
        assertEquals("Night", withDescription.data.groups.single().name)

        val withoutDescription = convert(V1Data(bookmarks = listOf(v1Bookmark(1, "AYAH", 2, 255, description = "", tags = listOf("Night", "Daily")))))
        assertEquals("Tags: Daily", withoutDescription.data.bookmarks.single().description)
    }

    @Test
    fun `a bookmark v1 could not have displayed is skipped and counted, the rest carry over`() {
        val conversion = convert(
            V1Data(
                bookmarks = listOf(
                    v1Bookmark(1, "AYAH", 2, 255),
                    v1Bookmark(2, "AYAH", 2, 999),
                    v1Bookmark(3, "HIZB", 1, 1),
                    v1Bookmark(4, "RANGE", 3, 5, 2, 1),
                    v1Bookmark(5, "PAGE", startAyah = 700),
                    v1Bookmark(6, "SURAH", 115),
                ),
            ),
        )

        assertEquals(listOf(1L), conversion.data.bookmarks.map { it.id })
        assertEquals(5, conversion.report.skippedBookmarks)
        assertEquals(1, conversion.report.bookmarks)
    }

    // ---- reading activity ----

    @Test
    fun `each tracked id becomes one imported reading on that day at noon`() {
        val conversion = convert(
            V1Data(
                bookmarks = listOf(v1Bookmark(15, "RANGE", 2, 255, 2, 256)),
                activities = listOf(V1ReadingActivity(day(3), 2, listOf("15:2:255", "15:2:256"))),
            ),
        )
        val events = conversion.data.events

        assertEquals(listOf(app.ayah(2, 255), app.ayah(2, 256)), events.map { it.ayahId })
        assertTrue(events.all { it.day == today.minusDays(3) && it.method == ReadMethod.IMPORT && it.batchId == null })
        assertTrue("the reading belongs to the bookmark it was made in", events.all { it.context == ReadContext.Bookmark(15) })
        val noon = today.minusDays(3).atTime(LocalTime.NOON).atZone(app.clock.zone()).toInstant()
        assertTrue(events.all { it.readAt == noon })
        assertTrue(conversion.data.tallies.isEmpty())
    }

    @Test
    fun `khatm and random readings, which v1 could not tell apart, and readings of unknown bookmarks become browse readings`() {
        val events = convert(
            V1Data(
                bookmarks = listOf(v1Bookmark(15, "AYAH", 2, 255)),
                activities = listOf(V1ReadingActivity(day(1), 3, listOf("-1:1:1", "99:1:2", "15:2:255"))),
            ),
        ).data.events

        assertEquals(listOf(ReadContext.Browse, ReadContext.Browse, ReadContext.Bookmark(15)), events.map { it.context })
    }

    @Test
    fun `a total greater than the tracked ids leaves the difference as an imported tally`() {
        val conversion = convert(V1Data(activities = listOf(V1ReadingActivity(day(2), 50, listOf("-1:1:1", "-1:1:2", "-1:1:3")))))

        val tally = conversion.data.tallies.single()
        assertEquals(47, tally.count)
        assertEquals(today.minusDays(2), tally.day)
        assertEquals("imported", tally.note)
        assertEquals(3, conversion.data.events.size)
        assertEquals(47, conversion.report.talliedAyahs)
    }

    @Test
    fun `a total equal to or smaller than the tracked ids makes no tally`() {
        val equal = convert(V1Data(activities = listOf(V1ReadingActivity(day(2), 2, listOf("-1:1:1", "-1:1:2")))))
        val smaller = convert(V1Data(activities = listOf(V1ReadingActivity(day(2), 1, listOf("-1:1:1", "-1:1:2")))))

        assertTrue(equal.data.tallies.isEmpty())
        assertTrue(smaller.data.tallies.isEmpty())
        assertEquals("every tracked ayah is a reading that happened", 2, smaller.data.events.size)
    }

    @Test
    fun `malformed ids are skipped and counted, and the day's total is still preserved`() {
        val conversion = convert(
            V1Data(
                activities = listOf(
                    V1ReadingActivity(day(1), 6, listOf("-1:1:1", "garbage", "1:2", "-1:x:3", "-1:2:999", "-1:115:1")),
                ),
            ),
        )

        assertEquals(1, conversion.data.events.size)
        assertEquals(5, conversion.report.skippedAyahIds)
        assertEquals("what could not be identified is kept as a number", 5, conversion.data.tallies.single().count)
    }

    @Test
    fun `a day with a count but no tracked ids becomes a tally alone`() {
        val conversion = convert(V1Data(activities = listOf(V1ReadingActivity(day(4), 120, emptyList()))))

        assertTrue(conversion.data.events.isEmpty())
        assertEquals(120, conversion.data.tallies.single().count)
        assertEquals(1, conversion.report.days)
    }

    @Test
    fun `rows with an unreadable or future date are skipped and counted`() {
        val conversion = convert(
            V1Data(
                activities = listOf(
                    V1ReadingActivity("not-a-date", 10, listOf("-1:1:1")),
                    V1ReadingActivity(today.plusDays(1).toString(), 10, listOf("-1:1:1")),
                    V1ReadingActivity(day(0), 1, listOf("-1:1:1")),
                ),
            ),
        )

        assertEquals(2, conversion.report.skippedDays)
        assertEquals(1, conversion.data.events.size)
    }

    @Test
    fun `badge level is not carried, the rank is derived again from the total`() = runTest {
        // v1 stored a badge next to the count; v2 has nowhere to put one.
        val conversion = convert(V1Data(activities = listOf(V1ReadingActivity(day(1), 120, listOf("-1:1:1")))))
        app.backup.import(conversion.data, ImportMode.MERGE)

        assertEquals(Rank.QANIT, RankCalculator.rankFor(app.totals.total(today.minusDays(1))))
    }

    // ---- khatm ----

    @Test
    fun `khatm progress at page p becomes one khatm assumed covered through page p minus one`() {
        val conversion = convert(
            V1Data(
                activities = listOf(V1ReadingActivity(day(9), 5, emptyList()), V1ReadingActivity(day(2), 5, emptyList())),
                khatmLastPageRead = 200,
            ),
        )
        val khatm = conversion.data.khatms.single()

        assertEquals(index.pageRange(199).end.value, khatm.assumedCoveredUpTo)
        assertEquals("started on the earliest imported day", today.minusDays(9), khatm.startedDay)
        assertEquals("Khatm 1", khatm.name)
        assertNull(khatm.dailyPages)
        assertNull(khatm.completedAt)
        assertTrue(conversion.report.khatmCarried)
    }

    @Test
    fun `with no reading history the carried khatm starts today`() {
        assertEquals(today, convert(V1Data(khatmLastPageRead = 2)).data.khatms.single().startedDay)
    }

    @Test
    fun `a khatm still on page one, or no khatm row, carries nothing`() {
        assertTrue(convert(V1Data(khatmLastPageRead = 1)).data.khatms.isEmpty())
        assertTrue(convert(V1Data(khatmLastPageRead = null)).data.khatms.isEmpty())
        assertFalse(convert(V1Data()).report.khatmCarried)
    }

    @Test
    fun `the carried khatm resumes at the page v1 was on and adds nothing to any day`() = runTest {
        val conversion = convert(V1Data(khatmLastPageRead = 200))
        app.backup.import(conversion.data, ImportMode.MERGE)

        val khatm = app.khatmProgress.snapshot(app.khatmStore.khatms().single().id)!!
        assertEquals(index.pageRange(200).start, khatm.resume)
        assertTrue(app.totals.totals(null, null).isEmpty())
    }

    // ---- settings ----

    @Test
    fun `v1 settings map onto v2 preferences and unknown values fall back to defaults`() {
        val prefs = convert(
            V1Data(settings = V1Settings(reciterId = "ar.husary", bitrate = 128, reminderEnabled = true, reminderTime = LocalTime.of(6, 30), theme = "DARK", playbackSpeed = 1.5f, language = "fa")),
        ).data.prefs!!

        assertEquals("ar.husary", prefs.reciterId)
        assertEquals(128, prefs.audioQuality)
        assertTrue(prefs.reminderEnabled)
        assertEquals(LocalTime.of(6, 30), prefs.reminderTime)
        assertEquals(ThemeMode.DARK, prefs.theme)
        assertEquals(1.5f, prefs.playbackSpeed)
        assertEquals(AppLanguage.PERSIAN, prefs.language)

        val odd = convert(V1Data(settings = V1Settings(theme = "sepia", playbackSpeed = 9f, language = "de", reciterId = " "))).data.prefs!!
        assertEquals(ThemeMode.SYSTEM, odd.theme)
        assertEquals(1f, odd.playbackSpeed)
        assertEquals(AppLanguage.SYSTEM, odd.language)
        assertEquals("ar.alafasy", odd.reciterId)

        assertNull("a v1 backup file without settings leaves preferences alone", convert(V1Data()).data.prefs)
    }

    // ---- end to end ----

    @Test
    fun `after an upgrade bookmarks and day totals match what v1 showed`() = runTest {
        val v1 = V1Data(
            bookmarks = listOf(
                v1Bookmark(15, "AYAH", 2, 255, description = "Kursi", tags = listOf("Morning", "Daily")),
                v1Bookmark(16, "SURAH", 112, tags = listOf("Morning")),
                v1Bookmark(17, "PAGE", startAyah = 293),
            ),
            activities = listOf(
                V1ReadingActivity(day(2), 12, listOf("15:2:255", "16:112:1", "16:112:2", "16:112:3", "16:112:4", "-1:1:1", "-1:1:2")),
                V1ReadingActivity(day(1), 60, listOf("15:2:255", "bad")),
                V1ReadingActivity(day(0), 3, listOf("16:112:1", "16:112:2", "16:112:3")),
            ),
            khatmLastPageRead = 50,
        )
        val conversion = convert(v1)

        val result = app.backup.import(conversion.data, ImportMode.MERGE)

        assertEquals(
            "every day shows the total v1 showed",
            mapOf(today.minusDays(2) to 12, today.minusDays(1) to 60, today to 3),
            app.totals.totals(null, null),
        )
        assertEquals(3, result.bookmarksAdded)
        assertEquals(1, result.groupsAdded)

        val board = app.availability.board()
        val morning = board.groups.single()
        assertEquals("Morning", morning.group.name)
        assertEquals(2, morning.bookmarks.size)
        assertEquals(1, board.ungrouped.size)
        val ikhlas = board.all.single { it.bookmark.kind == BookmarkKind.SURAH }
        assertEquals("today's three ayahs of al-Ikhlas show in the bookmark", 3, ikhlas.progress.readings)

        with(conversion.report) {
            assertEquals(3, bookmarks)
            assertEquals(1, groups)
            assertEquals(11, events)
            assertEquals(5 + 59, talliedAyahs)
            assertEquals(3, days)
            assertEquals(1, skippedAyahIds)
            assertTrue(khatmCarried)
        }
    }
}
