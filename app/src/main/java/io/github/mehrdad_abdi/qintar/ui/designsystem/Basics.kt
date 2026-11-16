package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The card of the app: surface tone, 1dp hairline border, 20dp corners, no shadow (§6.3).
 */
@Composable
fun QCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = PaddingValues(Spacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(Sizes.hairline, borderColor)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = QintarShapes.Card,
            color = containerColor,
            contentColor = contentColor,
            border = border,
        ) { Column(Modifier.padding(contentPadding), content = content) }
    } else {
        Surface(
            modifier = modifier,
            shape = QintarShapes.Card,
            color = containerColor,
            contentColor = contentColor,
            border = border,
        ) { Column(Modifier.padding(contentPadding), content = content) }
    }
}

/**
 * Title of a screen section, with an optional quiet trailing note ("2 left") or a text action.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = QintarType.Title,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = QintarType.Label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(actionLabel, style = QintarType.Label)
            }
        }
    }
}

/** Thin rounded progress bar. Fills from the start edge, so it mirrors in RTL. */
@Composable
fun QProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    height: Dp = 6.dp,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, spring(stiffness = 300f), label = "bar")
    Box(
        modifier
            .height(height)
            .clip(QintarShapes.Pill)
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(QintarShapes.Pill)
                    .background(color),
            )
        }
    }
}

/**
 * A labelled progress bar on one line: "Today ▓▓▓░░ 2.5 of 4 pages". Used for the khatm bars
 * of §4.6. [label] may be null for the headline bar of a card.
 */
@Composable
fun ProgressBarRow(
    progress: Float,
    valueText: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val description = listOfNotNull(label, valueText).joinToString(", ")
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = QintarType.Label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        QProgressBar(progress, Modifier.weight(1f), color = color, trackColor = trackColor)
        Text(
            text = valueText,
            style = QintarType.Numeric,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Small quiet pill naming a schedule: "Fridays", "Daily 08–12". */
@Composable
fun ScheduleChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Outlined.Schedule,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .clip(QintarShapes.Pill)
            .background(containerColor)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        Text(text, style = QintarType.Caption, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One figure with its label: "1,240 · Total ayahs". Lay several out in a two-column grid. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    QCard(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label, $value" },
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.height(Spacing.sm))
        }
        Text(
            text = value,
            style = QintarType.Headline.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = QintarType.Label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What a list or a card shows when there is nothing yet, with at most one way forward. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(64.dp).clip(QintarShapes.Pill).background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(title, style = QintarType.Title, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                message,
                style = QintarType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(actionLabel, style = QintarType.Label)
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun BasicsPreview() {
    QintarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                SectionHeader("Available now", trailing = "2 left")
                QCard(Modifier.fillMaxWidth()) {
                    ProgressBarRow(0.33f, "33%")
                    Spacer(Modifier.height(Spacing.sm))
                    ProgressBarRow(0.62f, "2.5 of 4 pages", label = "Today")
                    Spacer(Modifier.height(Spacing.sm))
                    ScheduleChip("Fridays")
                }
                StatTile("1,240", "Total ayahs")
                EmptyState("No khatm yet", message = "Start a khatm to read front to back.", icon = Icons.Outlined.AutoStories, actionLabel = "Start a khatm", onAction = {})
            }
        }
    }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun BasicsDarkRtlPreview() {
    QintarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
                SectionHeader("اکنون در دسترس", trailing = "۲ مانده")
                QCard(Modifier.fillMaxWidth()) { ProgressBarRow(0.62f, "۲٫۵ از ۴ صفحه", label = "امروز") }
            }
        }
    }
}
