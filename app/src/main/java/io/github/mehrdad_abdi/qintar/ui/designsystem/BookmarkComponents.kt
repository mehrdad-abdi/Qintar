package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R

/** The three read states of §5.6 (R-BMK-8), shared by bookmarks, groups and their headers. */
enum class ReadState { NotRead, Partial, Done }

@Composable
private fun ReadState.label(): String = stringResource(
    when (this) {
        ReadState.NotRead -> R.string.read_state_not_read
        ReadState.Partial -> R.string.read_state_partial
        ReadState.Done -> R.string.read_state_done
    },
)

/**
 * The small state glyph in front of a compact row: empty circle, a ring filled to [progress],
 * or a filled check. Colour is never the only signal (§5.6).
 */
@Composable
fun ReadStateIndicator(
    state: ReadState,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    size: Dp = 22.dp,
) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        ReadState.Done -> Box(
            modifier.size(size).clip(QintarShapes.Pill).background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = colors.onPrimary, modifier = Modifier.size(size * 0.7f))
        }

        else -> Canvas(modifier.size(size)) {
            val stroke = 2.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(colors.outline.copy(alpha = 0.55f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (state == ReadState.Partial) {
                drawArc(
                    colors.primary, -90f, 360f * progress.coerceIn(0.04f, 1f), false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * A bookmark as a full card (Bookmarks tab, group page).
 *
 * - [ReadState.NotRead]: plain surface.
 * - [ReadState.Partial]: a thin primary bar at the foot, filled to [progress].
 * - [ReadState.Done]: primaryContainer, a filled check and the "Read today" label.
 *
 * @param meta quiet facts, e.g. "110 ayahs".
 * @param progressText today's progress, e.g. "42 / 110".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkCard(
    title: String,
    state: ReadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    schedule: String? = null,
    meta: String? = null,
    progressText: String? = null,
    progress: Float = 0f,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val done = state == ReadState.Done
    val ink = if (done) colors.onPrimaryContainer else colors.onSurface
    val quietInk = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant
    val stateLabel = state.label()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(QintarShapes.Card)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .semantics { stateDescription = stateLabel },
        shape = QintarShapes.Card,
        color = if (done) colors.primaryContainer else colors.surface,
        contentColor = ink,
        border = BorderStroke(Sizes.hairline, if (done) colors.primaryContainer else colors.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = QintarType.BodyStrong,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (progressText != null) {
                    Text(progressText, style = QintarType.Numeric, color = quietInk, modifier = Modifier.padding(start = Spacing.sm))
                }
                if (done) {
                    Spacer(Modifier.width(Spacing.sm))
                    ReadStateIndicator(ReadState.Done)
                }
            }
            if (description != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(description, style = QintarType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = quietInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (schedule != null || meta != null || done) {
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (schedule != null) {
                        ScheduleChip(
                            schedule,
                            containerColor = if (done) colors.surface.copy(alpha = 0.55f) else colors.surfaceContainer,
                            contentColor = quietInk,
                        )
                    }
                    if (meta != null) Text(meta, style = QintarType.Caption, color = quietInk, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (done) Text(stateLabel, style = QintarType.Label, color = ink, maxLines = 1)
                }
            }
            if (state == ReadState.Partial) {
                Spacer(Modifier.height(Spacing.md))
                QProgressBar(progress, Modifier.fillMaxWidth(), height = 4.dp)
            }
        }
    }
}

/**
 * A bookmark as one line inside a card (Today's "Available now", the Now view):
 * state glyph · title · trailing progress ("1 ayah", "37 / 100").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkRow(
    title: String,
    state: ReadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    schedule: String? = null,
    progress: Float = 0f,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val stateLabel = state.label()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = Spacing.card, vertical = Spacing.sm)
            .semantics { stateDescription = stateLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ReadStateIndicator(state, progress = progress)
        Text(title, style = QintarType.Body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = schedule == null))
        if (schedule != null) {
            ScheduleChip(schedule, icon = null)
            Spacer(Modifier.weight(1f))
        }
        if (trailing != null) {
            Text(trailing, style = QintarType.Numeric, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * Header of a group inside a list: name, time window, combined progress and **Read**, which
 * opens the whole group in the reader (§5.6). Follows the same three states as a bookmark.
 */
@Composable
fun GroupHeader(
    name: String,
    state: ReadState,
    modifier: Modifier = Modifier,
    window: String? = null,
    progressText: String? = null,
    readLabel: String = stringResource(R.string.action_read),
    onRead: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val stateLabel = state.label()
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (state == ReadState.Done) colors.primaryContainer else Color.Transparent)
            .then(click)
            .heightIn(min = 56.dp)
            .padding(start = Spacing.card, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs)
            .semantics { stateDescription = stateLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = QintarType.BodyStrong,
                color = if (state == ReadState.Done) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val sub = listOfNotNull(window, progressText).joinToString("  ·  ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = QintarType.Caption.copy(fontFeatureSettings = "tnum"),
                    color = if (state == ReadState.Done) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state == ReadState.Done) {
            ReadStateIndicator(ReadState.Done)
            Spacer(Modifier.width(Spacing.xs))
        } else if (onRead != null) {
            FilledTonalButton(
                onClick = onRead,
                modifier = Modifier.heightIn(min = 40.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
            ) { Text(readLabel, style = QintarType.Label) }
        }
    }
}

/** A collapsed section inside a list: "▸ Later today (3)". */
@Composable
fun CollapsibleRow(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = Spacing.card)
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(title, style = QintarType.Label, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatNumber(count), style = QintarType.Numeric, color = colors.onSurfaceVariant)
    }
}

/** Hairline used between rows inside a card and between ayahs. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(modifier.fillMaxWidth().height(Sizes.hairline).background(color))
}

@Preview(widthDp = 360)
@Composable
private fun BookmarkComponentsPreview() {
    QintarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                BookmarkCard("Ayat al-Kursi", ReadState.NotRead, {}, description = "Whoever reads it after every prayer…", schedule = "Daily", meta = "1 ayah")
                BookmarkCard("Al-Kahf", ReadState.Partial, {}, schedule = "Fridays", meta = "110 ayahs", progressText = "42 / 110", progress = 0.38f)
                BookmarkCard("Al-Mulk", ReadState.Done, {}, schedule = "Nightly", meta = "30 ayahs")
                QCard(contentPadding = PaddingValues(0.dp)) {
                    GroupHeader("Morning", ReadState.Partial, window = "08:00–12:00", progressText = "1 / 2", onRead = {})
                    BookmarkRow("Ayat al-Kursi", ReadState.NotRead, {}, trailing = "1 ayah")
                    BookmarkRow("Al-Ikhlas", ReadState.Partial, {}, trailing = "37 / 100", progress = 0.37f)
                    CollapsibleRow("Later today", 3, false, {})
                }
            }
        }
    }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun BookmarkComponentsDarkRtlPreview() {
    QintarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                BookmarkCard("سوره کهف", ReadState.Partial, {}, schedule = "جمعه‌ها", meta = "۱۱۰ آیه", progressText = "۴۲ / ۱۱۰", progress = 0.38f)
                BookmarkCard("سوره ملک", ReadState.Done, {}, schedule = "هر شب", meta = "۳۰ آیه")
            }
        }
    }
}
