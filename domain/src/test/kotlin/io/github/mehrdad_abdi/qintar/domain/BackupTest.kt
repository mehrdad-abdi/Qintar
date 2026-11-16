package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.backup.BackupData
import io.github.mehrdad_abdi.qintar.domain.backup.BackupProblem
import io.github.mehrdad_abdi.qintar.domain.backup.ImportMode
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.service.StreakCalculator
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** §7.10: what an export carries, what an import refuses, and what Replace and Merge leave behind. */
class BackupTest {
    private val source = DomainFixture()
    private val today: LocalDate = source.clock.today()

    /** A device with a few days of history, a khatm, a group, two bookmarks and a tally. */
    private suspend fun populate(app: DomainFixture): Populated {
        app.clock.set(today.minusDays(2).atTime(9, 0))
        val khatm = app.khatms.start("Ramadan khatm", dailyPages = 4f)
        app.tracker.markRange(AyahRange.of(1, 30), ReadContext.Khatm(khatm.id), ReadMethod.BULK, RangeMode.FILL)
        val group = app.library.createGroup("Morning", description = "after fajr", schedule = Schedule.daily())
        val ikhlas = app.library.create(BookmarkDrafts.surah(112, app.index).copy(groupId = group.id, repeatTarget = 3, description = "a third of the Quran"))
        val kursi = app.library.create(BookmarkDrafts.ayah(app.ayah(2, 255)).copy(label = "Ayat al-Kursi"))

        app.clock.set(today.minusDays(1).atTime(9, 0))
        app.tracker.markRange(ikhlas.range, ReadContext.Bookmark(ikhlas.id), ReadMethod.BULK, RangeMode.ADD, times = 3)
        app.tracker.addTally(today.minusDays(1), 40, "mosque")

        app.clock.set(today.atTime(9, 0))
        app.tracker.mark(app.ayah(2, 255), ReadContext.Bookmark(kursi.id), ReadMethod.AUDIO)
        app.tracker.mark(app.ayah(36, 1), ReadContext.Browse, ReadMethod.TAP)
        return Populated(khatm.id, group.id, ikhlas.id, kursi.id)
    }

    private data class Populated(val khatm: Long, val group: Long, val ikhlas: Long, val kursi: Long)

    private suspend fun DomainFixture.allTotals() = totals.totals(null, null)

    // ---- export ----

    @Test
    fun `an export carries everything, and the summary describes it`() = runTest {
        populate(source)

        val data = source.backup.export(appVersionName = "2.0.0", appVersionCode = 200)
        val preview = source.backup.preview(data)

        assertEquals(BackupData.CURRENT_VERSION, data.version)
        assertEquals(source.clock.now(), data.exportedAt)
        assertEquals("2.0.0", data.appVersionName)
        assertTrue(preview.importable)
        with(preview.summary) {
            assertEquals(today.minusDays(2), firstDay)
            assertEquals(today, lastDay)
            assertEquals(30 + 12 + 2, events)
            assertEquals(1, tallies)
            assertEquals(30 + 12 + 2 + 40, totalAyahs)
            assertEquals(2, bookmarks)
            assertEquals(1, groups)
            assertEquals(1, khatms)
        }
    }

    // ---- replace ----

    @Test
    fun `an export re-imports into a clean install to the same totals, streak, khatm and bookmarks`() = runTest {
        val ids = populate(source)
        val data = source.backup.export()

        val target = DomainFixture()
        val result = target.backup.import(data, ImportMode.REPLACE)

        assertEquals(44, result.eventsAdded)
        assertEquals(source.allTotals(), target.allTotals())
        assertEquals(StreakCalculator.streak(source.allTotals(), today), StreakCalculator.streak(target.allTotals(), today))
        assertEquals(source.bookmarkStore.bookmarks(), target.bookmarkStore.bookmarks())
        assertEquals(source.bookmarkStore.groups(), target.bookmarkStore.groups())
        assertEquals(source.khatmStore.khatms(), target.khatmStore.khatms())

        val khatm = target.khatmProgress.snapshot(ids.khatm)!!
        assertEquals(30, khatm.coveredAyahs)
        assertEquals(AyahId(31), khatm.resume)
        assertEquals("the kursi bookmark still shows today's listening", 1,
            target.availability.board().state(ids.kursi)!!.progress.readings)
    }

    @Test
    fun `replace discards what was on the device`() = runTest {
        populate(source)
        val data = source.backup.export()

        val target = DomainFixture()
        target.tracker.addTally(today, 999, "will be replaced")
        target.library.create(BookmarkDrafts.surah(1, target.index))
        target.khatms.start("Old khatm")

        target.backup.import(data, ImportMode.REPLACE)

        assertEquals(source.allTotals(), target.allTotals())
        assertEquals(2, target.bookmarkStore.bookmarks().size)
        assertEquals(listOf("Ramadan khatm"), target.khatmStore.khatms().map { it.name })
    }

