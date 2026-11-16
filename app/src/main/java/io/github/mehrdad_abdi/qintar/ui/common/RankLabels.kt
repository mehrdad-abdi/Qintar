package io.github.mehrdad_abdi.qintar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.service.RankProgress
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankShortName
import java.time.LocalDate

/** The design system numbers ranks 1…7, and 0 for "none yet". */
val Rank.number: Int get() = ordinal

/**
 * What stands under today's count, on Today's ring and in the reader's pill (§5.2):
 * "10 ayahs to the first rank", "3 to Dhakir", "53 to Qanit, your goal", and nothing at the top.
 */
@Composable
@ReadOnlyComposable
fun rankCaption(progress: RankProgress): String {
    val target = progress.captionTarget ?: return ""
    val distance = formatNumber(progress.toCaptionTarget)
    return when {
        progress.captionTargetIsGoal -> stringResource(R.string.rank_distance_goal, distance, rankShortName(target.number))
        progress.rank == Rank.NONE -> stringResource(R.string.rank_distance_first, distance)
        else -> stringResource(R.string.rank_distance, distance, rankShortName(target.number))
    }
}

/** "Dhakir reached". */
@Composable
@ReadOnlyComposable
fun rankReachedText(rank: Rank): String = stringResource(R.string.rank_reached, rankShortName(rank.number))

/**
 * Decides when the rank-reached effect plays (R-RNK-4): when today's total crosses a threshold while
 * the screen is watching, once per rank per day. Feed it every (day, total) pair in order.
 *
 * The first total of a day is where the day stands, not an achievement, so it never fires; a total
 * that is lowered and raised again does not fire a second time for the same rank.
 */
class RankReachedDetector {
    private var day: LocalDate? = null
    private var total = 0
    private var highestShown = Rank.NONE

    fun onTotal(day: LocalDate, total: Int): Rank? {
        val sameDay = this.day == day
        val before = this.total
        this.day = day
        this.total = total
        if (!sameDay) {
            highestShown = RankCalculator.rankFor(total)
            return null
        }
        val reached = RankCalculator.reached(before, total)?.takeIf { it.ordinal > highestShown.ordinal } ?: return null
        highestShown = reached
        return reached
    }

    companion object {
        /** How long "Dhakir reached" stays in place of the count (§5.4.1). */
        const val SHOWN_FOR_MILLIS = 3_000L
    }
}
