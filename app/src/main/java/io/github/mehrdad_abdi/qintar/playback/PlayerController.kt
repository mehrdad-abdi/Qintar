package io.github.mehrdad_abdi.qintar.playback

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlinx.coroutines.flow.StateFlow

/** The reader a queue was started from: enough to tell "this reader" and to go back to it. */
data class PlaybackOrigin(
    val entry: ReaderEntry,
    val id: Long = ReaderRoute.UNSET_ID,
    val scopeFirstAyahId: Int = ReaderRoute.UNSET,
    val scopeLastAyahId: Int = ReaderRoute.UNSET,
) {
    /** The route that opens that reader again; it opens at the ayah being played. */
    fun route(): ReaderRoute = ReaderRoute(entry, id, ReaderRoute.UNSET, scopeFirstAyahId, scopeLastAyahId)
}

/** Two routes into the same reader have the same origin, wherever they open it. */
fun ReaderRoute.origin() = PlaybackOrigin(entry, id, scopeFirstAyahId, scopeLastAyahId)

/** "Play this scope from this ayah". */
data class PlayRequest(
    val origin: PlaybackOrigin,
    val segments: List<Segment>,
    val startSegment: Int,
    val startAyah: AyahId,
)

/** The item being played. For the basmala, [ayah] is the ayah 1 it stands before. */
data class NowPlaying(
    val ayah: AyahId,
    val surah: Int,
    val segment: Int,
    val basmala: Boolean,
    val context: ReadContext,
    /** Which play of this ayah in a row (1 unless the ayah repeats). */
    val play: Int = 1,
)

enum class PlaybackProblem {
    /** No connection and the audio is not in the cache. */
    OFFLINE,
    FAILED,
}

data class PlayerState(
    val origin: PlaybackOrigin? = null,
    val item: NowPlaying? = null,
    /** Sound is coming out. */
    val playing: Boolean = false,
    /** Wants to play and is loading. */
    val loading: Boolean = false,
    val repeat: RepeatSettings = RepeatSettings(),
    val reciterId: String? = null,
    val problem: PlaybackProblem? = null,
) {
    /** Something is loaded: the mini player shows. */
    val active: Boolean get() = item != null
}

/**
 * What the UI knows of playback (§7.8). The production implementation wraps a `MediaController`
 * connected to [PlaybackService]; view-model tests use a fake.
 */
interface PlayerController {
    val state: StateFlow<PlayerState>

    fun play(request: PlayRequest)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun setRepeat(repeat: RepeatSettings)

    /** Stops playback and lets the service go. */
    fun close()
}
