package io.github.mehrdad_abdi.quranbookmarks.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.CachedContentDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.KhatmProgressDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.ReadingActivityDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.database.QuranBookmarksDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideQuranBookmarksDatabase(@ApplicationContext context: Context): QuranBookmarksDatabase {
        return QuranBookmarksDatabase.getDatabase(context)
    }

    @Provides
    fun provideBookmarkDao(database: QuranBookmarksDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    fun provideCachedContentDao(database: QuranBookmarksDatabase): CachedContentDao {
        return database.cachedContentDao()
    }

    @Provides
    fun provideReadingActivityDao(database: QuranBookmarksDatabase): ReadingActivityDao {
        return database.readingActivityDao()
    }

    @Provides
    fun provideKhatmProgressDao(database: QuranBookmarksDatabase): KhatmProgressDao {
        return database.khatmProgressDao()
    }
}