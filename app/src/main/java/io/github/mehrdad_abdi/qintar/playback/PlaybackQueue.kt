package io.github.mehrdad_abdi.qintar.playback

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex

enum class QueueKind { AYAH, BASMALA }

/**
 * One thing the player plays.
 *
 * @param ayah the ayah; for a [QueueKind.BASMALA] the ayah 1 it stands before.
 * @param segment index of the reader segment it belongs to.
 * @param context what a completed listening of this ayah is counted under.
 */
data class QueueEntry(
    val kind: QueueKind,
    val ayah: AyahId,
    val surah: Int,
    val segment: Int,
    val context: ReadContext,
    /** An ayah with a sajda: playback can wait before it (§7.8, "Stop before sajda ayahs"). */
    val sajda: Boolean = false,
) {
    val mediaId: String get() = if (kind == QueueKind.AYAH) "ayah:${ayah.value}" else "basmala:$surah"

    /** The ayah whose audio file is played: the basmala is the recitation of 1:1. */
    val audioAyah: AyahId get() = if (kind == QueueKind.BASMALA) AyahId.FIRST else ayah
}

/**
 * @param ayahTimes how often each ayah is played before the next one (1 = no repeat).
 * @param passage start again from the beginning of the scope after its last ayah.
 */
data class RepeatSettings(val ayahTimes: Int = 1, val passage: Boolean = false) {
    init {
        require(ayahTimes >= 1) { "ayahTimes must be at least 1: $ayahTimes" }
    }
}

/** A position in the queue: the entry at [index], being played for the [play]-th time in a row. */
data class Cursor(val index: Int, val play: Int = 1)

/**
 * The reader's scope as an ordered list of things to play (§7.8): every ayah of every segment in
 * order, with the basmala before ayah 1 of each surah that has one. Pure: built from the index alone.
 */
class PlaybackQueue(val entries: List<QueueEntry>) {
    val size: Int get() = entries.size

    operator fun get(index: Int): QueueEntry = entries[index]

    fun getOrNull(cursor: Cursor?): QueueEntry? = cursor?.let { entries.getOrNull(it.index) }

    /** Index of the ayah entry of [ayah] in [segment]. */
    fun indexOf(segment: Int, ayah: AyahId): Int? =
        entries.indexOfFirst { it.kind == QueueKind.AYAH && it.segment == segment && it.ayah == ayah }.takeIf { it >= 0 }

    /** Where playback from [ayah] begins: at its basmala when it has one. */
    fun startAt(segment: Int, ayah: AyahId): Cursor? {
        val at = indexOf(segment, ayah) ?: return null
        val before = entries.getOrNull(at - 1)
        return Cursor(if (before?.kind == QueueKind.BASMALA && before.segment == segment) at - 1 else at)
    }

    /**
     * What plays when the entry at [cursor] has played to its end: the same ayah again while its
     * repeats last, then the following entry; after the last one the beginning again if the passage
     * repeats, otherwise null — playback stops at the end of the scope.
     */
    fun after(cursor: Cursor, repeat: RepeatSettings): Cursor? {
        val entry = entries.getOrNull(cursor.index) ?: return null
        return when {
            entry.kind == QueueKind.AYAH && cursor.play < repeat.ayahTimes -> Cursor(cursor.index, cursor.play + 1)
            cursor.index + 1 < entries.size -> Cursor(cursor.index + 1)
            repeat.passage -> Cursor(0)
            else -> null
        }
    }

    /** What happens when the entry at [cursor] has played to its end. */
    sealed interface Step {
        data class Play(val cursor: Cursor) : Step

        /** Stop, ready at [cursor]: a sajda ayah, played when the listener asks for it. */
        data class WaitAt(val cursor: Cursor) : Step

        data object End : Step
    }

    /** [after], except that with [stopBeforeSajda] a sajda ayah is not entered by itself. */
    fun step(cursor: Cursor, repeat: RepeatSettings, stopBeforeSajda: Boolean): Step {
        val next = after(cursor, repeat) ?: return Step.End
        val entry = entries[next.index]
        return if (stopBeforeSajda && entry.sajda && next.index != cursor.index) Step.WaitAt(next) else Step.Play(next)
    }

    /** "Next": the following entry, whatever repeats are left; null at the end of the scope. */
    fun next(cursor: Cursor): Cursor? = if (cursor.index + 1 < entries.size) Cursor(cursor.index + 1) else null

    /**
     * "Previous": the entry before; from an ayah that follows its basmala, the ayah before that
     * basmala (the basmala then plays again on the way forward). Null at the beginning of the scope.
     */
    fun previous(cursor: Cursor): Cursor? {
        val before = cursor.index - 1
        if (before < 0 || before >= entries.size) return null
        val skipBasmala = entries[before].kind == QueueKind.BASMALA && entries.getOrNull(cursor.index)?.kind == QueueKind.AYAH
        return Cursor(if (skipBasmala && before > 0) before - 1 else before)
    }

    companion object {
        /**
         * @param basmala false for a reciter whose ayah-1 files already begin with the basmala.
         */
        fun build(segments: List<Segment>, index: QuranIndex, basmala: Boolean = true): PlaybackQueue {
            val entries = ArrayList<QueueEntry>(segments.sumOf { it.range.size } + 8)
            segments.forEachIndexed { s, segment ->
                for (id in segment.range) {
                    val ref = index.ref(id)
                    if (basmala && ref.ayah == 1 && index.surah(ref.surah).hasBasmala) {
                        entries += QueueEntry(QueueKind.BASMALA, id, ref.surah, s, segment.context)
                    }
                    entries += QueueEntry(QueueKind.AYAH, id, ref.surah, s, segment.context, sajda = index.sajdaOf(id) != SajdaType.NONE)
                }
            }
            return PlaybackQueue(entries)
        }
    }
}
