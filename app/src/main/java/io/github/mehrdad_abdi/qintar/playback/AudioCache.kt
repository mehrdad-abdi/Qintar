@file:OptIn(UnstableApi::class)

package io.github.mehrdad_abdi.qintar.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The stored audio as Settings sees it. */
interface AudioStorage {
    suspend fun sizeBytes(): Long

    /** Removes every stored recitation. */
    suspend fun clear()
}

/** Brings one audio file into the cache. */
interface AudioFetcher {
    /** Returns when [url] is fully stored (at once if it already was). Throws when it cannot be loaded. */
    suspend fun fetch(url: String)
}

/** The connection, as far as downloads and error messages care. */
interface NetworkStatus {
    fun isOnline(): Boolean

    /** Wi-Fi or another connection that is not metered. */
    fun isUnmetered(): Boolean
}

/**
 * The one audio cache of the app (R-AUD-3): whatever is streamed is kept, least recently used
 * first out above [MAX_BYTES], so that a passage heard once plays again offline. Downloads for
 * offline go into the same cache.
 */
@Singleton
class Media3AudioCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AudioStorage, AudioFetcher {
    // Created on first use, off the main thread wherever the caller allows: opening reads the index.
    private val cache: SimpleCache by lazy {
        SimpleCache(File(context.noBackupFilesDir, DIRECTORY), LeastRecentlyUsedCacheEvictor(MAX_BYTES), StandaloneDatabaseProvider(context))
    }

    // Nothing of the user goes out: a fixed agent, no cookies, no extra headers.
    private val http = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT).setAllowCrossProtocolRedirects(false)

    /** Reads from the cache, loads what is missing from the network and keeps it. */
    fun dataSourceFactory(): DataSource.Factory = cacheDataSourceFactory()

    private fun cacheDataSourceFactory() = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(http)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    override suspend fun sizeBytes(): Long = withContext(io) { cache.cacheSpace }

    override suspend fun clear() = withContext(io) {
        for (key in cache.keys.toList()) cache.removeResource(key)
    }

    override suspend fun fetch(url: String) = withContext(io) {
        CacheWriter(cacheDataSourceFactory().createDataSource(), DataSpec(Uri.parse(url)), null, null).cache()
    }

    companion object {
        const val MAX_BYTES = 500L * 1024 * 1024
        const val USER_AGENT = "Qintar"
        private const val DIRECTORY = "audio"
    }
}

class SystemNetworkStatus @Inject constructor(@ApplicationContext private val context: Context) : NetworkStatus {
    private val manager get() = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val manager = manager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun isUnmetered(): Boolean = manager?.isActiveNetworkMetered == false
}
