package io.github.mehrdad_abdi.qintar.playback

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the player is playing, as far as counting goes.
 *
 * @param ayah null for the basmala, which is never counted.
 */
data class PlayingItem(val ayah: AyahId?, val context: ReadContext)

/** Why the player went from one item to another; mirrors Media3's transition reasons. */
enum class TransitionReason {
    /** The previous item played to its end and the player went on by itself. */
    AUTO,

    /** The same item starts again because the player repeats it. */
    REPEAT,

    /** The user (or a controller) jumped to another item. */
    SEEK,

    /** The playlist was replaced or changed under the player. */
    PLAYLIST_CHANGED,
}

/**
 * Counting by listening (R-TRK-7, §7.8): an ayah that has played to its end is one reading with
 * method AUDIO in the context of its queue entry, if "count listened ayahs" is on at that moment.
 *
 * It lives in the playback service and is fed the player's events, so it counts with the screen off
 * and with no UI at all. It does not hold the reading day: [ReadingTracker.mark] asks the clock when
 * it writes, so playback across the day boundary lands on both days.
 *
 * Not counted: an ayah left by a skip or by a new queue, one the listener seeked forward in, one that
 * failed, and the basmala. Each completed play counts once: an ayah repeated three times counts 3.
 */
class ListenCounter(
    private val tracker: ReadingTracker,
    private val prefs: PrefsStore,
    scope: CoroutineScope,
) {
    private var current: PlayingItem? = null

    /** The current play was shortened by a forward seek. */
    private var skippedAhead = false

    /** The current play has been counted already. */
    private var counted = false

    private val completed = Channel<PlayingItem>(Channel.UNLIMITED)

    init {
        // One writer, in order of completion.
        scope.launch {
            for (item in completed) {
                val ayah = item.ayah ?: continue
                runCatching {
                    if (prefs.prefs.first().countListened) tracker.mark(ayah, item.context, ReadMethod.AUDIO)
                }
            }
        }
    }

    /** The player moved to [item] (null: nothing is loaded any more). */
    fun onTransition(item: PlayingItem?, reason: TransitionReason) {
        if (reason == TransitionReason.AUTO || reason == TransitionReason.REPEAT) complete()
        current = item
        skippedAhead = false
        counted = false
    }

    /** A seek inside the current item. */
    fun onSeekInItem(fromMs: Long, toMs: Long) {
        when {
            // Back at the start: a new play of the whole ayah.
            toMs <= 0L -> {
                skippedAhead = false
                counted = false
            }
            toMs > fromMs + SEEK_TOLERANCE_MS -> skippedAhead = true
        }
    }

    /** The last item of the queue played to its end. */
    fun onEnded() = complete()

    /** Playback failed: the ayah was not heard to its end. Nothing is counted. */
    fun onError() = Unit

    private fun complete() {
        val item = current ?: return
        if (counted || skippedAhead || item.ayah == null) return
        counted = true
        completed.trySend(item)
    }

    companion object {
        /** A "seek" by less than this is the player adjusting its position, not the listener skipping. */
        const val SEEK_TOLERANCE_MS = 500L
    }
}
