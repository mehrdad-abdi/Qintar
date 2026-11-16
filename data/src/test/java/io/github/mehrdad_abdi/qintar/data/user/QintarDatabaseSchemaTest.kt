package io.github.mehrdad_abdi.qintar.data.user

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The baseline for every future migration: version 1 as exported to `data/schemas/` is a database the
 * current code opens and reads. When version 2 arrives, its test starts from `helper.createDatabase(…, 1)`
 * exactly as [aPopulatedVersion1DatabaseOpensWithItsRowsIntact] does and adds `runMigrationsAndValidate`.
 */
@RunWith(AndroidJUnit4::class)
class QintarDatabaseSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), QintarDatabase::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "schema-test.db"

    private fun schemaFile(version: Int): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "data/schemas/${QintarDatabase::class.java.canonicalName}/$version.json") }
            .first { it.parentFile?.parentFile?.isDirectory == true }

    @Test
    fun theSchemaOfTheCurrentVersionIsExported() {
        val file = schemaFile(QintarDatabase.VERSION)
        assertTrue("missing ${file.path}: build :data and commit data/schemas", file.isFile)
        val json = file.readText()
        for (table in listOf("read_event", "read_tally", "khatm", "bookmark_group", "bookmark")) {
            assertTrue("table $table is not in the exported schema", json.contains("\"tableName\": \"$table\""))
        }
        for (index in listOf("read_event_day", "read_event_ayah", "read_event_context", "read_event_batch", "read_tally_day", "bookmark_group_id")) {
            assertTrue("index $index is not in the exported schema", json.contains("`$index`"))
        }
    }

    @Test
    fun everyReleasedVersionHasACommittedSchema() {
        for (version in 1..QintarDatabase.VERSION) assertTrue("no schema for version $version", schemaFile(version).isFile)
        assertEquals("one migration per version step", QintarDatabase.VERSION - 1, QintarDatabase.MIGRATIONS.size)
    }

    private fun SupportSQLiteDatabase.seedVersion1() {
        execSQL("INSERT INTO bookmark_group (id, name, description, schedule_days, window_from, window_to, sort_index, created_at) VALUES (2, 'Friday', NULL, 16, NULL, NULL, 0, 1000)")
        execSQL(
            "INSERT INTO bookmark (id, start_ayah_id, end_ayah_id, kind, label, description, repeat_target, group_id, schedule_days, " +
                "window_from, window_to, schedule_once, sort_index, created_at, updated_at) " +
                "VALUES (7, 2158, 2267, 'SURAH', NULL, 'Kahf', 1, 2, 0, NULL, NULL, 0, 3, 1000, 2000)",
        )
        execSQL("INSERT INTO khatm (id, name, started_day, daily_pages, assumed_covered_up_to, completed_at, created_at) VALUES (1, 'First', 20740, 4.0, 7, NULL, 1000)")
        val day = LocalDate.of(2026, 11, 13).toEpochDay()
        execSQL("INSERT INTO read_event (ayah_id, day, read_at, context_type, context_id, method, batch_id) VALUES (2158, $day, 1794686400000, 'BOOKMARK', 7, 'TAP', NULL)")
        execSQL("INSERT INTO read_event (ayah_id, day, read_at, context_type, context_id, method, batch_id) VALUES (8, $day, 1794686400001, 'KHATM', 1, 'BULK', 'b1')")
        execSQL("INSERT INTO read_event (ayah_id, day, read_at, context_type, context_id, method, batch_id) VALUES (255, $day, 1794686400002, 'BROWSE', NULL, 'AUDIO', NULL)")
        execSQL("INSERT INTO read_tally (day, count, note, created_at) VALUES ($day, 40, 'mosque', 1000)")
    }

    @Test
    fun aPopulatedVersion1DatabaseOpensWithItsRowsIntact() = runTest {
        // Built from the exported JSON alone, not from the entity classes.
        helper.createDatabase(name, 1).use { it.seedVersion1() }

        val db = QintarDatabase.create(context, name)
        try {
            val stores = roomStores(db)
            val day = LocalDate.of(2026, 11, 13)

            assertEquals(3, stores.log.allEvents().size)
            assertEquals(mapOf(day to 3), stores.log.observeEventCountsByDay(null, null).first())
            assertEquals(mapOf(day to 40), stores.log.observeTallySumsByDay(null, null).first())
            assertEquals(1, stores.log.distinctAyahCount(ReadContext.Bookmark(7), AyahRange.ALL))
            assertEquals(ReadContext.Browse, stores.log.allEvents().last().context)

            val bookmark = stores.bookmarks.bookmark(7)!!
            assertEquals(AyahRange.of(2158, 2267), bookmark.range)
            assertEquals(2L, bookmark.groupId)
            assertNull(bookmark.schedule)
            assertEquals(setOf(java.time.DayOfWeek.FRIDAY), stores.bookmarks.group(2)!!.schedule!!.days)

            val khatm = stores.khatms.khatm(1)!!
            assertEquals(4.0f, khatm.dailyPages)
            assertEquals(7, khatm.assumedCoveredUpTo)
        } finally {
            db.close()
        }
    }

    @Test
    fun theDatabaseEnforcesItsForeignKeyAndNullsItOnGroupDelete() = runTest {
        helper.createDatabase(name, 1).use { it.seedVersion1() }

        // Through the production builder: foreign keys are a per-connection setting that Room turns on.
        val db = QintarDatabase.create(context, name)
        try {
            val raw = db.openHelper.writableDatabase
            try {
                raw.execSQL(
                    "INSERT INTO bookmark (start_ayah_id, end_ayah_id, kind, repeat_target, group_id, schedule_days, schedule_once, sort_index, created_at, updated_at) " +
                        "VALUES (1, 7, 'RANGE', 1, 999, 0, 0, 0, 0, 0)",
                )
                fail("a bookmark in a missing group was stored")
            } catch (_: SQLiteConstraintException) {
            }
            raw.execSQL("DELETE FROM bookmark_group WHERE id = 2")
            raw.query("SELECT group_id FROM bookmark WHERE id = 7").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue("group_id was not nulled", c.isNull(0))
            }
            // Events keep the bookmark's id whatever happens to the bookmark: no foreign key there.
            raw.execSQL("DELETE FROM bookmark WHERE id = 7")
            raw.query("SELECT COUNT(*) FROM read_event WHERE context_type = 'BOOKMARK' AND context_id = 7").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun theProductionDatabaseIsAFileNamedQintarDbThatSurvivesReopening() = runTest {
        val first = QintarDatabase.create(context)
        roomStores(first).log.insertTally(io.github.mehrdad_abdi.qintar.domain.model.NewTally(LocalDate.of(2026, 11, 13), 12, null, java.time.Instant.EPOCH))
        first.close()

        assertTrue(context.getDatabasePath("qintar.db").isFile)
        assertFalse("user data must not share the Quran database file", QintarDatabase.FILE_NAME == io.github.mehrdad_abdi.qintar.data.quran.QuranDatabase.FILE_NAME)

        val second = QintarDatabase.create(context)
        try {
            assertEquals(listOf(12), roomStores(second).log.allTallies().map { it.count })
        } finally {
            second.close()
        }
    }
}
