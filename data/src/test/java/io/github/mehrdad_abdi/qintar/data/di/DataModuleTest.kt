package io.github.mehrdad_abdi.qintar.data.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mehrdad_abdi.qintar.data.quran.AssetQuranTextSource
import io.github.mehrdad_abdi.qintar.data.user.RoomBookmarkStore
import io.github.mehrdad_abdi.qintar.data.user.RoomKhatmStore
import io.github.mehrdad_abdi.qintar.data.user.RoomReadLogStore
import io.github.mehrdad_abdi.qintar.data.user.RoomTransactionRunner
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The objects the Hilt module hands out, built by calling its provider functions the way the generated
 * component does. (The component itself is assembled and checked when `:app` compiles.)
 */
@RunWith(AndroidJUnit4::class)
class DataModuleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theProvidedObjectsWorkTogetherOnTheRealFiles() = runTest {
        val db = DataModule.qintarDatabase(context)
        val quranDb = DataModule.quranDatabase(context)
        try {
            val index = DataModule.quranIndex(DataModule.quranDao(quranDb))
            assertEquals(Quran.AYAH_COUNT, index.surahs.sumOf { it.ayahCount })
            assertEquals(7, AssetQuranTextSource(DataModule.quranDao(quranDb)).page(1).size)

            val log = RoomReadLogStore(DataModule.readLogDao(db))
            val khatms = RoomKhatmStore(DataModule.khatmDao(db))
            val bookmarks = RoomBookmarkStore(DataModule.bookmarkDao(db))
            RoomTransactionRunner(db).inTransaction { log.insertTally(NewTally(LocalDate.of(2026, 11, 13), 3, null, Instant.EPOCH)) }
            assertEquals(1, log.allTallies().size)
            assertEquals(emptyList<Any>(), khatms.khatms())
            assertEquals(emptyList<Any>(), bookmarks.bookmarks())

            val prefs = DataModule.prefsStore(DataModule.prefsDataStore(context))
            assertEquals(ThemeMode.DARK, prefs.update { it.copy(theme = ThemeMode.DARK) }.theme)
            assertEquals(ThemeMode.DARK, prefs.prefs.first().theme)

            assertTrue(context.getDatabasePath("qintar.db").isFile)
            assertTrue(context.filesDir.resolve("datastore/user_prefs.preferences_pb").isFile)
        } finally {
            db.close()
            quranDb.close()
        }
    }
}
