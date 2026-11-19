package io.github.mehrdad_abdi.qintar.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mehrdad_abdi.qintar.data.prefs.DataStorePrefsStore
import io.github.mehrdad_abdi.qintar.data.quran.AssetQuranTextSource
import io.github.mehrdad_abdi.qintar.data.quran.QuranDao
import io.github.mehrdad_abdi.qintar.data.quran.QuranDatabase
import io.github.mehrdad_abdi.qintar.data.quran.QuranIndexLoader
import io.github.mehrdad_abdi.qintar.data.user.BookmarkDao
import io.github.mehrdad_abdi.qintar.data.user.KhatmDao
import io.github.mehrdad_abdi.qintar.data.user.QintarDatabase
import io.github.mehrdad_abdi.qintar.data.user.ReadLogDao
import io.github.mehrdad_abdi.qintar.data.user.RoomBookmarkStore
import io.github.mehrdad_abdi.qintar.data.user.RoomKhatmStore
import io.github.mehrdad_abdi.qintar.data.user.RoomReadLogStore
import io.github.mehrdad_abdi.qintar.data.user.RoomTransactionRunner
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Databases, DAOs, the preferences store and the Quran index. Everything is a singleton: there must be
 * exactly one connection pool per database file and one DataStore per preferences file.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun qintarDatabase(@ApplicationContext context: Context): QintarDatabase = QintarDatabase.create(context)

    @Provides
    fun readLogDao(db: QintarDatabase): ReadLogDao = db.readLogDao()

    @Provides
    fun khatmDao(db: QintarDatabase): KhatmDao = db.khatmDao()

    @Provides
    fun bookmarkDao(db: QintarDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    @Singleton
    fun quranDatabase(@ApplicationContext context: Context): QuranDatabase = QuranDatabase.create(context)

    @Provides
    fun quranDao(db: QuranDatabase): QuranDao = db.quranDao()

    /**
     * Blocks its first caller while 6,236 structural rows are read (once per process). Inject it as
     * `Provider<QuranIndex>` / `Lazy<QuranIndex>` and resolve it off the main thread where that matters.
     */
    @Provides
    @Singleton
    fun quranIndex(dao: QuranDao): QuranIndex = runBlocking { QuranIndexLoader.load(dao) }

    @Provides
    @Singleton
    fun prefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        DataStorePrefsStore.createDataStore(context)

    @Provides
    @Singleton
    fun prefsStore(dataStore: DataStore<Preferences>): PrefsStore = DataStorePrefsStore(dataStore)
}

/** The storage ports of `:domain`, bound to their Room / asset implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindings {
    @Binds
    abstract fun readLogStore(store: RoomReadLogStore): ReadLogStore

    @Binds
    abstract fun khatmStore(store: RoomKhatmStore): KhatmStore

    @Binds
    abstract fun bookmarkStore(store: RoomBookmarkStore): BookmarkStore

    @Binds
    abstract fun transactionRunner(runner: RoomTransactionRunner): TransactionRunner

    @Binds
    abstract fun legacyMigration(migrator: io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigrator): io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigration

    @Binds
    abstract fun legacyFiles(files: io.github.mehrdad_abdi.qintar.data.legacy.LegacyFiles): io.github.mehrdad_abdi.qintar.data.legacy.LegacyFileStore

    @Binds
    abstract fun quranTextSource(source: AssetQuranTextSource): QuranTextSource
}
