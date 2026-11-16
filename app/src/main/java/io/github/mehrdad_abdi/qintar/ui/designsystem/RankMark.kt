package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Number of ranks on the ladder (§4.4). Rank 0 means "no rank yet". */
const val RankCount = 7

enum class RankMarkState {
    /** Gold. */
    Reached,

    /** The same figure as a quiet outline. */
    Unreached,
}

private val rankNameRes = intArrayOf(
    R.string.rank_name_not_heedless,
    R.string.rank_name_dhakir,
    R.string.rank_name_qanit,
    R.string.rank_name_khashi,
    R.string.rank_name_faiz,
    R.string.rank_name_mujtahid,
    R.string.rank_name_qintar,
)

private val rankShortNameRes = intArrayOf(
    R.string.rank_short_not_heedless,
    R.string.rank_short_dhakir,
    R.string.rank_short_qanit,
    R.string.rank_short_khashi,
    R.string.rank_short_faiz,
    R.string.rank_short_mujtahid,
    R.string.rank_short_qintar,
)

/** Full name of rank 1..7 ("Dhakir · the remembering"). */
@Composable
@ReadOnlyComposable
fun rankName(rank: Int): String = stringResource(rankNameRes[rank.coerceIn(1, RankCount) - 1])

/** Name used inside sentences ("3 to Dhakir"). */
@Composable
@ReadOnlyComposable
fun rankShortName(rank: Int): String = stringResource(rankShortNameRes[rank.coerceIn(1, RankCount) - 1])

/**
 * One mark of the parametric family of §6.4.
 *
 * @param rank 1..7; 0 draws the small muted dot used for a day without a rank.
 * @param size 16dp ([Sizes.rankMarkSmall]) is the smallest supported size.
 * @param tint overrides the state colour, for marks on a tinted container.
 * @param contentDescription defaults to the rank's name; pass null when a parent describes it.
 */
@Composable
fun RankMark(
    rank: Int,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.rankMark,
    state: RankMarkState = RankMarkState.Reached,
    tint: Color = Color.Unspecified,
    contentDescription: String? = if (rank in 1..RankCount) rankName(rank) else null,
) {
    val color = when {
        tint != Color.Unspecified -> tint
        rank <= 0 -> MaterialTheme.colorScheme.outlineVariant
        state == RankMarkState.Reached -> QintarTheme.extendedColors.gold
        // §6.4 says outlineVariant, but that hairline tone is about 1.3:1 on paper: too faint
        // for a figure whose shape matters. The outline is drawn part-way toward the quiet ink.
        else -> androidx.compose.ui.graphics.lerp(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            0.35f,
        )
    }
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Canvas(modifier.size(size).then(semantics)) {
        drawRankMark(rank, color, filled = state == RankMarkState.Reached)
    }
}