    @Test
    fun `replace restores the user's preferences but not this installation's own state`() = runTest {
        source.prefs.update { it.copy(goalRank = Rank.QANIT, quranFontSp = 36, onboardingDone = true, legacyMigrationState = LegacyMigrationState.ACKNOWLEDGED) }
        val data = source.backup.export()

        val target = DomainFixture()
        target.prefs.update { it.copy(legacyMigrationState = LegacyMigrationState.NOT_NEEDED, lastBrowseAyahId = AyahId(77)) }
        target.backup.import(data, ImportMode.REPLACE)

        val prefs = target.prefs.current
        assertEquals(Rank.QANIT, prefs.goalRank)
        assertEquals(36, prefs.quranFontSp)
        assertEquals(false, prefs.onboardingDone)
        assertEquals(LegacyMigrationState.NOT_NEEDED, prefs.legacyMigrationState)
        assertEquals(AyahId(77), prefs.lastBrowseAyahId)
    }

    // ---- merge ----

    @Test
    fun `merge adds the file's history to the device's without touching what is there`() = runTest {
        populate(source)
        val data = source.backup.export()

        val target = DomainFixture()
        val ownBookmark = target.library.create(BookmarkDrafts.surah(1, target.index))
        target.tracker.markRange(ownBookmark.range, ReadContext.Bookmark(ownBookmark.id), ReadMethod.BULK, RangeMode.ADD)
        val ownKhatm = target.khatms.start("My khatm")
        target.tracker.mark(AyahId(1), ReadContext.Khatm(ownKhatm.id), ReadMethod.TAP)

        val result = target.backup.import(data, ImportMode.MERGE)

        assertEquals(44, result.eventsAdded)
        assertEquals(0, result.eventsSkipped)
        assertEquals(source.allTotals().getValue(today) + 8, target.allTotals().getValue(today))
        assertEquals(3, target.bookmarkStore.bookmarks().size)
        assertEquals(setOf("My khatm", "Ramadan khatm"), target.khatmStore.khatms().map { it.name }.toSet())
        assertTrue("the device's own bookmark keeps its own readings", target.availability.board().state(ownBookmark.id)!!.progress.done)
        assertEquals(1, target.khatmProgress.snapshot(ownKhatm.id)!!.coveredAyahs)
    }

    @Test
    fun `merge re-maps ids so imported readings stay attached to their own bookmarks, groups and khatms`() = runTest {
        val ids = populate(source)
        val data = source.backup.export()

        val target = DomainFixture()
        // Occupy the ids the file uses, with unrelated content.
        val squatter = target.library.create(BookmarkDrafts.surah(1, target.index))
        val squatterKhatm = target.khatms.start("My khatm")
        assertEquals(ids.ikhlas, squatter.id)
        assertEquals(ids.khatm, squatterKhatm.id)

        target.backup.import(data, ImportMode.MERGE)

        val importedIkhlas = target.bookmarkStore.bookmarks().single { it.range == target.index.surahRange(112) }
        assertNotEquals(ids.ikhlas, importedIkhlas.id)
        assertEquals("Morning", target.bookmarkStore.group(importedIkhlas.groupId!!)!!.name)
        assertEquals(12, target.log.events.count { it.context == ReadContext.Bookmark(importedIkhlas.id) })
        assertEquals("nothing leaked into the bookmark that held that id", 0, target.log.events.count { it.context == ReadContext.Bookmark(squatter.id) })

        val importedKhatm = target.khatmStore.khatms().single { it.name == "Ramadan khatm" }
        assertEquals(30, target.khatmProgress.snapshot(importedKhatm.id)!!.coveredAyahs)
        assertEquals(0, target.khatmProgress.snapshot(squatterKhatm.id)!!.coveredAyahs)
    }

    @Test
    fun `merging the same file twice adds its readings once`() = runTest {
        populate(source)
        val data = source.backup.export()
        val target = DomainFixture()

        target.backup.import(data, ImportMode.MERGE)
        val second = target.backup.import(data, ImportMode.MERGE)

        assertEquals(0, second.eventsAdded)
        assertEquals(44, second.eventsSkipped)
        assertEquals(0, second.talliesAdded)
        assertEquals(source.allTotals(), target.allTotals())
    }

    @Test
    fun `merge keeps repeated readings that share a moment, such as three full readings logged at once`() = runTest {
        populate(source)
        val data = source.backup.export()
        val target = DomainFixture()

        target.backup.import(data, ImportMode.MERGE)

        // Yesterday held three full readings of al-Ikhlas written in one action, plus the tally.
        assertEquals(12 + 40, target.totals.total(today.minusDays(1)))
    }

    @Test
    fun `merging a device's own export back into it changes no total`() = runTest {
        populate(source)
        val before = source.allTotals()

        val result = source.backup.import(source.backup.export(), ImportMode.MERGE)

        assertEquals(0, result.eventsAdded)
        assertEquals(before, source.allTotals())
    }

    @Test
    fun `merge keeps the device's preferences`() = runTest {
        source.prefs.update { it.copy(quranFontSp = 40) }
        val data = source.backup.export()
        val target = DomainFixture()

        target.backup.import(data, ImportMode.MERGE)

        assertEquals(28, target.prefs.current.quranFontSp)
    }

