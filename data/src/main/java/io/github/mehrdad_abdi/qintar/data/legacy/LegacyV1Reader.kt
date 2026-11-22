package io.github.mehrdad_abdi.qintar.data.legacy

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mehrdad_abdi.qintar.data.backup.BackupCodec
import io.github.mehrdad_abdi.qintar.domain.backup.V1Bookmark
import io.github.mehrdad_abdi.qintar.domain.backup.V1Data
import io.github.mehrdad_abdi.qintar.domain.backup.V1ReadingActivity
import io.github.mehrdad_abdi.qintar.domain.backup.V1Settings
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** What was found where v1 kept its data. */
sealed interface LegacyReadResult {
    /** No v1 database file: a fresh install, or v1 never ran. */
    data object NotPresent : LegacyReadResult

    data class Found(val data: V1Data) : LegacyReadResult

    /** The file exists but could not be read as a v1 database. */
    data class Unreadable(val cause: Exception) : LegacyReadResult
}

/**
 * Reads v1's data in place (§7.11): the Room file `databases/quran_bookmarks_database` (version 3),
 * opened read-only with plain SQLite, and the SharedPreferences file `app_settings`.
 *
 * It never writes: the v1 files are left exactly as found, so a failed or interrupted carry-over can
 * be repeated and the user's v1 data stays as a fallback.
 */
class LegacyV1Reader(
    private val databaseFile: File,
    private val readSettings: () -> Map<String, *>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        databaseFile = context.getDatabasePath(DATABASE_NAME),
        readSettings = { context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE).all },
    )

    suspend fun read(): LegacyReadResult = withContext(io) {
        if (!databaseFile.isFile) return@withContext LegacyReadResult.NotPresent
        try {
            val db = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
            val data = db.use {
                val tables = it.strings("SELECT name FROM sqlite_master WHERE type = 'table'").toSet()
                V1Data(
                    bookmarks = if ("bookmarks" in tables) readBookmarks(it) else emptyList(),
                    activities = if ("reading_activity" in tables) readActivities(it) else emptyList(),
                    khatmLastPageRead = if ("khatm_progress" in tables) readKhatmPage(it) else null,
                    settings = settings(),
                )
            }
            LegacyReadResult.Found(data)
        } catch (failure: Exception) {
            LegacyReadResult.Unreadable(failure)
        }
    }

    private fun readBookmarks(db: SQLiteDatabase): List<V1Bookmark> =
        db.rawQuery("SELECT * FROM bookmarks ORDER BY id", null).use { c ->
            val rows = ArrayList<V1Bookmark>(c.count)
            while (c.moveToNext()) {
                val startSurah = c.int("startSurah") ?: 0
                val startAyah = c.int("startAyah") ?: 1
                val createdAt = c.long("createdAt") ?: 0L
                rows += V1Bookmark(
                    id = c.long("id") ?: continue,
                    type = c.string("type").orEmpty(),
                    startSurah = startSurah,
                    startAyah = startAyah,
                    endSurah = c.int("endSurah") ?: startSurah,
                    endAyah = c.int("endAyah") ?: startAyah,
                    description = c.string("description"),
                    tags = stringList(c.string("tags")),
                    createdAt = createdAt,
                    updatedAt = c.long("updatedAt") ?: createdAt,
                )
            }
            rows
        }

    private fun readActivities(db: SQLiteDatabase): List<V1ReadingActivity> =
        db.rawQuery("SELECT * FROM reading_activity ORDER BY date", null).use { c ->
            val rows = ArrayList<V1ReadingActivity>(c.count)
            while (c.moveToNext()) {
                val tracked = stringList(c.string("trackedAyahIds"))
                rows += V1ReadingActivity(
                    date = c.string("date") ?: continue,
                    totalAyahsRead = c.int("totalAyahsRead") ?: tracked.size,
                    trackedAyahIds = tracked,
                )
            }
            rows
        }

    private fun readKhatmPage(db: SQLiteDatabase): Int? =
        db.rawQuery("SELECT * FROM khatm_progress ORDER BY id LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.int("lastPageRead") else null
        }

    /** v1 stored every setting under these keys; an empty file means v1 never saved one. */
    private fun settings(): V1Settings? {
        val all = try {
            readSettings()
        } catch (_: Exception) {
            return null
        }
        if (all.isEmpty()) return null
        return V1Settings(
            reciterId = all["reciter_edition"] as? String,
            bitrate = (all["reciter_bitrate"] as? String)?.trim()?.toIntOrNull(),
            reminderEnabled = all["notification_enabled"] as? Boolean,
            reminderTime = (all["notification_time"] as? String)?.let(BackupCodec::v1Time),
            theme = all["theme"] as? String,
            playbackSpeed = all["playback_speed"] as? Float,
            language = all["language"] as? String,
        )
    }

    companion object {
        const val DATABASE_NAME = "quran_bookmarks_database"
        const val SETTINGS_NAME = "app_settings"

        private val JSON = Json

        /** v1 stored string lists as a Gson JSON array; anything unreadable is an empty list. */
        internal fun stringList(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            val array = try {
                JSON.parseToJsonElement(json) as? JsonArray
            } catch (_: Exception) {
                null
            }
            return array?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }.orEmpty()
        }

        private fun SQLiteDatabase.strings(sql: String): List<String> = rawQuery(sql, null).use { c ->
            val out = ArrayList<String>()
            while (c.moveToNext()) out += c.getString(0)
            out
        }

        private fun Cursor.column(name: String): Int? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }

        private fun Cursor.int(name: String): Int? = column(name)?.let(::getInt)

        private fun Cursor.long(name: String): Long? = column(name)?.let(::getLong)

        private fun Cursor.string(name: String): String? = column(name)?.let(::getString)
    }
}
