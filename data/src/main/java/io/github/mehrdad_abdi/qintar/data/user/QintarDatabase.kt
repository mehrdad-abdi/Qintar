package io.github.mehrdad_abdi.qintar.data.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The user's data (DESIGN.md §7.5): the reading log, khatms, bookmarks and groups. File `qintar.db`,
 * separate from the bundled `quran.db`.
 *
 * This database must never be opened with a destructive-migration fallback: losing it loses the
 * user's history. Every version bump adds a [Migration] to [MIGRATIONS] and a migration test that
 * starts from the exported schema under `data/schemas/`.
 */
@Database(
    entities = [
        ReadEventEntity::class,
        ReadTallyEntity::class,
        KhatmEntity::class,
        BookmarkGroupEntity::class,
        BookmarkEntity::class,
    ],
    version = QintarDatabase.VERSION,
    exportSchema = true,
)
abstract class QintarDatabase : RoomDatabase() {

    abstract fun readLogDao(): ReadLogDao

    abstract fun khatmDao(): KhatmDao

    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "qintar.db"

        /** Every migration between released versions, oldest first. Empty while there is one version. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context, name: String = FILE_NAME): QintarDatabase =
            Room.databaseBuilder(context.applicationContext, QintarDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()

        /** For tests. Same schema and foreign keys, nothing on disk. */
        fun inMemory(context: Context): QintarDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, QintarDatabase::class.java).build()
    }
}
