package io.github.mehrdad_abdi.quranbookmarks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mehrdad_abdi.quranbookmarks.data.repository.BookmarkRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.QuranRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.data.repository.SurahRepositoryImpl
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
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
}