    @Test
    fun `readings of a bookmark deleted before the export are imported and never attach to another bookmark`() = runTest {
        val gone = source.library.create(BookmarkDrafts.surah(112, source.index))
        source.tracker.markRange(gone.range, ReadContext.Bookmark(gone.id), ReadMethod.BULK, RangeMode.ADD)
        source.library.delete(gone.id)
        val data = source.backup.export()

        val target = DomainFixture()
        val unrelated = target.library.create(BookmarkDrafts.surah(1, target.index))
        assertEquals(gone.id, unrelated.id)

        target.backup.import(data, ImportMode.MERGE)

        assertEquals("the readings still count", 4, target.totals.today())
        assertEquals(0, target.availability.board().state(unrelated.id)!!.progress.readings)
    }

    // ---- validation ----

    private fun event(day: LocalDate) =
        NewReadEvent(AyahId(1), day, source.clock.now(), ReadContext.Browse, ReadMethod.TAP, null)

    private fun assertRejected(data: BackupData, expected: (BackupProblem) -> Boolean) {
        val problems = source.backup.preview(data).problems
        assertTrue("$problems", problems.any(expected))
        for (mode in ImportMode.entries) {
            val failure = assertThrows(DomainFailure.InvalidBackup::class.java) { runBlocking { source.backup.import(data, mode) } }
            assertEquals(problems, failure.problems)
        }
    }

    @Test
    fun `a file from a newer version of the app is refused`() = runTest {
        assertRejected(BackupData(version = 3, exportedAt = source.clock.now())) { it == BackupProblem.UnsupportedVersion(3) }
    }

    @Test
    fun `a file with readings dated in the future is refused`() = runTest {
        val data = BackupData(
            exportedAt = source.clock.now(),
            events = listOf(event(today), event(today.plusDays(1))),
            tallies = listOf(NewTally(today.plusDays(5), 10, null, source.clock.now())),
        )
        assertRejected(data) { it == BackupProblem.FutureDays(records = 2, latest = today.plusDays(5)) }
    }

    @Test
    fun `a file whose bookmark names a missing group is refused`() = runTest {
        populate(source)
        val data = source.backup.export().copy(groups = emptyList())
        assertRejected(data) { it is BackupProblem.UnknownGroup }
    }

    @Test
    fun `a file with duplicate ids or a non-positive tally is refused`() = runTest {
        populate(source)
        val exported = source.backup.export()

        assertRejected(exported.copy(bookmarks = exported.bookmarks + exported.bookmarks.first())) { it is BackupProblem.DuplicateId }
        assertRejected(exported.copy(tallies = listOf(NewTally(today, 0, null, source.clock.now())))) { it is BackupProblem.InvalidTally }
    }

    @Test
    fun `a failed import changes nothing`() = runTest {
        val target = DomainFixture()
        target.tracker.addTally(today, 25, "mine")
        val own = target.library.create(BookmarkDrafts.surah(1, target.index))
        populate(source)
        val bad = source.backup.export().let { it.copy(events = it.events + event(today.plusDays(1))) }

        for (mode in ImportMode.entries) {
            assertThrows(DomainFailure.InvalidBackup::class.java) { runBlocking { target.backup.import(bad, mode) } }
        }

        assertEquals(mapOf(today to 25), target.allTotals())
        assertEquals(listOf(own), target.bookmarkStore.bookmarks())
        assertTrue(target.khatmStore.khatms().isEmpty())
    }

    @Test
    fun `an import that fails half-way is rolled back completely`() = runTest {
        val target = DomainFixture()
        target.tracker.addTally(today, 25, "mine")
        val own = target.library.create(BookmarkDrafts.surah(1, target.index))
        populate(source)
        val exported = source.backup.export()

        // A valid file, but storage gives out after the device was emptied and most of the file written.
        val failing = FailingAfter(target, failOnTallyInsert = true)
        assertThrows(IllegalStateException::class.java) { runBlocking { failing.backup.import(exported, ImportMode.REPLACE) } }

        assertEquals("the device is exactly as it was", mapOf(today to 25), target.allTotals())
        assertEquals(listOf(own), target.bookmarkStore.bookmarks())
        assertNull(target.khatmStore.khatms().firstOrNull())
    }

    /** The target's services with a log store that fails when the import reaches its tallies. */
    private class FailingAfter(app: DomainFixture, failOnTallyInsert: Boolean) {
        private val failingLog = object : io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore by app.log {
            override suspend fun insertTally(tally: NewTally) =
                if (failOnTallyInsert) error("disk full") else app.log.insertTally(tally)
        }
        private val tracker = io.github.mehrdad_abdi.qintar.domain.service.DefaultReadingTracker(
            failingLog, app.khatmStore, app.clock, app.tx,
        )
        val backup = io.github.mehrdad_abdi.qintar.domain.backup.BackupManager(
            failingLog, app.khatmStore, app.bookmarkStore, app.prefs, tracker, app.clock, app.tx,
        )
    }
}
