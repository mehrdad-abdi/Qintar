package io.github.mehrdad_abdi.qintar.playback

import android.os.Bundle
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry

/**
 * What crosses the media session between the UI's controller and [PlaybackService], as bundles:
 * the custom commands' arguments, the session extras (origin, repeat, reciter) and the extras of a
 * media item (which ayah, in which segment and context).
 */
object SessionProtocol {
    const val COMMAND_PLAY = "qintar.play"
    const val COMMAND_REPEAT = "qintar.repeat"
    const val COMMAND_NEXT = "qintar.next"
    const val COMMAND_PREVIOUS = "qintar.previous"
    const val COMMAND_CLOSE = "qintar.close"

    private const val ORIGIN_ENTRY = "originEntry"
    private const val ORIGIN_ID = "originId"
    private const val ORIGIN_FIRST = "originFirst"
    private const val ORIGIN_LAST = "originLast"
    private const val SEGMENT_KINDS = "segmentKinds"
    private const val SEGMENT_IDS = "segmentIds"
    private const val SEGMENT_STARTS = "segmentStarts"
    private const val SEGMENT_ENDS = "segmentEnds"
    private const val START_SEGMENT = "startSegment"
    private const val START_AYAH = "startAyah"
    private const val REPEAT_AYAH = "repeatAyah"
    private const val REPEAT_PASSAGE = "repeatPassage"
    private const val RECITER = "reciter"
    private const val CLOSED = "closed"
    private const val ITEM_BASMALA = "basmala"
    private const val ITEM_AYAH = "ayah"
    private const val ITEM_SURAH = "surah"
    private const val ITEM_SEGMENT = "segment"
    private const val ITEM_CONTEXT_KIND = "contextKind"
    private const val ITEM_CONTEXT_ID = "contextId"
    private const val ITEM_INDEX = "index"
    private const val ITEM_PLAY = "play"

    // ---- Contexts --------------------------------------------------------------------------------

    private fun contextOf(kind: Int, id: Long): ReadContext? = when (ContextKind.entries.getOrNull(kind)) {
        ContextKind.KHATM -> ReadContext.Khatm(id)
        ContextKind.BOOKMARK -> ReadContext.Bookmark(id)
        ContextKind.BROWSE -> ReadContext.Browse
        ContextKind.RANDOM -> ReadContext.Random
        ContextKind.LOG -> ReadContext.Log
        null -> null
    }

    // ---- Origin ----------------------------------------------------------------------------------

    private fun Bundle.putOrigin(origin: PlaybackOrigin) {
        putString(ORIGIN_ENTRY, origin.entry.name)
        putLong(ORIGIN_ID, origin.id)
        putInt(ORIGIN_FIRST, origin.scopeFirstAyahId)
        putInt(ORIGIN_LAST, origin.scopeLastAyahId)
    }

    private fun Bundle.origin(): PlaybackOrigin? {
        val entry = ReaderEntry.entries.firstOrNull { it.name == getString(ORIGIN_ENTRY) } ?: return null
        return PlaybackOrigin(entry, getLong(ORIGIN_ID), getInt(ORIGIN_FIRST), getInt(ORIGIN_LAST))
    }

    // ---- "Play" ----------------------------------------------------------------------------------

    fun playArgs(request: PlayRequest): Bundle = Bundle().apply {
        putOrigin(request.origin)
        putIntArray(SEGMENT_KINDS, IntArray(request.segments.size) { request.segments[it].context.kind.ordinal })
        putLongArray(SEGMENT_IDS, LongArray(request.segments.size) { request.segments[it].context.id ?: 0L })
        putIntArray(SEGMENT_STARTS, IntArray(request.segments.size) { request.segments[it].range.start.value })
        putIntArray(SEGMENT_ENDS, IntArray(request.segments.size) { request.segments[it].range.end.value })
        putInt(START_SEGMENT, request.startSegment)
        putInt(START_AYAH, request.startAyah.value)
    }

