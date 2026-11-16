package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R

/** State of the check at the end of an ayah's meta line (§5.4.1). */
enum class AyahCheckState {
    /** Not read today: outline. */
    Unread,

    /** Read today in this context: filled primary; shows ×n from the second reading. */
    ReadToday,

    /** Covered on an earlier day (khatm only): filled, muted. */
    CoveredEarlier,
}

/**
 * One ayah in the reader: the text block, then the meta line
 * `reference · ⋯ · star · play · check ×n`.
 *
 * @param text the display string, already formatted (pause-mark line breaks, ayah-end marker —
 *   see [Numerals.ayahEndMarker]). It is drawn right-to-left in the Quran font whatever the app
 *   language.
 * @param reference "8:1" in display numerals.
 * @param readCount readings today in this context; "×n" appears from 2.
 * @param accessibilityLabel "Al-Anfal, ayah 1"; the state is appended for TalkBack.
 * @param sajdaLabel shown on the meta line for a sajda ayah.
 * @param onTextClick opens the ayah sheet (§5.4.3).
 * @param onPlay null hides the play control (a reader without audio).
 */
@Composable
fun AyahRow(
    text: String,
    reference: String,
    checkState: AyahCheckState,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    readCount: Int = if (checkState == AyahCheckState.ReadToday) 1 else 0,
    starred: Boolean = false,
    playing: Boolean = false,
    sajdaLabel: String? = null,
    fontSizeSp: Int = QuranTextSizeDefaultSp,
    accessibilityLabel: String = reference,
    onTextClick: () -> Unit = {},
    onPlay: (() -> Unit)? = {},
    onStar: () -> Unit = {},
    onMenu: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val stateText = when (checkState) {
        AyahCheckState.Unread -> stringResource(R.string.ayah_state_unread)
        AyahCheckState.ReadToday ->
            if (readCount > 1) stringResource(R.string.ayah_state_read_times, formatNumber(readCount))
            else stringResource(R.string.ayah_state_read_today)
        AyahCheckState.CoveredEarlier -> stringResource(R.string.ayah_state_covered_earlier)
    }
    val markReadLabel = stringResource(R.string.ayah_action_mark_read)
    val bookmarkLabel = stringResource(R.string.ayah_action_bookmark)
    val playLabel = stringResource(R.string.ayah_action_play)
    val rowTint = if (playing) colors.primaryContainer.copy(alpha = 0.35f) else Color.Transparent

    Column(modifier.fillMaxWidth().background(rowTint)) {
        // The Quran font carries its own script; TalkBack gets the reference and state instead
        // of a letter-by-letter reading, plus the row's actions.
        Text(
            text = text,
            style = quranTextStyle(fontSizeSp),
            color = colors.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.ayah_action_details), onClick = onTextClick)
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md)
                .clearAndSetSemantics {
                    contentDescription = "$accessibilityLabel, $stateText"
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(markReadLabel) { onCheck(); true },
                        CustomAccessibilityAction(bookmarkLabel) { onStar(); true },
                        onPlay?.let { play -> CustomAccessibilityAction(playLabel) { play(); true } },
                    )
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = reference,
                style = QintarType.Numeric,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { },
            )
            if (sajdaLabel != null) {
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = sajdaLabel,
                    style = QintarType.Caption,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .clip(QintarShapes.Pill)
                        .background(colors.surfaceContainer)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMenu, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.ayah_action_more), tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = onStar, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(
                    if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    stringResource(if (starred) R.string.ayah_bookmarked else R.string.ayah_action_bookmark),
                    tint = if (starred) colors.primary else colors.onSurfaceVariant,
                )
            }
            if (onPlay != null) Box(Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
                if (playing) PlayingRing()
                IconButton(onClick = onPlay, modifier = Modifier.size(Sizes.touchTarget)) {
                    // The play triangle points along the reading direction of the audio control,
                    // which by platform convention is not mirrored.
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.ayah_action_pause else R.string.ayah_action_play),
                        tint = if (playing) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
            AyahCheck(checkState, readCount, stateText, onCheck)
        }
    }
}

