package io.github.mehrdad_abdi.quranbookmarks.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.QuranDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.SurahEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.VerseEntity

@Database(entities = [SurahEntity::class, VerseEntity::class], version = 1, exportSchema = false)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun quranDao(): QuranDao

    companion object {
        @Volatile
        private var INSTANCE: QuranDatabase? = null

        fun getDatabase(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran.db"
                )
                    .createFromAsset("databases/quran.db")
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