    /** Null for arguments that do not describe a request (they come from outside: nothing is trusted). */
    fun playRequest(args: Bundle): PlayRequest? {
        val origin = args.origin() ?: return null
        val kinds = args.getIntArray(SEGMENT_KINDS) ?: return null
        val ids = args.getLongArray(SEGMENT_IDS) ?: return null
        val starts = args.getIntArray(SEGMENT_STARTS) ?: return null
        val ends = args.getIntArray(SEGMENT_ENDS) ?: return null
        if (kinds.isEmpty() || ids.size != kinds.size || starts.size != kinds.size || ends.size != kinds.size) return null
        val segments = kinds.indices.map { i ->
            val context = contextOf(kinds[i], ids[i]) ?: return null
            val start = AyahId.orNull(starts[i]) ?: return null
            val end = AyahId.orNull(ends[i])?.takeIf { it >= start } ?: return null
            Segment(context, AyahRange(start, end))
        }
        val startSegment = args.getInt(START_SEGMENT)
        val startAyah = AyahId.orNull(args.getInt(START_AYAH)) ?: return null
        if (startSegment !in segments.indices || startAyah !in segments[startSegment].range) return null
        return PlayRequest(origin, segments, startSegment, startAyah)
    }

    // ---- Repeat ----------------------------------------------------------------------------------

    fun repeatArgs(repeat: RepeatSettings): Bundle = Bundle().apply {
        putInt(REPEAT_AYAH, repeat.ayahTimes)
        putBoolean(REPEAT_PASSAGE, repeat.passage)
    }

    fun repeat(args: Bundle): RepeatSettings =
        RepeatSettings(args.getInt(REPEAT_AYAH, 1).coerceIn(1, MAX_AYAH_REPEAT), args.getBoolean(REPEAT_PASSAGE))

    const val MAX_AYAH_REPEAT = 100

    // ---- Session extras --------------------------------------------------------------------------

    data class SessionInfo(val origin: PlaybackOrigin?, val repeat: RepeatSettings, val reciterId: String?, val closed: Boolean)

    fun sessionExtras(origin: PlaybackOrigin?, repeat: RepeatSettings, reciterId: String?, closed: Boolean = false): Bundle =
        repeatArgs(repeat).apply {
            origin?.let { putOrigin(it) }
            putString(RECITER, reciterId)
            putBoolean(CLOSED, closed)
        }

    fun sessionInfo(extras: Bundle): SessionInfo =
        SessionInfo(extras.origin(), repeat(extras), extras.getString(RECITER), extras.getBoolean(CLOSED))

    // ---- Media item extras -----------------------------------------------------------------------

    fun itemExtras(entry: QueueEntry, cursor: Cursor): Bundle = Bundle().apply {
        putBoolean(ITEM_BASMALA, entry.kind == QueueKind.BASMALA)
        putInt(ITEM_AYAH, entry.ayah.value)
        putInt(ITEM_SURAH, entry.surah)
        putInt(ITEM_SEGMENT, entry.segment)
        putInt(ITEM_CONTEXT_KIND, entry.context.kind.ordinal)
        putLong(ITEM_CONTEXT_ID, entry.context.id ?: 0L)
        putInt(ITEM_INDEX, cursor.index)
        putInt(ITEM_PLAY, cursor.play)
    }

    fun nowPlaying(extras: Bundle?): NowPlaying? {
        extras ?: return null
        val ayah = AyahId.orNull(extras.getInt(ITEM_AYAH)) ?: return null
        val context = contextOf(extras.getInt(ITEM_CONTEXT_KIND, -1), extras.getLong(ITEM_CONTEXT_ID)) ?: return null
        return NowPlaying(
            ayah = ayah,
            surah = extras.getInt(ITEM_SURAH),
            segment = extras.getInt(ITEM_SEGMENT),
            basmala = extras.getBoolean(ITEM_BASMALA),
            context = context,
            play = extras.getInt(ITEM_PLAY, 1),
        )
    }

    fun cursor(extras: Bundle?): Cursor? {
        extras ?: return null
        if (!extras.containsKey(ITEM_INDEX)) return null
        return Cursor(extras.getInt(ITEM_INDEX), extras.getInt(ITEM_PLAY, 1))
    }
}

/** What the counter needs to know of an item. */
fun NowPlaying.playingItem() = PlayingItem(ayah.takeUnless { basmala }, context)
