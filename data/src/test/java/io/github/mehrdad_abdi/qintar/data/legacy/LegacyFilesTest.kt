package io.github.mehrdad_abdi.qintar.data.legacy

import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** §7.11: what is removed of v1, and when. */
class LegacyFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val databases by lazy { temp.newFolder("databases") }
    private val cache by lazy { temp.newFolder("cache") }
    private var settingsDeleted = false

    private fun files(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        LegacyFiles(databases, cache, { settingsDeleted = true; true }, StandardTestDispatcher(scheduler))

    private fun touch(dir: File, name: String) = File(dir, name).apply { parentFile!!.mkdirs(); writeText("x") }

    /** v1's files as found on a device, plus v2's own next to them. */
    private fun layOut() {
        listOf("quran.db", "quran.db-wal", "quran.db-shm").forEach { touch(databases, it) }
        listOf("quran_bookmarks_database", "quran_bookmarks_database-wal", "quran_bookmarks_database-shm").forEach { touch(databases, it) }
        listOf("qintar.db", "qintar.db-wal", "quran-text.db").forEach { touch(databases, it) }
        touch(cache, "quran_audio/2_255.mp3")
        touch(cache, "quran_images/2_255.png")
        touch(cache, "quran.db.lck")
    }

    @Test
    fun theRegenerableFilesGoAndTheUserDatabaseAndV2sOwnFilesStay() = runTest {
        layOut()

        files(testScheduler).deleteRegenerable()

        assertEquals(
            setOf("quran_bookmarks_database", "quran_bookmarks_database-wal", "quran_bookmarks_database-shm", "qintar.db", "qintar.db-wal", "quran-text.db"),
            databases.list()!!.toSet(),
        )
        assertEquals(emptySet<String>(), cache.list()!!.toSet())
        assertFalse(settingsDeleted)
        assertTrue(files(testScheduler).userDataPresent())

        // Again, with nothing left to delete.
        files(testScheduler).deleteRegenerable()
    }

    @Test
    fun removingTheUserDataTakesTheDatabaseWithItsJournalAndTheSettings() = runTest {
        layOut()

        assertTrue(files(testScheduler).deleteUserData())

        assertFalse(files(testScheduler).userDataPresent())
        assertTrue(settingsDeleted)
        assertEquals(setOf("quran.db", "quran.db-wal", "quran.db-shm", "qintar.db", "qintar.db-wal", "quran-text.db"), databases.list()!!.toSet())
    }
}
