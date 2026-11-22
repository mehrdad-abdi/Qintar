package io.github.mehrdad_abdi.qintar.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mehrdad_abdi.qintar.playback.AudioDownloader
import io.github.mehrdad_abdi.qintar.playback.AudioDownloads
import io.github.mehrdad_abdi.qintar.playback.AudioFetcher
import io.github.mehrdad_abdi.qintar.playback.AudioSource
import io.github.mehrdad_abdi.qintar.playback.AudioStorage
import io.github.mehrdad_abdi.qintar.playback.IslamicNetworkAudioSource
import io.github.mehrdad_abdi.qintar.playback.Media3AudioCache
import io.github.mehrdad_abdi.qintar.playback.MediaPlayerController
import io.github.mehrdad_abdi.qintar.playback.NetworkStatus
import io.github.mehrdad_abdi.qintar.playback.PlayerController
import io.github.mehrdad_abdi.qintar.playback.SystemNetworkStatus

/** Audio (§7.8): what the UI and the playback service see of the player, the cache and the source. */
@Module
@InstallIn(SingletonComponent::class)
interface PlaybackModule {
    @Binds
    fun audioSource(source: IslamicNetworkAudioSource): AudioSource

    @Binds
    fun playerController(controller: MediaPlayerController): PlayerController

    @Binds
    fun audioStorage(cache: Media3AudioCache): AudioStorage

    @Binds
    fun audioFetcher(cache: Media3AudioCache): AudioFetcher

    @Binds
    fun networkStatus(status: SystemNetworkStatus): NetworkStatus

    @Binds
    fun audioDownloads(downloader: AudioDownloader): AudioDownloads
}
