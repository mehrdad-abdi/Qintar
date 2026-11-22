package io.github.mehrdad_abdi.qintar.data.legacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What v1 left on the device, and its removal (§7.11). */
interface LegacyFileStore {
    /** v1's own database (bookmarks, readings, khatm page) is still on the device. */
    suspend fun userDataPresent(): Boolean

    /**
     * Deletes what v1 can regenerate and v2 never reads: its copy of the Quran text and its audio
     * and image caches. Safe to call at every start. The v1 user database is left alone.
     */
    suspend fun deleteRegenerable()

    /** Deletes v1's user database and settings file. Only on the user's request. */
    suspend fun deleteUserData(): Boolean
}

/**
 * The files of v1 (v1-final): `databases/quran.db` (its bundled Quran text), `cache/quran_audio`
 * and `cache/quran_images` (downloaded recitation and ayah images), and the user's data in
 * `databases/quran_bookmarks_database` and `shared_prefs/app_settings.xml`.
 */
class LegacyFiles(
    private val databasesDir: File,
    private val cacheDir: File,
    private val deleteSettings: () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LegacyFileStore {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        databasesDir = context.getDatabasePath(LegacyV1Reader.DATABASE_NAME).parentFile!!,
        cacheDir = context.cacheDir,
        deleteSettings = { context.deleteSharedPreferences(LegacyV1Reader.SETTINGS_NAME) },
    )

    override suspend fun userDataPresent(): Boolean = withContext(io) {
        File(databasesDir, LegacyV1Reader.DATABASE_NAME).isFile
    }

    override suspend fun deleteRegenerable() = withContext(io) {
        sqliteFiles(QURAN_DATABASE).forEach { it.delete() }
        CACHE_DIRECTORIES.forEach { File(cacheDir, it).deleteRecursively() }
        // The lock files Room 2.6 kept next to the cache for each database.
        LOCK_FILES.forEach { File(cacheDir, it).delete() }
    }

    override suspend fun deleteUserData(): Boolean = withContext(io) {
        val files = sqliteFiles(LegacyV1Reader.DATABASE_NAME)
        files.forEach { it.delete() }
        runCatching { deleteSettings() }
        files.none { it.exists() }
    }

    private fun sqliteFiles(name: String) = SQLITE_SUFFIXES.map { File(databasesDir, name + it) }

    companion object {
        /** v1's prepopulated Quran text; v2 has its own (`quran-text.db`). */
        const val QURAN_DATABASE = "quran.db"
        private val SQLITE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")
        private val CACHE_DIRECTORIES = listOf("quran_audio", "quran_images")
        private val LOCK_FILES = listOf("quran.db.lck", "${LegacyV1Reader.DATABASE_NAME}.lck")
    }
}
