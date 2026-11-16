package io.github.mehrdad_abdi.qintar.data.legacy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mehrdad_abdi.qintar.data.quran.QuranDatabase
import io.github.mehrdad_abdi.qintar.data.quran.QuranIndexLoader
import io.github.mehrdad_abdi.qintar.data.user.QintarDatabase
import io.github.mehrdad_abdi.qintar.data.user.Stores
import io.github.mehrdad_abdi.qintar.data.user.roomStores
import io.github.mehrdad_abdi.qintar.domain.backup.BackupManager
import io.github.mehrdad_abdi.qintar.domain.backup.V1Bookmark
import io.github.mehrdad_abdi.qintar.domain.backup.V1ReadingActivity
import io.github.mehrdad_abdi.qintar.domain.backup.V1Settings
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.ayahId
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.DefaultReadingTracker
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.domain.testing.FakeDayClock
import io.github.mehrdad_abdi.qintar.domain.testing.FakePrefsStore
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-place upgrade from v1: a database file with v1's real schema (Room version 3, as Room created
 * it from v1's entities at tag `v1-final`) is carried into qintar.db.
 */
@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: QintarDatabase
    private lateinit var quranDb: QuranDatabase
    private lateinit var stores: Stores
    private lateinit var index: QuranIndex

    private val clock = FakeDayClock(LocalDateTime.of(2026, 11, 15, 9, 0))
    private val prefs = FakePrefsStore()
    private lateinit var totals: DayTotals
    private lateinit var khatmProgress: KhatmProgress
    private lateinit var migrator: LegacyMigrator
    private var indexLoads = 0

    private val v1File: File get() = context.getDatabasePath(LegacyV1Reader.DATABASE_NAME)

    @Before
    fun setUp() = runTest {
        db = QintarDatabase.inMemory(context)
        quranDb = QuranDatabase.create(context)
        stores = roomStores(db)
        index = QuranIndexLoader.load(quranDb.quranDao())
        val tracker = DefaultReadingTracker(stores.log, stores.khatms, clock, stores.tx)
        totals = DayTotals(stores.log, clock)
        khatmProgress = KhatmProgress(stores.log, stores.khatms, index, clock)
        val backup = BackupManager(stores.log, stores.khatms, stores.bookmarks, prefs, tracker, clock, stores.tx)
        migrator = LegacyMigrator(prefs, LegacyV1Reader(context), Provider { indexLoads++; index }, backup, clock)
    }

    @After
    fun tearDown() {
        db.close()
        quranDb.close()
    }

    // ---- the v1 fixture ----

    /** The statements Room 2.x generated for v1's four entities, plus its bookkeeping table. */
    private fun createV1Database(fill: (SQLiteDatabase) -> Unit) {
        v1File.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(v1File, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`startSurah` INTEGER NOT NULL, `startAyah` INTEGER NOT NULL, `endSurah` INTEGER NOT NULL, `endAyah` INTEGER NOT NULL, " +
                    "`description` TEXT NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            )
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_content` (`id` TEXT NOT NULL, `surah` INTEGER NOT NULL, `ayah` INTEGER NOT NULL, " +
                    "`audioPath` TEXT, `imagePath` TEXT, `metadata` TEXT, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_activity` (`date` TEXT NOT NULL, `totalAyahsRead` INTEGER NOT NULL, " +
                    "`trackedAyahIds` TEXT NOT NULL, `badgeLevel` TEXT NOT NULL, PRIMARY KEY(`date`))",
            )
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `khatm_progress` (`id` INTEGER NOT NULL, `lastPageRead` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            v1.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            v1.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1-identity-hash')")
            v1.version = 3
            fill(v1)
        }
    }

    private fun SQLiteDatabase.bookmark(id: Long, type: String, startSurah: Int, startAyah: Int, endSurah: Int, endAyah: Int, description: String, tags: String, createdAt: Long) {
        execSQL(
            "INSERT INTO bookmarks (id, type, startSurah, startAyah, endSurah, endAyah, description, tags, createdAt, updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(id, type, startSurah, startAyah, endSurah, endAyah, description, tags, createdAt, createdAt + 500),
        )
    }

    private fun SQLiteDatabase.activity(date: String, total: Int, trackedJson: String) {
        execSQL("INSERT INTO reading_activity (date, totalAyahsRead, trackedAyahIds, badgeLevel) VALUES (?,?,?,?)", arrayOf<Any>(date, total, trackedJson, "DHAKIR"))
    }

    private val day1: LocalDate = LocalDate.of(2025, 9, 30)
    private val day2: LocalDate = LocalDate.of(2025, 10, 1)
    private val day3: LocalDate = LocalDate.of(2025, 10, 2)

    /** A v1 user: four bookmarks (two share their first tag), three days of reading, a khatm on page 50. */
    private fun createTypicalV1Database() = createV1Database { v1 ->
        v1.bookmark(15, "RANGE", 2, 255, 2, 257, "Ayat al-Kursi", """["Daily","Protection","Night"]""", 1_727_700_000_000)
        v1.bookmark(16, "SURAH", 36, 1, 36, 1, "", """["Daily"]""", 1_727_700_100_000)
        v1.bookmark(17, "PAGE", 1, 293, 1, 293, "Kahf start", """["Friday"]""", 1_727_700_200_000)
        v1.bookmark(18, "AYAH", 1, 1, 1, 1, "", "[]", 1_727_700_300_000)
        // Tracked in a bookmark, in khatm/random (-1) and in a bookmark that was deleted since (9).
        v1.activity("2025-09-30", 5, """["15:2:255","15:2:256","15:2:257","-1:1:1","9:112:1"]""")
        // v1 counted 60 but kept only two ids: the other 58 must not be lost.
        v1.activity("2025-10-01", 60, """["16:36:1","16:36:2"]""")
        // One id names an ayah that does not exist, one is not an id at all.
        v1.activity("2025-10-02", 4, """["18:1:1","18:1:8","rubbish","-1:114:6"]""")
        v1.execSQL("INSERT INTO khatm_progress (id, lastPageRead, updatedAt) VALUES (1, 50, 1727700000000)")
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun v1Files(): Map<String, String> =
        v1File.parentFile!!.listFiles()!!.filter { it.name.startsWith(LegacyV1Reader.DATABASE_NAME) }.associate { it.name to sha256(it) }

    // ---- reading ----

    @Test
    fun aDeviceWithoutV1DataHasNothingToRead() = runTest {
        assertEquals(LegacyReadResult.NotPresent, LegacyV1Reader(context).read())
        assertFalse("looking must not create the file", v1File.exists())
    }

    @Test
    fun theReaderReturnsV1RowsAsTheyWereStored() = runTest {
        createTypicalV1Database()
        context.getSharedPreferences(LegacyV1Reader.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
            .putString("reciter_edition", "ar.husary")
            .putString("reciter_bitrate", "128")
            .putBoolean("notification_enabled", true)
            .putString("notification_time", "07:30")
            .putString("theme", "DARK")
            .putString("primary_color", "#006B7D")
            .putFloat("playback_speed", 1.5f)
            .putString("language", "fa")
            .commit()

        val data = (LegacyV1Reader(context).read() as LegacyReadResult.Found).data

        assertEquals(
            V1Bookmark(15, "RANGE", 2, 255, 2, 257, "Ayat al-Kursi", listOf("Daily", "Protection", "Night"), 1_727_700_000_000, 1_727_700_000_500),
            data.bookmarks.first(),
        )
        assertEquals(listOf(15L, 16L, 17L, 18L), data.bookmarks.map { it.id })
        assertEquals(V1ReadingActivity("2025-10-01", 60, listOf("16:36:1", "16:36:2")), data.activities[1])
        assertEquals(3, data.activities.size)
        assertEquals(50, data.khatmLastPageRead)
        assertEquals(V1Settings("ar.husary", 128, true, LocalTime.of(7, 30), "DARK", 1.5f, "fa"), data.settings)
    }

    @Test
    fun aV1DatabaseWithoutSettingsOrKhatmIsStillRead() = runTest {
        createV1Database { it.activity("2025-09-30", 2, "not json") }

        val data = (LegacyV1Reader(context).read() as LegacyReadResult.Found).data

        assertEquals(listOf(V1ReadingActivity("2025-09-30", 2, emptyList())), data.activities)
        assertEquals(emptyList<Any>(), data.bookmarks)
        assertNull(data.khatmLastPageRead)
        assertNull(data.settings)
    }

    @Test
    fun rowsStillInV1sWriteAheadLogAreRead() = runTest {
        createTypicalV1Database()
        // v1 ran Room in WAL mode; after its process is killed for the update, the newest rows can
        // still be in the -wal file. Simulated by a writer that has not checkpointed.
        val writer = SQLiteDatabase.openDatabase(
            v1File.path,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        )
        try {
            writer.activity("2025-10-03", 7, """["-1:2:1"]""")
            assertTrue("the fixture is not in WAL mode", File(v1File.path + "-wal").length() > 0)

            val data = (LegacyV1Reader(context).read() as LegacyReadResult.Found).data

            assertEquals(V1ReadingActivity("2025-10-03", 7, listOf("-1:2:1")), data.activities.last())
            assertEquals(4, data.activities.size)
        } finally {
            writer.close()
        }
    }

    @Test
    fun aFileThatIsNotADatabaseIsReportedNotThrown() = runTest {
        v1File.parentFile!!.mkdirs()
        v1File.writeText("this is not an SQLite file, however long it is. ".repeat(100))
        val before = sha256(v1File)

        val result = LegacyV1Reader(context).read()

        assertTrue("$result", result is LegacyReadResult.Unreadable)
        assertEquals(before, sha256(v1File))
    }

    // ---- migrating ----

    @Test
    fun aFreshInstallIsMarkedAsNeedingNoMigration() = runTest {
        assertEquals(LegacyMigrationOutcome.NotNeeded, migrator.migrate("Khatm"))

        assertEquals(LegacyMigrationState.NOT_NEEDED, prefs.current.legacyMigrationState)
        assertEquals(emptyList<Any>(), stores.log.allEvents())
        assertEquals("the index is not loaded for nothing", 0, indexLoads)
        assertEquals(LegacyMigrationOutcome.AlreadyHandled(LegacyMigrationState.NOT_NEEDED), migrator.migrate("Khatm"))
    }

    @Test
    fun everyDayTotalOfV1IsPreserved() = runTest {
        createTypicalV1Database()

        val outcome = migrator.migrate("Carried-over khatm") as LegacyMigrationOutcome.Migrated

        assertEquals(mapOf(day1 to 5, day2 to 60, day3 to 4), totals.totals(null, null))
        assertEquals(LegacyMigrationState.MIGRATED, prefs.current.legacyMigrationState)

        // 5 + 2 + 2 readable ids became events; 58 uncounted and 2 unreadable became tallies.
        assertEquals(9, stores.log.allEvents().size)
        assertEquals(mapOf(day2 to 58, day3 to 2), stores.log.allTallies().associate { it.day to it.count })
        assertTrue(stores.log.allEvents().all { it.method == ReadMethod.IMPORT && it.day == it.readAt.atZone(clock.zone()).toLocalDate() })
        assertEquals(9, outcome.report.events)
        assertEquals(60, outcome.report.talliedAyahs)
        assertEquals(2, outcome.report.skippedAyahIds)
        assertEquals(3, outcome.report.days)
        assertEquals(9, outcome.result.eventsAdded)
    }

    @Test
    fun bookmarksKeepTheirPassagesAndTagsBecomeGroups() = runTest {
        createTypicalV1Database()

        migrator.migrate("Khatm")

        val groups = stores.bookmarks.groups()
        assertEquals(listOf("Daily", "Friday"), groups.map { it.name })
        val (daily, friday) = groups.map { it.id }

        val bookmarks = stores.bookmarks.bookmarks().sortedBy { it.sortIndex }
        assertEquals(listOf(BookmarkKind.RANGE, BookmarkKind.SURAH, BookmarkKind.PAGE, BookmarkKind.AYAH), bookmarks.map { it.kind })
        assertEquals(
            listOf(AyahRange(index.ayahId(2, 255)!!, index.ayahId(2, 257)!!), index.surahRange(36), index.pageRange(293), AyahRange.of(1, 1)),
            bookmarks.map { it.range },
        )
        assertEquals(listOf(daily, daily, friday, null), bookmarks.map { it.groupId })
        assertEquals("Ayat al-Kursi\nTags: Protection, Night", bookmarks[0].description)
        assertNull(bookmarks[1].description)
        assertEquals(1_727_700_000_000, bookmarks[0].createdAt.toEpochMilli())
        assertTrue(bookmarks.all { it.schedule == null && it.repeatTarget == 1 })
    }

    @Test
    fun readingsStayAttachedToTheirBookmarkAndTheRestBecomeBrowsing() = runTest {
        createTypicalV1Database()

        migrator.migrate("Khatm")

        val kursi = stores.bookmarks.bookmarks().single { it.kind == BookmarkKind.RANGE }
        assertEquals(mapOf(index.ayahId(2, 255)!! to 1, index.ayahId(2, 256)!! to 1, index.ayahId(2, 257)!! to 1), stores.log.ayahCounts(ReadContext.Bookmark(kursi.id), day1, AyahRange.ALL))
        // -1 (khatm or random in v1) and the deleted bookmark 9.
        assertEquals(
            setOf(index.ayahId(1, 1)!!, index.ayahId(112, 1)!!, index.ayahId(114, 6)!!),
            stores.log.distinctAyahs(ReadContext.Browse, AyahRange.ALL),
        )
    }

    @Test
    fun theKhatmContinuesFromThePageV1WasOn() = runTest {
        createTypicalV1Database()

        val outcome = migrator.migrate("Carried-over khatm") as LegacyMigrationOutcome.Migrated

        assertTrue(outcome.report.khatmCarried)
        val khatm = stores.khatms.khatms().single()
        assertEquals("Carried-over khatm", khatm.name)
        assertEquals(day1, khatm.startedDay)
        assertEquals(index.pageRange(49).end.value, khatm.assumedCoveredUpTo)
        val snapshot = khatmProgress.snapshot(khatm.id)!!
        assertEquals(index.pageRange(50).start, snapshot.firstUnread)
        assertEquals(index.pageRange(49).end.value, snapshot.coveredAyahs)
    }

    @Test
    fun v1SettingsAreCarriedAndEverythingElseKeepsItsValue() = runTest {
        createTypicalV1Database()
        context.getSharedPreferences(LegacyV1Reader.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
            .putString("reciter_edition", "ar.husary").putString("reciter_bitrate", "128")
            .putBoolean("notification_enabled", true).putString("notification_time", "07:30")
            .putString("theme", "DARK").putFloat("playback_speed", 1.5f).putString("language", "fa")
            .commit()
        prefs.update { it.copy(quranFontSp = 40, onboardingDone = true) }

        migrator.migrate("Khatm")

        assertEquals(
            UserPrefs(
                reciterId = "ar.husary",
                audioQuality = 128,
                reminderEnabled = true,
                reminderTime = LocalTime.of(7, 30),
                theme = ThemeMode.DARK,
                playbackSpeed = 1.5f,
                language = AppLanguage.PERSIAN,
                quranFontSp = 40,
                onboardingDone = true,
                legacyMigrationState = LegacyMigrationState.MIGRATED,
            ),
            prefs.current,
        )
    }

    @Test
    fun theV1FilesAreLeftExactlyAsTheyWere() = runTest {
        createTypicalV1Database()
        val before = v1Files()
        val modified = v1File.lastModified()

        migrator.migrate("Khatm")

        assertEquals(before, v1Files())
        assertEquals(modified, v1File.lastModified())
        // And v1's own code could still open it: schema, version and rows are all there.
        SQLiteDatabase.openDatabase(v1File.path, null, SQLiteDatabase.OPEN_READONLY).use { v1 ->
            assertEquals(3, v1.version)
            v1.rawQuery("SELECT COUNT(*) FROM bookmarks", null).use { c ->
                c.moveToFirst()
                assertEquals(4, c.getInt(0))
            }
        }
    }

    @Test
    fun runningAgainAfterASuccessfulMigrationChangesNothing() = runTest {
        createTypicalV1Database()
        migrator.migrate("Khatm")
        val events = stores.log.allEvents()
        val tallies = stores.log.allTallies()
        val bookmarks = stores.bookmarks.bookmarks()

        assertEquals(LegacyMigrationOutcome.AlreadyHandled(LegacyMigrationState.MIGRATED), migrator.migrate("Khatm"))
        assertEquals(LegacyMigrationOutcome.AlreadyHandled(LegacyMigrationState.MIGRATED), migrator.migrate("Khatm", retryFailed = true))

        assertEquals(events, stores.log.allEvents())
        assertEquals(tallies, stores.log.allTallies())
        assertEquals(bookmarks, stores.bookmarks.bookmarks())
        assertEquals(1, stores.khatms.khatms().size)
        assertEquals(mapOf(day1 to 5, day2 to 60, day3 to 4), totals.totals(null, null))

        // Once the "carried over" card has been dismissed it stays dismissed.
        prefs.update { it.copy(legacyMigrationState = LegacyMigrationState.ACKNOWLEDGED) }
        assertEquals(LegacyMigrationOutcome.AlreadyHandled(LegacyMigrationState.ACKNOWLEDGED), migrator.migrate("Khatm"))
        assertEquals(events, stores.log.allEvents())
    }

    /** The process dies after the import committed but before `MIGRATED` was written. */
    private class Crash : Error("process died")

    private class DyingPrefs(private val inner: FakePrefsStore) : io.github.mehrdad_abdi.qintar.domain.port.PrefsStore by inner {
        var armed = true
        override suspend fun update(transform: (UserPrefs) -> UserPrefs): UserPrefs {
            val next = transform(inner.current)
            if (armed && next.legacyMigrationState == LegacyMigrationState.MIGRATED) {
                armed = false
                throw Crash()
            }
            return inner.update(transform)
        }
    }

    @Test
    fun aCrashAfterTheImportDoesNotDuplicateAnythingAtTheNextStart() = runTest {
        createTypicalV1Database()
        val dying = DyingPrefs(prefs)
        val backup = BackupManager(stores.log, stores.khatms, stores.bookmarks, dying, DefaultReadingTracker(stores.log, stores.khatms, clock, stores.tx), clock, stores.tx)
        val first = LegacyMigrator(dying, LegacyV1Reader(context), Provider { index }, backup, clock)

        try {
            first.migrate("Khatm")
            org.junit.Assert.fail("the simulated crash must escape")
        } catch (_: Crash) {
        }
        // What the dead process left behind: the data is in, the state still says "in progress".
        assertEquals(LegacyMigrationState.IN_PROGRESS, prefs.current.legacyMigrationState)
        assertEquals(4, stores.bookmarks.bookmarks().size)

        // The next launch.
        val outcome = migrator.migrate("Khatm")

        assertTrue(outcome is LegacyMigrationOutcome.Migrated)
        assertEquals(LegacyMigrationState.MIGRATED, prefs.current.legacyMigrationState)
        assertEquals(4, stores.bookmarks.bookmarks().size)
        assertEquals(2, stores.bookmarks.groups().size)
        assertEquals(1, stores.khatms.khatms().size)
        assertEquals(mapOf(day1 to 5, day2 to 60, day3 to 4), totals.totals(null, null))
        assertEquals(9, stores.log.allEvents().size)
        assertEquals(2, stores.log.allTallies().size)
    }

    @Test
    fun readingDoneInV2BeforeTheMigrationIsKept() = runTest {
        createTypicalV1Database()
        stores.log.insertEvents(
            listOf(io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent(index.ayahId(1, 1)!!, clock.today(), clock.now(), ReadContext.Browse, ReadMethod.TAP, null)),
        )

        migrator.migrate("Khatm")

        assertEquals(mapOf(day1 to 5, day2 to 60, day3 to 4, clock.today() to 1), totals.totals(null, null))
    }

    @Test
    fun anUnreadableV1FileFailsWithoutWritingAndCanBeRetried() = runTest {
        v1File.parentFile!!.mkdirs()
        v1File.writeText("garbage ".repeat(1000))

        assertTrue(migrator.migrate("Khatm") is LegacyMigrationOutcome.Failed)
        assertEquals(LegacyMigrationState.FAILED, prefs.current.legacyMigrationState)
        assertEquals(emptyList<Any>(), stores.log.allEvents())
        assertEquals(emptyList<Any>(), stores.bookmarks.bookmarks())

        // Not retried on its own at every launch…
        assertEquals(LegacyMigrationOutcome.AlreadyHandled(LegacyMigrationState.FAILED), migrator.migrate("Khatm"))

        // …but when asked to, and the file is readable this time, it goes through.
        v1File.delete()
        createTypicalV1Database()
        assertTrue(migrator.migrate("Khatm", retryFailed = true) is LegacyMigrationOutcome.Migrated)
        assertEquals(mapOf(day1 to 5, day2 to 60, day3 to 4), totals.totals(null, null))
    }

    @Test
    fun aV1DatabaseWithNothingInItMigratesToNothing() = runTest {
        createV1Database { }

        val outcome = migrator.migrate("Khatm") as LegacyMigrationOutcome.Migrated

        assertEquals(0, outcome.report.events + outcome.report.bookmarks + outcome.report.talliedAyahs)
        assertFalse(outcome.report.khatmCarried)
        assertEquals(LegacyMigrationState.MIGRATED, prefs.current.legacyMigrationState)
        assertEquals(emptyMap<LocalDate, Int>(), totals.totals(null, null))
    }
}
