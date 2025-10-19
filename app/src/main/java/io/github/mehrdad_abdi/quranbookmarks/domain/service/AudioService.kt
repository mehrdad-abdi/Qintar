package io.github.mehrdad_abdi.quranbookmarks.domain.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mehrdad_abdi.quranbookmarks.domain.model.PlaybackContext
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.AyahReference
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.CacheAyahContentUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val currentUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val progress: Int = 0,
    val duration: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val completedUrl: String? = null, // URL of the audio that just completed
    val isPlayingBismillah: Boolean = false,
    val pendingVerseAfterBismillah: VerseMetadata? = null,
    val prefetchInProgress: Boolean = false,
    val lastPrefetchedAyah: String? = null, // Format: "surah:ayah"
    val playbackContext: PlaybackContext? = null,
    val currentVerse: VerseMetadata? = null
)

enum class PlaybackSpeed(val value: Float, val displayText: String) {
    SPEED_0_5(0.5f, "0.5x"),
    SPEED_0_75(0.75f, "0.75x"),
    SPEED_1(1.0f, "1x"),
    SPEED_1_25(1.25f, "1.25x"),
    SPEED_1_5(1.5f, "1.5x"),
    SPEED_2(2.0f, "2x");

    companion object {
        fun fromValue(value: Float): PlaybackSpeed {
            return values().find { it.value == value } ?: SPEED_1
        }
    }
}

