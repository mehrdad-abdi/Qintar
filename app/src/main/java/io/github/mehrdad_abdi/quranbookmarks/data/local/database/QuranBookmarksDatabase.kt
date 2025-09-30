package io.github.mehrdad_abdi.quranbookmarks.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkGroupDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.CachedContentDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.BookmarkEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.BookmarkGroupEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.CachedContentEntity

@Database(
    entities = [
        BookmarkGroupEntity::class,
        BookmarkEntity::class,
        CachedContentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class QuranBookmarksDatabase : RoomDatabase() {

    abstract fun bookmarkGroupDao(): BookmarkGroupDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun cachedContentDao(): CachedContentDao

    companion object {
        @Volatile
        private var INSTANCE: QuranBookmarksDatabase? = null

        fun getDatabase(context: Context): QuranBookmarksDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranBookmarksDatabase::class.java,
                    "quran_bookmarks_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}