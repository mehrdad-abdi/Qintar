package io.github.mehrdad_abdi.qintar.data.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 also kept a Room database named `quran.db` (version 1, another schema) in the app's database
 * directory. A device upgraded in place still has that file when v2 first opens its own Quran data.
 */
@RunWith(AndroidJUnit4::class)
class QuranDatabaseUpgradeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theBundledQuranOpensOnADeviceThatStillHasV1sQuranDatabase() = runTest {
        val v1Quran = context.getDatabasePath("quran.db")
        v1Quran.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(v1Quran, null).use { v1 ->
            // v1's entities (tag v1-final): other tables, other columns, same Room version number.
            v1.execSQL("CREATE TABLE `surahs` (`number` INTEGER NOT NULL, `name` TEXT NOT NULL, `englishName` TEXT NOT NULL, PRIMARY KEY(`number`))")
            v1.execSQL("CREATE TABLE `verses` (`globalNumber` INTEGER NOT NULL, `surahNumber` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`globalNumber`))")
            v1.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            v1.execSQL("INSERT INTO room_master_table (id,identity_hash) VALUES(42, 'the-hash-of-v1s-schema')")
            v1.version = 1
        }

        val db = QuranDatabase.create(context)
        try {
            assertEquals(114, db.quranDao().surahs().size)
            assertEquals(6236, db.quranDao().ayahStructures().size)
        } finally {
            db.close()
        }
    }
}