@Composable
private fun PlayingRing() {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "playing")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "phase")
    Canvas(Modifier.size(36.dp)) {
        drawCircle(color, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        // A second ring breathes outward while audio plays.
        drawCircle(
            color.copy(alpha = 0.35f * (1f - phase)),
            radius = size.minDimension / 2f + 5.dp.toPx() * phase,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

@Composable
private fun AyahCheck(state: AyahCheckState, readCount: Int, stateText: String, onCheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.ayah_action_mark_read)
    Row(
        modifier = Modifier
            .heightIn(min = Sizes.touchTarget)
            .widthIn(min = Sizes.touchTarget)
            .clip(QintarShapes.Pill)
            .clickable(role = Role.Button, onClick = onCheck)
            .padding(horizontal = Spacing.sm + Spacing.xxs)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = stateText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val circle = Modifier.size(28.dp).clip(QintarShapes.Pill)
        when (state) {
            AyahCheckState.Unread -> Box(circle.border(2.dp, colors.outline.copy(alpha = 0.6f), QintarShapes.Pill))
            AyahCheckState.ReadToday -> Box(circle.background(colors.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
            AyahCheckState.CoveredEarlier -> Box(circle.background(colors.outlineVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        if (state == AyahCheckState.ReadToday && readCount > 1) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.times_n, formatNumber(readCount)),
                style = QintarType.Numeric,
                color = colors.primary,
            )
        }
    }
}

/** A small eight-pointed star outline used as a typographic ornament (never gold). */
@Composable
private fun Ornament(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Canvas(modifier.size(12.dp)) { drawRankMark(3, color, filled = false) }
}

/**
 * Start of a surah: the name between two ornaments, with a quiet line of facts
 * ("75 ayahs · Medinan").
 */
@Composable
fun SurahHeader(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
            .clip(QintarShapes.Inner)
            .background(colors.surfaceContainer)
            .border(Sizes.hairline, colors.outlineVariant, QintarShapes.Inner)
            .padding(vertical = Spacing.md, horizontal = Spacing.lg)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Ornament()
            Text(name, style = QintarType.Title, color = colors.onSurface, textAlign = TextAlign.Center)
            Ornament()
        }
        if (subtitle != null) {
            Text(subtitle, style = QintarType.Caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The basmala as its own centred row. Not an ayah: no check, never counted (§5.4.2). The gap
 * beneath it is part of the row: two lines of text at the reader's font size.
 */
@Composable
fun BasmalaRow(
    modifier: Modifier = Modifier,
    fontSizeSp: Int = QuranTextSizeDefaultSp,
) {
    val style = quranTextStyle(fontSizeSp)
    Text(
        text = BASMALA,
        style = style.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = with(LocalDensity.current) { (style.fontSize * 2).toDp() })
            .semantics { contentDescription = BASMALA_PLAIN },
    )
}

/** Tanzil Uthmani spelling, identical to 1:1. */
private const val BASMALA =
    "بِسْمِ ٱللَّهِ " +
        "ٱلرَّحْمَـٰنِ " +
        "ٱلرَّحِيمِ"
private const val BASMALA_PLAIN = "بسم الله الرحمن الرحيم"

/**
 * Divider between pages: "Page 201 · Juz 10 · Hizb 19" between two hairlines. A juz start gets
 * a prominent [juzLabel]; [actionLabel] is the "Mark page 200 read" button for the page above.
 */
@Composable
fun PageDivider(
    text: String,
    modifier: Modifier = Modifier,
    juzLabel: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = Sizes.touchTarget).padding(bottom = Spacing.xs),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(actionLabel, style = QintarType.Label)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Hairline(Modifier.weight(1f))
            Text(text, style = QintarType.Caption.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant, maxLines = 1)
            Hairline(Modifier.weight(1f))
        }
        if (juzLabel != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = juzLabel,
                style = QintarType.Label,
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .clip(QintarShapes.Pill)
                    .background(colors.primaryContainer)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                    .semantics { heading() },
            )
        }
    }
}

/**
 * The reader's position bar: where the first visible ayah is. Tap opens the position sheet.
 *
 * @param primary "Page 200 of 604 · 33%"
 * @param secondary "Juz 10"
 */
@Composable
fun PositionBar(
    primary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
        shape = QintarShapes.Inner,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                primary,
                style = QintarType.Label.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (secondary != null) {
                Text(secondary, style = QintarType.Label.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant, maxLines = 1)
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                stringResource(R.string.position_bar_open),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconSmall),
            )
        }
    }
}

/**
 * Floating audio control, shown only while audio is active:
 * `play/pause · title · speed · close`.
 *
 * @param title "Al-Anfal 2 · Alafasy"
 * @param speedLabel "1×"
 */
@Composable
fun MiniPlayer(
    title: String,
    isPlaying: Boolean,
    speedLabel: String,
    onPlayPause: () -> Unit,
    onSpeed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = QintarShapes.Pill,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
        border = BorderStroke(Sizes.hairline, colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.then(click).heightIn(min = 52.dp).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(Sizes.touchTarget)) {
                Box(Modifier.size(36.dp).clip(QintarShapes.Pill).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = colors.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                title,
                style = QintarType.Label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
            )
            val speedDescription = stringResource(R.string.player_speed, speedLabel)
            TextButton(
                onClick = onSpeed,
                modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics { contentDescription = speedDescription },
            ) {
                Text(speedLabel, style = QintarType.Numeric)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.player_close), tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun ReaderComponentsPreview() {
    QintarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                PositionBar("Page 200 of 604 · 33%", {}, Modifier.padding(Spacing.screen), secondary = "Juz 10")
                SurahHeader("Al-Anfal", subtitle = "75 ayahs · Medinan")
                BasmalaRow()
                AyahRow(BASMALA + " " + Numerals.ayahEndMarker(1), "8:1", AyahCheckState.Unread, {})
                Hairline(Modifier.padding(horizontal = Spacing.screen))
                AyahRow(BASMALA + " " + Numerals.ayahEndMarker(2), "8:2", AyahCheckState.ReadToday, {}, readCount = 2, starred = true)
                PageDivider("Page 201 · Juz 10 · Hizb 19", actionLabel = "Mark page 200 read", onAction = {})
                MiniPlayer("Al-Anfal 2 · Alafasy", true, "1×", {}, {}, {}, Modifier.padding(Spacing.screen))
            }
        }
    }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun ReaderComponentsDarkRtlPreview() {
    QintarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                SurahHeader("انفال", subtitle = "۷۵ آیه · مدنی")
                AyahRow(BASMALA + " " + Numerals.ayahEndMarker(3), "۸:۳", AyahCheckState.CoveredEarlier, {}, playing = true)
            }
        }
    }
}
