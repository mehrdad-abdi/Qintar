package io.github.mehrdad_abdi.quranbookmarks.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import io.github.mehrdad_abdi.quranbookmarks.data.local.converters.StringListConverter
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.CachedContentDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.ReadingActivityDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.BookmarkEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.CachedContentEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.ReadingActivityEntity

@Database(
    entities = [
        BookmarkEntity::class,
        CachedContentEntity::class,
        ReadingActivityEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class QuranBookmarksDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun cachedContentDao(): CachedContentDao
    abstract fun readingActivityDao(): ReadingActivityDao

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