@Singleton
class AudioService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val quranRepository: QuranRepository,
    private val cacheAyahContentUseCase: CacheAyahContentUseCase
) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val serviceScope = CoroutineScope(SupervisorJob())

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    init {
        // Load initial playback speed from settings
        loadInitialPlaybackSpeed()
    }

    private fun loadInitialPlaybackSpeed() {
        serviceScope.launch {
            settingsRepository.getSettings().collect { settings ->
                val speed = PlaybackSpeed.fromValue(settings.playbackSpeed)
                // Only update state, don't apply to MediaPlayer yet (it will be applied when audio plays)
                _playbackState.value = _playbackState.value.copy(playbackSpeed = speed.value)
                Log.d("AudioService", "Loaded initial playback speed: ${speed.displayText}")
            }
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }

    fun playAudio(url: String) {
        try {
            Log.d("AudioService", "Attempting to play audio from URL: $url")

            // If already playing the same URL, just resume
            if (_playbackState.value.currentUrl == url && _playbackState.value.isPlaying) {
                Log.d("AudioService", "Already playing this URL, skipping")
                return
            }

            // If playing different URL, stop current playback
            if (_playbackState.value.isPlaying) {
                Log.d("AudioService", "Stopping current playback")
                stop()
            }

            _playbackState.value = _playbackState.value.copy(
                isLoading = true,
                error = null,
                currentUrl = url
            )

            // Request audio focus
            requestAudioFocus()

            // Initialize MediaPlayer
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setOnPreparedListener {
                    Log.d("AudioService", "MediaPlayer prepared successfully")

                    // Apply current speed setting
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val currentSpeed = _playbackState.value.playbackSpeed
                            val params = it.playbackParams
                            params.speed = currentSpeed
                            it.playbackParams = params
                            Log.d("AudioService", "Applied playback speed: ${currentSpeed}x")
                        } catch (e: Exception) {
                            Log.w("AudioService", "Failed to apply playback speed", e)
                        }
                    }

                    _playbackState.value = _playbackState.value.copy(
                        isLoading = false,
                        isPlaying = true,
                        duration = it.duration
                    )
                    it.start()
                    Log.d("AudioService", "Audio playback started")

                    // Start prefetch after playback begins (skip for bismillah)
                    if (!_playbackState.value.isPlayingBismillah) {
                        serviceScope.launch {
                            val verse = _playbackState.value.currentVerse
                            val ctx = _playbackState.value.playbackContext
                            if (verse != null && ctx != null) {
                                prefetchNextAyah(verse, ctx)
                            }
                        }
                    }
                }

                setOnCompletionListener {
                    val completedUrl = _playbackState.value.currentUrl
                    Log.d("AudioService", "Audio completed: $completedUrl")
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        progress = 0,
                        completedUrl = completedUrl
                    )
                    releaseAudioFocus()

                    // Check if bismillah just completed - if so, play the pending verse
                    if (_playbackState.value.isPlayingBismillah) {
                        handleBismillahCompletion()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AudioService", "MediaPlayer error: what=$what, extra=$extra")
                    _playbackState.value = _playbackState.value.copy(
                        isLoading = false,
                        isPlaying = false,
                        error = "Audio playback error: $what, $extra"
                    )
                    releaseAudioFocus()
                    true
                }

                Log.d("AudioService", "Setting data source: $url")
                setDataSource(url)
                Log.d("AudioService", "Starting async preparation")
                prepareAsync()
            }

        } catch (e: Exception) {
            Log.e("AudioService", "Exception in playAudio", e)
            _playbackState.value = _playbackState.value.copy(
                isLoading = false,
                isPlaying = false,
                error = "Failed to play audio: ${e.message}"
            )
            releaseAudioFocus()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                }
            }
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(
                error = "Failed to pause audio: ${e.message}"
            )
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    requestAudioFocus()
                    it.start()
                    _playbackState.value = _playbackState.value.copy(isPlaying = true)
                }
            }
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(
                error = "Failed to resume audio: ${e.message}"
            )
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
            }
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                progress = 0,
                currentUrl = null
            )
            releaseAudioFocus()
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(
                error = "Failed to stop audio: ${e.message}"
            )
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            _playbackState.value = _playbackState.value.copy(progress = position)
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(
                error = "Failed to seek: ${e.message}"
            )
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        try {
            // Update state immediately
            _playbackState.value = _playbackState.value.copy(playbackSpeed = speed.value)

            // Apply to MediaPlayer if available and supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { player ->
                    val params = player.playbackParams
                    params.speed = speed.value
                    player.playbackParams = params
                    Log.d("AudioService", "Playback speed set to ${speed.displayText}")
                }
            } else {
                Log.w("AudioService", "Playback speed control not supported on this Android version")
            }

            // Persist to settings
            serviceScope.launch {
                try {
                    settingsRepository.updatePlaybackSpeed(speed.value)
                    Log.d("AudioService", "Persisted playback speed: ${speed.displayText}")
                } catch (e: Exception) {
                    Log.e("AudioService", "Failed to persist playback speed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioService", "Failed to set playback speed", e)
            _playbackState.value = _playbackState.value.copy(
                error = "Failed to change playback speed: ${e.message}"
            )
        }
    }

    fun getCurrentPlaybackSpeed(): PlaybackSpeed {
        return PlaybackSpeed.fromValue(_playbackState.value.playbackSpeed)
    }

    fun clearError() {
        _playbackState.value = _playbackState.value.copy(error = null)
    }

    fun clearCompletion() {
        _playbackState.value = _playbackState.value.copy(completedUrl = null)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    /**
     * Play a verse with automatic bismillah handling and smart prefetching.
     * If the verse is the first ayah of a surah (2-114, excluding surah 9),
     * bismillah will be played first, then the verse.
     *
     * @param verse The verse to play
     * @param context Optional playback context for smart prefetching of next ayah
     */
    suspend fun playVerse(verse: VerseMetadata, context: PlaybackContext? = null) {
        try {
            Log.d("AudioService", "=== PLAY VERSE ===")
            Log.d("AudioService", "Verse: Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}")
            Log.d("AudioService", "Context: ${context?.javaClass?.simpleName}")

            // Store context and current verse for prefetching
            _playbackState.value = _playbackState.value.copy(
                playbackContext = context,
                currentVerse = verse
            )

            // Check if bismillah should be played first
            if (shouldPlayBismillah(verse)) {
                Log.d("AudioService", "✓ BISMILLAH REQUIRED")
                val bismillahUrl = getBismillahUrl()

                if (bismillahUrl != null) {
                    Log.d("AudioService", "▶ PLAYING BISMILLAH: $bismillahUrl")
                    // Store the pending verse and mark as playing bismillah
                    _playbackState.value = _playbackState.value.copy(
                        isPlayingBismillah = true,
                        pendingVerseAfterBismillah = verse
                    )
                    playAudio(bismillahUrl)
                    return
                } else {
                    Log.e("AudioService", "✗ BISMILLAH URL IS NULL - playing verse directly")
                }
            } else {
                Log.d("AudioService", "✗ No bismillah needed")
            }

            // Play the verse directly (either no bismillah needed or bismillah URL failed)
            playVerseDirectly(verse)
        } catch (e: Exception) {
            Log.e("AudioService", "Exception in playVerse", e)
            _playbackState.value = _playbackState.value.copy(
                isLoading = false,
                isPlaying = false,
                error = "Failed to play verse: ${e.message}",
                isPlayingBismillah = false,
                pendingVerseAfterBismillah = null
            )
        }
    }

    /**
     * Play a verse directly without bismillah check
     */
    private suspend fun playVerseDirectly(verse: VerseMetadata) {
        try {
            val audioUrl = getVerseAudioUrl(verse)
            Log.d("AudioService", "▶ PLAYING VERSE: $audioUrl")
            _playbackState.value = _playbackState.value.copy(
                isPlayingBismillah = false,
                pendingVerseAfterBismillah = null
            )
            playAudio(audioUrl)
        } catch (e: Exception) {
            Log.e("AudioService", "Failed to play verse directly", e)
            _playbackState.value = _playbackState.value.copy(
                error = "Failed to play audio: ${e.message}",
                isPlayingBismillah = false,
                pendingVerseAfterBismillah = null
            )
        }
    }

    /**
     * Check if bismillah should be played before this verse.
     * Bismillah plays for first verses (ayahInSurah == 1) of surahs 2-114, excluding surah 9 (At-Tawbah)
     */
    private fun shouldPlayBismillah(verse: VerseMetadata): Boolean {
        Log.d("AudioService", "Checking bismillah: Surah ${verse.surahNumber}, Ayah ${verse.ayahInSurah}")
        val shouldPlay = verse.ayahInSurah == 1 &&
                        verse.surahNumber in 2..114 &&
                        verse.surahNumber != 9
        Log.d("AudioService", "  - RESULT: shouldPlay = $shouldPlay")
        return shouldPlay
    }

    /**
     * Get audio URL for bismillah (global ayah number 1 - Al-Fatiha 1:1)
     */
    private suspend fun getBismillahUrl(): String? {
        Log.d("AudioService", "getBismillahUrl called")

        return try {
            // Check if bismillah is cached (surah 1, ayah 1)
            val cachedContent = quranRepository.getCachedContent(1, 1)
            Log.d("AudioService", "  - Cached content for 1:1: ${cachedContent != null}")
            Log.d("AudioService", "  - Cached audio path: ${cachedContent?.audioPath}")

            if (cachedContent?.audioPath != null) {
                Log.d("AudioService", "  → Using cached bismillah audio: ${cachedContent.audioPath}")
                return "file://${cachedContent.audioPath}"
            }

            // Fall back to streaming URL for global ayah number 1
            val settings = settingsRepository.getSettings().stateIn(serviceScope).value
            val audioUrl = quranRepository.getAudioUrl(settings.reciterEdition, 1, settings.reciterBitrate)
            Log.d("AudioService", "  → Using streaming bismillah URL: $audioUrl (bitrate: ${settings.reciterBitrate})")
            audioUrl
        } catch (e: Exception) {
            Log.e("AudioService", "  ✗ Error getting bismillah URL", e)
            null
        }
    }

    /**
     * Get audio URL for a verse (cached or streaming)
     */
    private suspend fun getVerseAudioUrl(verse: VerseMetadata): String {
        // Check if we have cached audio first
        val cachedContent = quranRepository.getCachedContent(verse.surahNumber, verse.ayahInSurah)
        if (cachedContent?.audioPath != null) {
            Log.d("AudioService", "Using cached audio: ${cachedContent.audioPath}")
            return "file://${cachedContent.audioPath}"
        }

        // Fall back to streaming URL using settings
        val settings = settingsRepository.getSettings().stateIn(serviceScope).value
        val audioUrl = quranRepository.getAudioUrl(
            settings.reciterEdition,
            verse.globalAyahNumber,
            settings.reciterBitrate
        )
        Log.d("AudioService", "Using streaming audio URL: $audioUrl")
        return audioUrl
    }

    /**
     * Handle the completion transition from bismillah to verse
     */
    private fun handleBismillahCompletion() {
        serviceScope.launch {
            val pendingVerse = _playbackState.value.pendingVerseAfterBismillah
            if (pendingVerse != null) {
                Log.d("AudioService", "Bismillah completed, playing pending verse")

                // Clear completion BEFORE playing next
                clearCompletion()
                kotlinx.coroutines.delay(100)

                // Play the pending verse
                playVerseDirectly(pendingVerse)
            }
        }
    }

    /**
     * Prefetch the next ayah in the background based on playback context.
     * This ensures seamless playback by downloading the next ayah while the current one plays.
     */
    private suspend fun prefetchNextAyah(currentVerse: VerseMetadata, context: PlaybackContext) {
        if (_playbackState.value.prefetchInProgress) {
            Log.d("AudioService", "Prefetch already in progress, skipping")
            return
        }

        val nextVerse = determineNextVerse(currentVerse, context) ?: return
        val ayahId = "${nextVerse.surahNumber}:${nextVerse.ayahInSurah}"

        // Skip if already prefetched
        if (_playbackState.value.lastPrefetchedAyah == ayahId) {
            Log.d("AudioService", "Next ayah already prefetched: $ayahId")
            return
        }

        // Check if already cached
        val cached = quranRepository.getCachedContent(nextVerse.surahNumber, nextVerse.ayahInSurah)
        if (cached?.audioPath != null) {
            Log.d("AudioService", "Next ayah already cached: $ayahId")
            _playbackState.value = _playbackState.value.copy(lastPrefetchedAyah = ayahId)
            return
        }

        // Start prefetch in background
        _playbackState.value = _playbackState.value.copy(prefetchInProgress = true)

        serviceScope.launch {
            try {
                Log.d("AudioService", "Prefetching next ayah: $ayahId")

                // Prefetch bismillah if needed for next verse
                if (shouldPlayBismillah(nextVerse)) {
                    val bismillahCached = quranRepository.getCachedContent(1, 1)
                    if (bismillahCached?.audioPath == null) {
                        Log.d("AudioService", "Prefetching bismillah for next ayah")
                        cacheAyahContentUseCase(AyahReference(1, 1, 1))
                    }
                }

                // Prefetch next ayah
                val result = cacheAyahContentUseCase(
                    AyahReference(
                        nextVerse.surahNumber,
                        nextVerse.ayahInSurah,
                        nextVerse.globalAyahNumber
                    )
                )

                if (result.isSuccess) {
                    Log.d("AudioService", "Successfully prefetched: $ayahId")
                    _playbackState.value = _playbackState.value.copy(
                        lastPrefetchedAyah = ayahId,
                        prefetchInProgress = false
                    )
                } else {
                    Log.w("AudioService", "Failed to prefetch: $ayahId - ${result.exceptionOrNull()?.message}")
                    _playbackState.value = _playbackState.value.copy(prefetchInProgress = false)
                }
            } catch (e: Exception) {
                Log.e("AudioService", "Error during prefetch", e)
                _playbackState.value = _playbackState.value.copy(prefetchInProgress = false)
            }
        }
    }

    /**
     * Determine the next verse to prefetch based on playback context
     */
    private suspend fun determineNextVerse(
        current: VerseMetadata,
        context: PlaybackContext
    ): VerseMetadata? {
        return when (context) {
            is PlaybackContext.SingleBookmark -> {
                val nextIndex = context.currentIndex + 1
                if (nextIndex < context.allAyahs.size) {
                    context.allAyahs[nextIndex]
                } else {
                    null
                }
            }
            is PlaybackContext.AllBookmarks -> {
                val nextIndex = context.currentIndex + 1
                if (nextIndex < context.allAyahs.size) {
                    context.allAyahs[nextIndex].verse
                } else {
                    null
                }
            }
            is PlaybackContext.KhatmReading -> {
                val nextIndex = context.currentIndex + 1
                if (nextIndex < context.allAyahsOnPage.size) {
                    // Next ayah on same page
                    context.allAyahsOnPage[nextIndex]
                } else {
                    // Last ayah on page - fetch first ayah of next page
                    val nextPageNumber = context.currentPageNumber + 1
                    if (nextPageNumber <= 604) { // Quran has 604 pages
                        Log.d("AudioService", "Prefetching first ayah of next page: $nextPageNumber")
                        val nextPageResult = quranRepository.getPageMetadata(nextPageNumber)
                        if (nextPageResult.isSuccess) {
                            val nextPageAyahs = nextPageResult.getOrThrow()
                            nextPageAyahs.firstOrNull()
                        } else {
                            Log.w("AudioService", "Failed to get next page metadata")
                            null
                        }
                    } else {
                        null // End of Quran
                    }
                }
            }
            is PlaybackContext.Random -> null // No prefetch for random
        }
    }

    fun release() {
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
        releaseAudioFocus()
    }
}