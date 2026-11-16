package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.Rank

/** Where a day total stands on the ladder: what Today's ring and the reader's pill show. */
data class RankProgress(
    val count: Int,
    /** [Rank.NONE] below 10: there is no rank yet, and the UI must not invent a name for it. */
    val rank: Rank,
    /** The rank above; null once Qintar is reached. */
    val next: Rank?,
    /** Ayahs still needed for [next]; 0 at the top. */
    val toNext: Int,
    /** `count / next.threshold`, so "47 of 50" looks nearly full; 1 at the top (§5.2). */
    val ringFraction: Float,
    /** The user's goal rank, if one is set (R-RNK-5). */
    val goal: Rank?,
    val goalMet: Boolean,
    /** Ayahs still needed for the goal; 0 when met or not set. */
    val toGoal: Int,
) {
    /**
     * The rank the caption counts down to: the goal while it is set and unmet ("53 to Qanit, your
     * goal"), otherwise the next rank.
     */
    val captionTarget: Rank? get() = if (goal != null && !goalMet) goal else next

    val captionTargetIsGoal: Boolean get() = goal != null && !goalMet

    val toCaptionTarget: Int get() = if (captionTargetIsGoal) toGoal else toNext
}

object RankCalculator {
    /** The highest rank whose threshold is at most [count] (§4.4). */
    fun rankFor(count: Int): Rank = Rank.entries.last { it.threshold <= count.coerceAtLeast(0) }

    fun progress(count: Int, goal: Rank? = null): RankProgress {
        val rank = rankFor(count)
        val next = rank.next
        val realGoal = goal?.takeIf { it != Rank.NONE }
        return RankProgress(
            count = count,
            rank = rank,
            next = next,
            toNext = next?.let { it.threshold - count } ?: 0,
            ringFraction = next?.let { count.coerceAtLeast(0).toFloat() / it.threshold } ?: 1f,
            goal = realGoal,
            goalMet = realGoal != null && count >= realGoal.threshold,
            toGoal = realGoal?.let { (it.threshold - count).coerceAtLeast(0) } ?: 0,
        )
    }

    /**
     * The rank to celebrate when a day total moves from [before] to [after] (R-RNK-4): the highest rank
     * newly reached, or null. A bulk mark that crosses several thresholds shows one effect, for the top one.
     */
    fun reached(before: Int, after: Int): Rank? {
        val newRank = rankFor(after)
        return newRank.takeIf { it.ordinal > rankFor(before).ordinal }
    }
}
