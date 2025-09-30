package io.github.mehrdad_abdi.quranbookmarks.domain.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val completedUrl: String? = null // URL of the audio that just completed
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
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { player ->
                    val params = player.playbackParams
                    params.speed = speed.value
                    player.playbackParams = params

                    _playbackState.value = _playbackState.value.copy(playbackSpeed = speed.value)
                    Log.d("AudioService", "Playback speed set to ${speed.displayText}")
                }
            } else {
                Log.w("AudioService", "Playback speed control not supported on this Android version")
                _playbackState.value = _playbackState.value.copy(
                    error = "Speed control requires Android 6.0 or higher"
                )
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

    fun release() {
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
        releaseAudioFocus()
    }
}