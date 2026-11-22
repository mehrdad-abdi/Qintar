@file:OptIn(UnstableApi::class)

package io.github.mehrdad_abdi.qintar.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [PlayerController] over a `MediaController` (§7.8). The controller is connected when something is
 * to be played — which starts [PlaybackService] — and released when the session is closed, so that
 * the service can go. Everything here runs on the main thread.
 */
@Singleton
class MediaPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayerController {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val executor = ContextCompat.getMainExecutor(context)
    private var connecting: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    private val sessionListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = publish()

        override fun onDisconnected(controller: MediaController) = release()
    }

    private fun connected(block: (MediaController) -> Unit) {
        controller?.let { return block(it) }
        val future = connecting ?: MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .setListener(sessionListener)
            .buildAsync()
            .also { connecting = it }
        future.addListener(
            {
                if (connecting === future) {
                    connecting = null
                    controller = runCatching { future.get() }.getOrNull()?.also { it.addListener(playerListener) }
                }
                // A failed connection leaves nothing behind; the action is dropped.
                controller?.let(block)
            },
            executor,
        )
    }

    private fun command(action: String, args: Bundle = Bundle.EMPTY) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    override fun play(request: PlayRequest) = connected {
        it.sendCustomCommand(SessionCommand(SessionProtocol.COMMAND_PLAY, Bundle.EMPTY), SessionProtocol.playArgs(request))
    }

    override fun togglePlayPause() {
        // Also "try again" after an error and "from the beginning" after the end.
        controller?.let { Util.handlePlayPauseButtonAction(it) }
    }

    override fun next() = command(SessionProtocol.COMMAND_NEXT)

    override fun previous() = command(SessionProtocol.COMMAND_PREVIOUS)

    override fun setRepeat(repeat: RepeatSettings) = command(SessionProtocol.COMMAND_REPEAT, SessionProtocol.repeatArgs(repeat))

    override fun close() {
        val controller = controller ?: return
        controller.sendCustomCommand(SessionCommand(SessionProtocol.COMMAND_CLOSE, Bundle.EMPTY), Bundle.EMPTY)
            .addListener({ release() }, executor)
    }

    private fun release() {
        controller?.let {
            it.removeListener(playerListener)
            it.release()
        }
        controller = null
        _state.value = PlayerState()
    }

    private fun publish() {
        val controller = controller ?: return
        val info = SessionProtocol.sessionInfo(controller.sessionExtras)
        // The service closed the session by itself (idle for long, or the task was removed).
        if (info.closed && controller.mediaItemCount == 0 && _state.value.active) return release()
        val wantsToPlay = controller.playWhenReady && controller.playbackState != Player.STATE_ENDED
        _state.value = PlayerState(
            origin = info.origin,
            item = SessionProtocol.nowPlaying(controller.currentMediaItem?.mediaMetadata?.extras),
            playing = controller.isPlaying,
            loading = wantsToPlay && controller.playbackState == Player.STATE_BUFFERING,
            repeat = info.repeat,
            reciterId = info.reciterId,
            problem = controller.playerError?.let(::problemOf),
        )
    }

    companion object {
        fun problemOf(error: PlaybackException): PlaybackProblem = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> PlaybackProblem.OFFLINE
            else -> PlaybackProblem.FAILED
        }
    }
}
