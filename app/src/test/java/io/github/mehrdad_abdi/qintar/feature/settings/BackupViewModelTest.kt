package io.github.mehrdad_abdi.qintar.feature.settings

import io.github.mehrdad_abdi.qintar.data.backup.BackupDecodeResult.Invalid.Reason
import io.github.mehrdad_abdi.qintar.domain.backup.BackupProblem
import io.github.mehrdad_abdi.qintar.domain.backup.ImportMode
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.backupViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Backup and restore through the view model: export, file choice, preview, then (only then) the write. */
class BackupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture() // Sunday 15 Nov 2026
    private val today = app.clock.today()

    private fun vm(of: DomainFixture = app) = of.backupViewModel(Dispatchers.Unconfined)

    /** History with a khatm, a bookmark in a group and three days of reading. */
    private fun seed(fixture: DomainFixture = app) = runBlocking {
        val khatm = fixture.khatms.start("Ramadan")
        val group = fixture.library.createGroup(name = "Friday")
        val bookmark = fixture.library.create(BookmarkDrafts.ayah(fixture.ayah(36, 1)).copy(groupId = group.id))
        fixture.tracker.markRange(AyahRange.of(1, 20), ReadContext.Khatm(khatm.id), ReadMethod.TAP, RangeMode.ADD, day = today)
        fixture.tracker.markRange(AyahRange.of(100, 130), ReadContext.Bookmark(bookmark.id), ReadMethod.TAP, RangeMode.ADD, day = today.minusDays(1))
        fixture.tracker.addTally(today.minusDays(2), 25, "Tarawih")
        fixture.prefs.update { it.copy(goalRank = Rank.QANIT, theme = ThemeMode.DARK) }
    }

    private fun exportText(of: DomainFixture = app): String {
        var text: String? = null
        vm(of).export { text = it }
        return checkNotNull(text) { "nothing was written" }
    }

    private fun choose(vm: io.github.mehrdad_abdi.qintar.feature.settings.BackupViewModel, text: String) = vm.chooseFile("Carried khatm") { text }

    private fun counts(f: DomainFixture) = runBlocking {
        Triple(f.log.allEvents().size, f.bookmarkStore.bookmarks().size, f.khatmStore.khatms().size)
    }

    // ---- export ---------------------------------------------------------------------------------------

    @Test
    fun `the suggested name carries the date`() {
        assertEquals("qintar-backup-2026-11-15.json", vm().suggestedFileName())
    }

    @Test
    fun `export writes the file's text and reports it saved`() {
        seed()
        val vm = vm()
        var written: String? = null
        vm.export { written = it }

        assertEquals(BackupPhase.Exported, vm.phase.value)
        assertTrue(written!!.contains("\"qintar-backup\""))
    }

    @Test
    fun `a write that fails is reported and nothing claims success`() {
        val vm = vm()
        vm.export { throw java.io.IOException("disk full") }
        assertEquals(BackupPhase.Failed(BackupError.ExportFailed), vm.phase.value)
    }

    // ---- preview comes first ------------------------------------------------------------------------

    @Test
    fun `choosing a file shows a preview and writes nothing`() {
        seed()
        val text = exportText()
        val other = DomainFixture()
        val vm = vm(other)

        choose(vm, text)

        val preview = (vm.phase.value as BackupPhase.Preview)
        assertEquals(Triple(0, 0, 0), counts(other))
        assertEquals(today.minusDays(2), preview.preview.summary.firstDay)
        assertEquals(today, preview.preview.summary.lastDay)
        assertEquals(51 + 25, preview.preview.summary.totalAyahs)
        assertEquals(1, preview.preview.summary.bookmarks)
        assertEquals(1, preview.preview.summary.groups)
        assertEquals(1, preview.preview.summary.khatms)
        assertTrue(!preview.fromV1)
    }

    @Test
    fun `cancelling the preview leaves the phone as it was`() {
        seed()
        val other = DomainFixture()
        val vm = vm(other)
        choose(vm, exportText())

        vm.dismiss()

        assertEquals(BackupPhase.Idle, vm.phase.value)
        assertEquals(Triple(0, 0, 0), counts(other))
        // With the preview gone there is nothing left to confirm.
        vm.confirm(ImportMode.REPLACE)
        assertEquals(Triple(0, 0, 0), counts(other))
    }

    @Test
    fun `confirming without a preview does nothing`() {
        val vm = vm()
        vm.confirm(ImportMode.REPLACE)
        assertEquals(BackupPhase.Idle, vm.phase.value)
    }

    // ---- round trips ----------------------------------------------------------------------------------

    @Test
    fun `export then replace on a fresh phone restores everything`() {
        seed()
        val text = exportText()
        val fresh = DomainFixture()
        val vm = vm(fresh)

        choose(vm, text)
        vm.confirm(ImportMode.REPLACE)

        val done = vm.phase.value as BackupPhase.Imported
        assertEquals(ImportMode.REPLACE, done.mode)
        assertEquals(51, done.result.eventsAdded)
        assertEquals(counts(app), counts(fresh))
        runBlocking {
            assertEquals(app.totals.totals(null, null), fresh.totals.totals(null, null))
            assertEquals(app.bookmarkStore.groups().map { it.name }, fresh.bookmarkStore.groups().map { it.name })
        }
        assertEquals(Rank.QANIT, fresh.prefs.current.goalRank)
        assertEquals(ThemeMode.DARK, fresh.prefs.current.theme)
    }

    @Test
    fun `replace removes what the phone had before`() {
        seed()
        val text = exportText()
        val other = DomainFixture()
        runBlocking { other.tracker.markRange(AyahRange.of(500, 520), ReadContext.Browse, ReadMethod.TAP, RangeMode.ADD, day = today) }

        val vm = vm(other)
        choose(vm, text)
        vm.confirm(ImportMode.REPLACE)

        assertEquals(counts(app), counts(other))
        assertEquals(0, runBlocking { other.log.allEvents().count { it.ayahId.value in 500..520 } })
    }

    @Test
    fun `merging the same file again adds no readings twice`() {
        seed()
        val text = exportText()
        val before = counts(app)
        val totalsBefore = runBlocking { app.totals.totals(null, null) }

        val vm = vm()
        choose(vm, text)
        vm.confirm(ImportMode.MERGE)

        val done = vm.phase.value as BackupPhase.Imported
        assertEquals(0, done.result.eventsAdded)
        assertEquals(51, done.result.eventsSkipped)
        assertEquals(0, done.result.talliesAdded)
        assertEquals(before.first, counts(app).first)
        assertEquals(totalsBefore, runBlocking { app.totals.totals(null, null) })
    }

    @Test
    fun `merging into a phone with its own readings keeps both`() {
        seed()
        val text = exportText()
        val other = DomainFixture()
        runBlocking { other.tracker.markRange(AyahRange.of(500, 504), ReadContext.Browse, ReadMethod.TAP, RangeMode.ADD, day = today) }

        val vm = vm(other)
        choose(vm, text)
        vm.confirm(ImportMode.MERGE)

        assertEquals(5 + 51, counts(other).first)
        assertEquals(1, counts(other).second)
        assertEquals(1, counts(other).third)
    }

    // ---- unusable files ---------------------------------------------------------------------------------

    private fun failure(text: String): BackupError {
        val vm = vm(DomainFixture())
        choose(vm, text)
        return (vm.phase.value as BackupPhase.Failed).error
    }

    @Test
    fun `a file that is not JSON says so`() {
        assertEquals(Reason.NOT_JSON, (failure("this is not json {") as BackupError.Invalid).reason)
    }

    @Test
    fun `JSON that is not a backup says so`() {
        assertEquals(Reason.NOT_A_BACKUP, (failure("""{"hello":"world"}""") as BackupError.Invalid).reason)
    }

    @Test
    fun `a backup from a newer app names its version`() {
        val text = exportText().replace("\"version\": 2", "\"version\": 9").replace("\"version\":2", "\"version\":9")
        val error = failure(text) as BackupError.Invalid
        assertEquals(Reason.NEWER_VERSION, error.reason)
        assertEquals("9", error.detail)
    }

    @Test
    fun `a damaged backup is refused with its reason`() {
        seed()
        val text = exportText().replace("\"columns\"", "\"colz\"")
        val error = failure(text)
        assertTrue(error.toString(), error is BackupError.Invalid && error.reason == Reason.MALFORMED)
    }

    @Test
    fun `a backup that is consistent in form but not in content lists the problems`() {
        seed()
        val tomorrow = today.plusDays(3)
        val text = exportText().replace(today.toString(), tomorrow.toString())
        val error = failure(text) as BackupError.Rejected
        assertTrue(error.problems.any { it is BackupProblem.FutureDays })
    }

    @Test
    fun `a file that cannot be read is reported and nothing is previewed`() {
        val vm = vm()
        vm.chooseFile("Khatm") { throw java.io.IOException("revoked") }
        assertEquals(BackupPhase.Failed(BackupError.Unreadable), vm.phase.value)
    }

    @Test
    fun `an error closes without effect`() {
        val vm = vm()
        choose(vm, "garbage")
        vm.dismiss()
        assertEquals(BackupPhase.Idle, vm.phase.value)
    }

    // ---- a v1 file --------------------------------------------------------------------------------------

    private val v1File = """
        {"metadata":{"version":"1.0","timestamp":"2025-10-05T21:14:09.123","appVersion":"1.4.2"},
         "bookmarks":[{"id":15,"type":"RANGE","startSurah":2,"startAyah":255,"endSurah":2,"endAyah":257,
                       "description":"Kursi","tags":["Daily"],"createdAt":1727700000000,"updatedAt":1727700500000}],
         "readingActivity":[{"date":"2025-09-30","totalAyahsRead":5,"trackedAyahIds":["15:2:255","15:2:256","-1:1:1"]}]}
    """.trimIndent()

    @Test
    fun `a v1 backup is recognised, converted, previewed and only merged`() {
        val other = DomainFixture()
        runBlocking { other.tracker.markRange(AyahRange.of(500, 504), ReadContext.Browse, ReadMethod.TAP, RangeMode.ADD, day = today) }
        val vm = vm(other)

        choose(vm, v1File)

        val preview = vm.phase.value as BackupPhase.Preview
        assertTrue(preview.fromV1)
        assertNotNull(preview.legacy)
        assertEquals(1, preview.preview.summary.bookmarks)
        assertEquals(1, preview.preview.summary.groups)
        assertEquals(5, preview.preview.summary.totalAyahs)
        assertEquals(LocalDate.of(2025, 9, 30), preview.preview.summary.firstDay)
        assertEquals("nothing written before the choice", 5, counts(other).first)

        // Even if Replace is asked for, a v1 file is merged: the phone's own data survives.
        vm.confirm(ImportMode.REPLACE)

        val done = vm.phase.value as BackupPhase.Imported
        assertEquals(ImportMode.MERGE, done.mode)
        assertEquals(3, done.result.eventsAdded)
        assertEquals(1, done.result.talliesAdded)
        assertEquals(5 + 3, counts(other).first)
        assertEquals(1, counts(other).second)
        assertEquals(5 + 5, runBlocking { other.totals.totals(null, null).values.sum() })
    }

    @Test
    fun `the same v1 file merged twice does not double its readings`() {
        val vm = vm()
        choose(vm, v1File)
        vm.confirm(ImportMode.MERGE)
        val after = counts(app)
        val total = runBlocking { app.totals.totals(null, null).values.sum() }

        choose(vm, v1File)
        vm.confirm(ImportMode.MERGE)

        assertEquals(after.first, counts(app).first)
        assertEquals(total, runBlocking { app.totals.totals(null, null).values.sum() })
    }
}