/** A star polygon with [points] tips, alternating between [outer] and [inner] radius, tip up. */
private fun starPath(center: Offset, points: Int, outer: Float, inner: Float): Path {
    val path = Path()
    val step = PI / points
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * step
        val x = center.x + (r * cos(a)).toFloat()
        val y = center.y + (r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Inner/outer ratio of the Rub el Hizb star (two squares overlaid at 45°). */
private const val EIGHT_STAR_RATIO = 0.7654f

/**
 * Draws rank [rank] centred in the draw area. Shared by [RankMark] and by charts that place
 * marks themselves.
 */
fun DrawScope.drawRankMark(rank: Int, color: Color, filled: Boolean = true) {
    val c = center
    val r = size.minDimension / 2f
    // Strokes never go below ~1.25dp so the figure survives at 16dp.
    val thin = max(r * 0.12f, 1.25.dp.toPx())
    val outline = Stroke(width = thin, join = StrokeJoin.Round)
    val body: DrawStyle = if (filled) Fill else outline
    // An outlined figure is inset by half the stroke so it does not grow past its box.
    val inset = if (filled) 0f else thin / 2f

    when (rank.coerceIn(0, RankCount)) {
        0 -> drawCircle(color, radius = r * 0.16f, center = c)

        1 -> {
            drawCircle(color, radius = r * 0.80f - thin / 2f, center = c, style = outline)
            drawCircle(color, radius = r * 0.30f, center = c, style = body)
        }

        2 -> drawPath(starPath(c, 2, r * 0.92f - inset, r * 0.92f - inset), color, style = body)

        3 -> {
            val outer = r * 0.98f - thin / 2f
            drawPath(starPath(c, 8, outer, outer * EIGHT_STAR_RATIO), color, style = outline)
        }

        4 -> {
            val outer = r * 0.98f - inset
            drawPath(starPath(c, 8, outer, outer * EIGHT_STAR_RATIO), color, style = body)
        }

        5 -> {
            drawCircle(color, radius = r - thin / 2f, center = c, style = outline)
            val outer = r * 0.66f - inset
            drawPath(starPath(c, 8, outer, outer * EIGHT_STAR_RATIO), color, style = body)
        }

        6 -> {
            val outer = r * 0.98f - inset
            val rosette = starPath(c, 12, outer, outer * 0.74f)
            if (filled) {
                rosette.fillType = PathFillType.EvenOdd
                rosette.addOval(androidx.compose.ui.geometry.Rect(c, r * 0.26f))
                drawPath(rosette, color)
            } else {
                drawPath(rosette, color, style = outline)
                drawCircle(color, radius = r * 0.20f, center = c, style = outline)
            }
        }

        else -> {
            if (filled) {
                // The only mark with a glow (§6.4).
                drawCircle(
                    brush = Brush.radialGradient(
                        0.55f to color.copy(alpha = 0.38f),
                        1f to Color.Transparent,
                        center = c,
                        radius = r * 1.45f,
                    ),
                    radius = r * 1.45f,
                    center = c,
                )
            }
            drawCircle(color, radius = r - thin / 2f, center = c, style = outline)
            val outer = r * 0.74f - inset
            val rosette = starPath(c, 16, outer, outer * 0.72f)
            if (filled) {
                rosette.fillType = PathFillType.EvenOdd
                rosette.addOval(androidx.compose.ui.geometry.Rect(c, r * 0.20f))
                drawPath(rosette, color)
            } else {
                drawPath(rosette, color, style = outline)
            }
        }
    }
}

/** The current rank as a chip: mark and full name on the gold container. Nothing for rank 0. */
@Composable
fun RankChip(rank: Int, modifier: Modifier = Modifier) {
    if (rank !in 1..RankCount) return
    val extended = QintarTheme.extendedColors
    val name = rankName(rank)
    Row(
        modifier = modifier
            .clip(QintarShapes.Pill)
            .background(extended.goldContainer)
            .padding(start = Spacing.md, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm)
            .clearAndSetSemantics { contentDescription = name },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // goldInk, not gold: the mark sits on the gold container here and must keep 3:1.
        RankMark(rank, size = 20.dp, tint = extended.goldInk, contentDescription = null)
        androidx.compose.material3.Text(name, style = QintarType.Label, color = extended.goldInk, maxLines = 1)
    }
}

/**
 * The seven marks in a row; those up to [currentRank] are reached. An optional [goalRank]
 * (R-RNK-5) gets a small tick beneath its mark.
 */
@Composable
fun RankLadder(
    currentRank: Int,
    modifier: Modifier = Modifier,
    markSize: Dp = Sizes.rankMark,
    goalRank: Int? = null,
) {
    val description = if (currentRank in 1..RankCount) {
        stringResource(R.string.rank_ladder_description, formatNumber(currentRank), formatNumber(RankCount), rankName(currentRank))
    } else {
        stringResource(R.string.rank_ladder_none_description)
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        for (rank in 1..RankCount) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RankMark(
                    rank = rank,
                    size = markSize,
                    state = if (rank <= currentRank) RankMarkState.Reached else RankMarkState.Unreached,
                    contentDescription = null,
                )
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    Modifier
                        .size(width = 12.dp, height = 3.dp)
                        .clip(CircleShape)
                        .background(if (rank == goalRank) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Preview(widthDp = 320)
@Composable
private fun RankMarkPreview() {
    QintarTheme(darkTheme = false) {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    for (rank in 0..RankCount) RankMark(rank, size = 32.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    for (rank in 0..RankCount) RankMark(rank, size = Sizes.rankMarkSmall)
                }
                RankLadder(currentRank = 2, goalRank = 3, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(widthDp = 320, locale = "fa")
@Composable
private fun RankMarkDarkRtlPreview() {
    QintarTheme(darkTheme = true) {
        Surface {
            RankLadder(currentRank = 5, modifier = Modifier.fillMaxWidth().padding(Spacing.lg))
        }
    }
}
