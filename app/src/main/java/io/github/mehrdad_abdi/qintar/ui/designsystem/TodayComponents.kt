package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R

/**
 * The gold ring of Today (§5.2): fills from 0 toward the next threshold. [content] is centred
 * inside; see [RingCount] for the usual count-and-caption block.
 *
 * @param progress 0..1 toward the next rank; pass 1 at rank 7.
 * @param contentDescription what TalkBack reads for the ring as a whole ("47 ayahs today, 3 to Dhakir").
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 224.dp,
    strokeWidth: Dp = 12.dp,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, spring(stiffness = 200f), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainer
    val gold = QintarTheme.extendedColors.gold
    val semantics = if (contentDescription != null) {
        Modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        }
    } else {
        Modifier
    }
    Box(modifier.size(size).then(semantics), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(
                    color = gold,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** The block inside the Today ring: the count in display type, then up to two quiet lines. */
@Composable
fun RingCount(
    count: Int,
    caption: String,
    modifier: Modifier = Modifier,
    secondaryCaption: String? = null,
) {
    Column(modifier.padding(horizontal = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatNumber(count),
            style = QintarType.Display,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = caption,
            style = QintarType.Label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (secondaryCaption != null) {
            Text(
                text = secondaryCaption,
                style = QintarType.BodyStrong,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One day of [WeekStrip]. */
data class WeekDayUi(
    /** Short weekday label in the app language ("S", "ش"). */
    val label: String,
    /** Rank reached that day, 0 for none. */
    val rank: Int,
    /** Shown instead of the mark when [isToday]. */
    val count: Int = 0,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    /** "Monday, 120 ayahs, Qanit". */
    val contentDescription: String = label,
)

/**
 * Seven days with each day's rank mark; today shows its running count. The caller passes the
 * days already ordered by the week-start setting; the row follows layout direction.
 */
@Composable
fun WeekStrip(
    days: List<WeekDayUi>,
    modifier: Modifier = Modifier,
    onDayClick: ((index: Int) -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { index, day ->
            val click = if (onDayClick != null && !day.isFuture) {
                Modifier.clip(QintarShapes.Inner).clickable(role = Role.Button) { onDayClick(index) }
            } else {
                Modifier
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(click)
                    .clearAndSetSemantics { contentDescription = day.contentDescription }
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = day.label,
                    style = QintarType.Caption,
                    color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                    when {
                        day.isToday -> Box(
                            Modifier
                                .heightIn(min = 32.dp)
                                .widthIn(min = 32.dp)
                                .clip(QintarShapes.Pill)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = Spacing.xs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = formatNumber(day.count),
                                style = QintarType.Numeric,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        day.isFuture -> Unit
                        else -> RankMark(day.rank, size = 22.dp, contentDescription = null)
                    }
                }
            }
        }
    }
}

/**
 * The reader's always-visible feedback loop (§5.4.1, R-RDR-10): rank mark, today's count,
 * distance to the next rank, and a thin bar toward it.
 *
 * @param rank current rank, 0 before the first.
 * @param caption distance text, e.g. "3 to Dhakir".
 * @param rankReached the transient state after a threshold is crossed: gold outline that pulses
 *   once, the new mark scales in, and [reachedText] ("Dhakir reached") replaces the count line.
 *   The caller turns it off after about three seconds.
 */
@Composable
fun TodayPill(
    rank: Int,
    count: Int,
    caption: String,
    progress: Float,
    modifier: Modifier = Modifier,
    rankReached: Boolean = false,
    reachedText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val extended = QintarTheme.extendedColors
    val colors = MaterialTheme.colorScheme
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(rankReached) {
        if (rankReached) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(durationMillis = 600))
        } else {
            pulse.snapTo(0f)
        }
    }
    val markScale by animateFloatAsState(1f + 0.35f * pulse.value, label = "mark")
    val border = if (rankReached) {
        BorderStroke(2.dp + 2.dp * pulse.value, extended.gold)
    } else {
        BorderStroke(Sizes.hairline, colors.outlineVariant)
    }
    val showReached = rankReached && reachedText != null
    val countText = formatNumber(count)
    val todayText = stringResource(R.string.today_pill_count, countText)
    val description = if (showReached) "$reachedText, $todayText" else "$todayText, $caption"

    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            RankMark(
                rank = rank,
                size = 26.dp,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { scaleX = markScale; scaleY = markScale },
            )
            if (showReached) {
                Text(
                    text = reachedText!!,
                    style = QintarType.BodyStrong,
                    color = extended.goldInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = buildAnnotatedString {
                        // The count is the emphasised part of "47 today".
                        val start = todayText.indexOf(countText)
                        if (start < 0) {
                            append(todayText)
                        } else {
                            append(todayText.substring(0, start))
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = QintarType.Body.fontSize, color = colors.onSurface)) { append(countText) }
                            append(todayText.substring(start + countText.length))
                        }
                        if (caption.isNotEmpty()) {
                            append("  ·  ")
                            append(caption)
                        }
                    },
                    style = QintarType.Label.copy(fontFeatureSettings = "tnum", lineHeight = QintarType.Body.lineHeight),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            QProgressBar(
                progress = if (rankReached) 1f else progress,
                modifier = Modifier.width(48.dp),
                color = extended.gold,
                trackColor = colors.outlineVariant,
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = QintarShapes.Pill,
            color = colors.surfaceContainer,
            contentColor = colors.onSurface,
            border = border,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = QintarShapes.Pill,
            color = colors.surfaceContainer,
            contentColor = colors.onSurface,
            border = border,
            content = body,
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun TodayComponentsPreview() {
    QintarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.padding(Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
            ) {
                ProgressRing(0.94f) { RingCount(47, "of 50", secondaryCaption = "3 to Dhakir") }
                WeekStrip(previewWeek)
                TodayPill(rank = 1, count = 47, caption = "3 to Dhakir", progress = 0.94f)
                TodayPill(rank = 2, count = 50, caption = "", progress = 1f, rankReached = true, reachedText = "Dhakir reached")
            }
        }
    }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun TodayComponentsDarkRtlPreview() {
    QintarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                WeekStrip(previewWeek)
                TodayPill(rank = 1, count = 47, caption = "۳ تا ذاکر", progress = 0.94f)
            }
        }
    }
}

private val previewWeek = listOf(
    WeekDayUi("S", 3), WeekDayUi("M", 3), WeekDayUi("T", 1), WeekDayUi("W", 4),
    WeekDayUi("T", 2), WeekDayUi("F", 0), WeekDayUi("S", 1, count = 47, isToday = true),
)
