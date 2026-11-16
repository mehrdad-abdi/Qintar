package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The seven thresholds of §4.4, for chart guides. Rules live in `:domain`; this is drawing data. */
val RankThresholds = listOf(10, 50, 100, 200, 300, 500, 1000)

/**
 * The rank ramp for chart bars: one hue (gold) growing from light to full with the rank, so
 * the ramp reads as magnitude; rank 0 is neutral.
 */
@Composable
@ReadOnlyComposable
fun rankRampColor(rank: Int): Color {
    val colors = MaterialTheme.colorScheme
    if (rank <= 0) return lerp(colors.surface, colors.onSurfaceVariant, 0.35f)
    val t = (rank.coerceAtMost(RankCount) - 1) / (RankCount - 1f)
    return lerp(colors.surface, QintarTheme.extendedColors.gold, 0.45f + 0.55f * t)
}

/** One bar of [DayBarChart]. */
data class DayBarUi(
    /** Axis label; empty to leave the tick unlabelled (label every nth day on long ranges). */
    val label: String,
    val count: Int,
    val rank: Int,
    /** "Monday, 120 ayahs, Qanit" — the text alternative of the bar. */
    val contentDescription: String,
)

/**
 * Daily totals as bars coloured by rank reached, with faint guides at the rank thresholds that
 * fall inside the y-range (§5.7). The x-axis follows layout direction.
 */
@Composable
fun DayBarChart(
    bars: List<DayBarUi>,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
    thresholds: List<Int> = RankThresholds,
    selectedIndex: Int? = null,
    onBarClick: ((index: Int) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val maxCount = (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(thresholds.firstOrNull() ?: 1)
    // Head-room so the tallest bar does not touch the top and the next guide can show.
    val yMax = maxCount * 1.12f
    val guides = thresholds.filter { it <= yMax }.takeLast(3)
    val guideColor = colors.outlineVariant
    val axisWidth = 36.dp

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(height)) {
            // Threshold labels in ink, at the start edge.
            Box(Modifier.width(axisWidth).fillMaxHeight()) {
                guides.forEach { value ->
                    val fraction = 1f - value / yMax
                    Text(
                        text = formatNumber(value),
                        style = QintarType.Caption.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = (height * fraction - 8.dp).coerceAtLeast(0.dp))
                            .clearAndSetSemantics { },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        guides.forEach { value ->
                            val y = size.height * (1f - value / yMax)
                            drawLine(guideColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        }
                        drawLine(guideColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                    },
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEachIndexed { index, bar ->
                    val click = if (onBarClick != null) Modifier.clickable(role = Role.Button) { onBarClick(index) } else Modifier
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(click)
                            .background(if (index == selectedIndex) colors.surfaceContainer else Color.Transparent)
                            .clearAndSetSemantics { contentDescription = bar.contentDescription },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (bar.count > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.72f)
                                    .widthIn(max = 28.dp)
                                    .fillMaxHeight((bar.count / yMax).coerceIn(0.015f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(rankRampColor(bar.rank)),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = axisWidth, top = Spacing.xs)) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = QintarType.Caption,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
    }
}

/** One day of [MonthGrid]. */
data class MonthDayUi(
    /** Day of month in the chosen calendar system and display numerals. */
    val dayLabel: String,
    val rank: Int,
    val count: Int = 0,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    /** "15 November, 120 ayahs, Qanit". */
    val contentDescription: String = dayLabel,
)

/**
 * A month as a 7-column grid; each cell holds the day number, that day's rank mark (16dp) and
 * the count beneath (§5.7). The caller supplies the weekday labels in week-start order and the
 * number of [leadingBlanks] before day 1; rows follow layout direction.
 */
@Composable
fun MonthGrid(
    weekdayLabels: List<String>,
    leadingBlanks: Int,
    days: List<MonthDayUi>,
    modifier: Modifier = Modifier,
    onDayClick: (index: Int) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    style = QintarType.Caption,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        val cells: List<Int?> = List(leadingBlanks) { null } + days.indices
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { index ->
                    if (index == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val day = days[index]
                        val click = if (day.isFuture) Modifier else Modifier.clickable(role = Role.Button) { onDayClick(index) }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 1.dp)
                                .clip(QintarShapes.Inner)
                                .background(if (day.isToday) colors.primaryContainer else Color.Transparent)
                                .then(click)
                                .heightIn(min = 60.dp)
                                .padding(vertical = Spacing.xs)
                                .clearAndSetSemantics { contentDescription = day.contentDescription },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            val ink = when {
                                day.isToday -> colors.onPrimaryContainer
                                day.isFuture -> colors.onSurfaceVariant
                                else -> colors.onSurface
                            }
                            Text(
                                day.dayLabel,
                                style = QintarType.Caption.copy(fontFeatureSettings = "tnum"),
                                fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
                                color = ink,
                            )
                            Box(Modifier.height(Sizes.rankMarkSmall), contentAlignment = Alignment.Center) {
                                if (!day.isFuture) RankMark(day.rank, size = Sizes.rankMarkSmall, contentDescription = null)
                            }
                            Text(
                                text = if (day.count > 0 && !day.isFuture) formatNumber(day.count) else "",
                                style = QintarType.Caption.copy(fontFeatureSettings = "tnum"),
                                color = if (day.isToday) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

internal val previewBars = listOf(12, 55, 0, 130, 47, 210, 320, 8, 64, 101, 0, 75, 510, 47).mapIndexed { i, n ->
    val rank = RankThresholds.count { it <= n }
    DayBarUi(label = if (i % 2 == 0) "${i + 1}" else "", count = n, rank = rank, contentDescription = "Day ${i + 1}, $n ayahs")
}

internal val previewMonth = List(30) { i ->
    val n = listOf(12, 55, 0, 130, 47, 210, 320, 8, 64, 101, 0, 75, 510, 1040, 47)[i % 15]
    MonthDayUi(
        dayLabel = "${i + 1}",
        rank = if (i >= 15) 0 else RankThresholds.count { it <= n },
        count = if (i >= 15) 0 else n,
        isToday = i == 14,
        isFuture = i > 14,
    )
}

@Preview(widthDp = 360)
@Composable
private fun InsightComponentsPreview() {
    QintarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                QCard { DayBarChart(previewBars) }
                QCard { MonthGrid(listOf("S", "M", "T", "W", "T", "F", "S"), 2, previewMonth) }
            }
        }
    }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun InsightComponentsDarkRtlPreview() {
    QintarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                QCard { DayBarChart(previewBars) }
                QCard { MonthGrid(listOf("ش", "ی", "د", "س", "چ", "پ", "ج"), 2, previewMonth) }
            }
        }
    }
}
