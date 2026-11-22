@file:OptIn(UnstableApi::class)

package io.github.mehrdad_abdi.qintar.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import io.github.mehrdad_abdi.qintar.MainActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.ui.common.displayName
import io.github.mehrdad_abdi.qintar.ui.designsystem.Numerals
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Set on the intent that the playback notification sends to the activity: show what is playing. */
const val EXTRA_OPEN_PLAYER = "io.github.mehrdad_abdi.qintar.OPEN_PLAYER"

fun openPlayerIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    .putExtra(EXTRA_OPEN_PLAYER, true)

/**
 * The one player of the app (§7.8): an ExoPlayer behind a media session. Media3 gives it the
 * foreground notification, the lock-screen and headset controls, audio focus and the pause when
 * headphones are unplugged; none of that is hand-made here.
 *
 * The UI sends "play this scope from this ayah" as a custom command; the service builds the
 * [PlaybackQueue], keeps the player loaded with the current item and the one that follows, and counts
 * completed ayahs through [ListenCounter] — with no UI alive, with the screen off.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var tracker: ReadingTracker
    @Inject lateinit var prefs: PrefsStore
    @Inject lateinit var index: Provider<QuranIndex>
    @Inject lateinit var cache: Media3AudioCache
    @Inject lateinit var source: AudioSource

    @Inject
    @IoDispatcher
    lateinit var io: CoroutineDispatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var exo: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var counter: ListenCounter

    private var queue: PlaybackQueue? = null
    private var quran: QuranIndex? = null
    private var cursor: Cursor? = null

    /** A sajda ayah playback stopped before ("Stop before sajda ayahs"); "play" starts there. */
    private var waitingAt: Cursor? = null
    private var origin: PlaybackOrigin? = null
    private var repeat = RepeatSettings()
    private var settings = UserPrefs()
    private var loading: Job? = null
    private var idle: Job? = null

    override fun onCreate() {
        super.onCreate()
        counter = ListenCounter(tracker, prefs, scope)
        exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cache.dataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo.addListener(PlayerEvents())
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        val builder = MediaSession.Builder(this, QueuePlayer(exo)).setCallback(SessionCallback())
        // A tap on the notification (or on the lock-screen player) opens the reader the audio came from.
        builder.setSessionActivity(
            PendingIntent.getActivity(this, 0, openPlayerIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        session = builder.build()

        // Speed, reciter and quality follow the settings while the service lives.
        scope.launch { prefs.prefs.collect(::applySettings) }
    }

    private fun applySettings(value: UserPrefs) {
        val before = settings
        settings = value
        exo.setPlaybackSpeed(value.playbackSpeed.coerceIn(MIN_SPEED, MAX_SPEED))
        if (before.reciterId != value.reciterId || before.audioQuality != value.audioQuality || before.stopBeforeSajda != value.stopBeforeSajda) {
            // From the next ayah on; the one being played is not interrupted.
            refreshLookahead()
            publishExtras()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** The app was swiped away: go on while something plays, leave otherwise. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!exo.playWhenReady || exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED) close()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        exo.release()
        super.onDestroy()
    }

    // ---- The queue -------------------------------------------------------------------------------

    private fun start(request: PlayRequest) {
        loading?.cancel()
        idle?.cancel()
        publishExtras()
        loading = scope.launch {
            applySettings(prefs.prefs.first())
            // The index is loaded at app start; this waits only in a process that was just created.
            val (index, built) = withContext(io) {
                val index = index.get()
                index to PlaybackQueue.build(request.segments, index, basmala = !Reciters.of(settings.reciterId).ayahOneIncludesBasmala)
            }
            val at = built.startAt(request.startSegment, request.startAyah) ?: return@launch
            quran = index
            queue = built
            origin = request.origin
            load(at, play = true)
        }
    }

    /** Puts the entry at [at] and the one that follows it into the player. */
    private fun load(at: Cursor, play: Boolean) {
        val queue = queue ?: return
        val items = listOfNotNull(at, following(queue, at)).map(::mediaItem)
        cursor = at
        exo.setMediaItems(items, /* resetPosition = */ true)
        exo.prepare()
        exo.playWhenReady = play
        publishExtras()
    }

    /** After the player moved by itself: the current item first, the planned next one behind it. */
    private fun syncToCurrentItem() {
        cursor = SessionProtocol.cursor(exo.currentMediaItem?.mediaMetadata?.extras) ?: return
        val at = exo.currentMediaItemIndex
        if (at > 0) exo.removeMediaItems(0, at)
        refreshLookahead()
    }

    private fun refreshLookahead() {
        val queue = queue ?: return
        val cursor = cursor ?: return
        if (exo.mediaItemCount == 0) return
        val at = exo.currentMediaItemIndex
        if (exo.mediaItemCount > at + 1) exo.removeMediaItems(at + 1, exo.mediaItemCount)
        following(queue, cursor)?.let { exo.addMediaItem(mediaItem(it)) }
    }

    /** What the player plays after [at] by itself; before a sajda ayah (when asked) nothing: it waits. */
    private fun following(queue: PlaybackQueue, at: Cursor): Cursor? =
        when (val step = queue.step(at, repeat, settings.stopBeforeSajda)) {
            is PlaybackQueue.Step.Play -> step.cursor.also { waitingAt = null }
            is PlaybackQueue.Step.WaitAt -> { waitingAt = step.cursor; null }
            PlaybackQueue.Step.End -> { waitingAt = null; null }
        }

    private fun skipToNext() {
        val queue = queue ?: return
        val cursor = cursor ?: return
        queue.next(cursor)?.let { load(it, exo.playWhenReady) }
    }

    private fun skipToPrevious() {
        val queue = queue ?: return
        val cursor = cursor ?: return
        val before = queue.previous(cursor)
        if (before == null || exo.currentPosition > RESTART_THRESHOLD_MS) exo.seekTo(0) else load(before, exo.playWhenReady)
    }

    /** "Play" after the player stopped: at the sajda ayah it waited before, else from the scope's beginning. */
    private fun restart() {
        val wait = waitingAt
        if (wait != null) return load(wait, play = true)
        if ((queue?.size ?: 0) > 0) load(Cursor(0), play = true)
    }

    private fun setRepeat(value: RepeatSettings) {
        repeat = value
        refreshLookahead()
        publishExtras()
    }

    private fun close() {
        loading?.cancel()
        idle?.cancel()
        exo.stop()
        exo.clearMediaItems()
        queue = null
        cursor = null
        origin = null
        session?.setSessionExtras(SessionProtocol.sessionExtras(null, repeat, settings.reciterId, closed = true))
        stopSelf()
    }

    private fun publishExtras() {
        session?.setSessionExtras(SessionProtocol.sessionExtras(origin, repeat, settings.reciterId))
    }

    private fun mediaItem(cursor: Cursor): MediaItem {
        val entry = queue!![cursor.index]
        val language = resources.configuration.locales[0].language
        val surah = quran?.surah(entry.surah)?.displayName(language).orEmpty()
        val title = if (entry.kind == QueueKind.BASMALA) {
            getString(R.string.audio_title_basmala, surah)
        } else {
            val number = quran?.ref(entry.ayah)?.ayah ?: 0
            getString(R.string.audio_title_ayah, surah, Numerals.format(number, if (settings.latinNumerals) "en" else language))
        }
        return MediaItem.Builder()
            .setMediaId(entry.mediaId)
            .setUri(source.url(settings.reciterId, entry.audioAyah, settings.audioQuality))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(getString(Reciters.of(settings.reciterId).name))
                    .setExtras(SessionProtocol.itemExtras(entry, cursor))
                    .build(),
            )
            .build()
    }

    /** Nothing has played for [IDLE_STOP_MILLIS]: the service lets go. */
    private fun watchIdle() {
        val waiting = !exo.playWhenReady || exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED
        if (!waiting) {
            idle?.cancel()
            idle = null
        } else if (idle?.isActive != true) {
            idle = scope.launch {
                delay(IDLE_STOP_MILLIS)
                close()
            }
        }
    }

    // ---- Player events ---------------------------------------------------------------------------

    private inner class PlayerEvents : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val item = SessionProtocol.nowPlaying(mediaItem?.mediaMetadata?.extras)?.playingItem()
            val why = when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> TransitionReason.AUTO
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> TransitionReason.REPEAT
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> TransitionReason.SEEK
                else -> TransitionReason.PLAYLIST_CHANGED
            }
            counter.onTransition(item, why)
            if (why == TransitionReason.AUTO || why == TransitionReason.SEEK) syncToCurrentItem()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            val seek = reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            if (seek && oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                counter.onSeekInItem(oldPosition.positionMs, newPosition.positionMs)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) counter.onEnded()
            watchIdle()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = watchIdle()

        override fun onPlayerError(error: PlaybackException) = counter.onError()
    }

    /**
     * The player as the session shows it. Under it the playlist holds two items only, so "next" and
     * "previous" are answered from the queue, and they are always offered. Seeking inside an ayah and
     * the player's own repeat and shuffle modes are not offered.
     */
    private inner class QueuePlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .removeAll(
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
            )
            .build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

        override fun hasNextMediaItem(): Boolean = cursor?.let { queue?.next(it) } != null
        override fun hasPreviousMediaItem(): Boolean = cursor?.let { queue?.previous(it) } != null

        override fun seekToNext() = skipToNext()
        override fun seekToNextMediaItem() = skipToNext()
        override fun seekToPrevious() = skipToPrevious()
        override fun seekToPreviousMediaItem() = skipToPrevious()

        /** How "play" arrives after the end of the queue. */
        override fun seekToDefaultPosition() {
            if (playbackState == Player.STATE_ENDED) restart() else super.seekToDefaultPosition()
        }

        override fun play() {
            if (playbackState == Player.STATE_ENDED) restart() else super.play()
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val builder = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            // Only the app's own UI starts a queue or changes how it repeats.
            if (controller.packageName == packageName) {
                for (action in OWN_COMMANDS) builder.add(SessionCommand(action, Bundle.EMPTY))
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(builder.build()).build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (controller.packageName != packageName) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
            when (customCommand.customAction) {
                SessionProtocol.COMMAND_PLAY -> {
                    val request = SessionProtocol.playRequest(args)
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    start(request)
                }
                SessionProtocol.COMMAND_REPEAT -> setRepeat(SessionProtocol.repeat(args))
                SessionProtocol.COMMAND_NEXT -> skipToNext()
                SessionProtocol.COMMAND_PREVIOUS -> skipToPrevious()
                SessionProtocol.COMMAND_CLOSE -> close()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f

        /** "Previous" later than this into an ayah starts the ayah again. */
        const val RESTART_THRESHOLD_MS = 3_000L
        const val IDLE_STOP_MILLIS = 10 * 60_000L
        private val OWN_COMMANDS = listOf(
            SessionProtocol.COMMAND_PLAY,
            SessionProtocol.COMMAND_REPEAT,
            SessionProtocol.COMMAND_NEXT,
            SessionProtocol.COMMAND_PREVIOUS,
            SessionProtocol.COMMAND_CLOSE,
        )
    }
}
