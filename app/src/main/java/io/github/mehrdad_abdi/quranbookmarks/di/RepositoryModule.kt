package io.github.mehrdad_abdi.quranbookmarks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mehrdad_abdi.quranbookmarks.data.repository.BackupRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.BookmarkRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.KhatmProgressRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.QuranRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.ReadingActivityRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.SettingsRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.SurahRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BackupRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.KhatmProgressRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SurahRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        bookmarkRepositoryImpl: BookmarkRepositoryImpl
    ): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindQuranRepository(
        quranRepositoryImpl: QuranRepositoryImpl
    ): QuranRepository

    @Binds
    @Singleton
    abstract fun bindSurahRepository(
        surahRepositoryImpl: SurahRepositoryImpl
    ): SurahRepository

    @Binds
    @Singleton
    abstract fun bindReadingActivityRepository(
        readingActivityRepositoryImpl: ReadingActivityRepositoryImpl
    ): ReadingActivityRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(
        backupRepositoryImpl: BackupRepositoryImpl
    ): BackupRepository

    @Binds
    @Singleton
    abstract fun bindKhatmProgressRepository(
        khatmProgressRepositoryImpl: KhatmProgressRepositoryImpl
    ): KhatmProgressRepository
}