package io.github.mehrdad_abdi.qintar.ui.designsystem.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahCheckState
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.BasmalaRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.MiniPlayer
import io.github.mehrdad_abdi.qintar.ui.designsystem.Numerals
import io.github.mehrdad_abdi.qintar.ui.designsystem.PageDivider
import io.github.mehrdad_abdi.qintar.ui.designsystem.PositionBar
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.SurahHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.TodayPill
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankShortName

/** Which passage [ReaderShowcase] shows. */
enum class ReaderShowcaseSample {
    /** §5.4.1 as drawn: a khatm at Al-Anfal 1–2, mini player active. */
    Anfal,

    /** A bookmark reader on Ayat al-Kursi with the today pill in its rank-reached state. */
    AyatAlKursi,
}

/**
 * The Reader wireframe of DESIGN.md §5.4.1, built only from design-system components with fixed
 * sample data. Visual reference for the reader feature and the subject of screenshot tests.
 * Give it the size of a phone screen; the list is cut off at the bottom as a scrolled list is.
 */
@Composable
fun ReaderShowcase(
    modifier: Modifier = Modifier,
    sample: ReaderShowcaseSample = ReaderShowcaseSample.Anfal,
) {
    val t = showcaseText()
    val colors = MaterialTheme.colorScheme
    val anfal = sample == ReaderShowcaseSample.Anfal
    Box(modifier.fillMaxSize().background(colors.background).clipToBounds()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = colors.onSurface)
                }
                Text(
                    if (anfal) t.khatmName else t.ayatAlKursi,
                    style = QintarType.Title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
                )
                IconButton(onClick = {}, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.FormatSize, t.textSize, tint = colors.onSurfaceVariant)
                }
                IconButton(onClick = {}, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.MoreVert, t.more, tint = colors.onSurfaceVariant)
                }
            }
            PositionBar(
                primary = if (anfal) t.position else t.kursiPosition,
                secondary = if (anfal) t.juz else t.kursiJuz,
                onClick = {},
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
            if (anfal) AnfalRows(t) else KursiRows(t)
        }

        // Floating controls stay above the text; a fade keeps the last line from colliding.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to colors.background))
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xxl, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (anfal) {
                TodayPill(
                    rank = 1,
                    count = 47,
                    caption = stringResource(R.string.rank_distance, formatNumber(3), rankShortName(2)),
                    progress = 47f / 50f,
                    onClick = {},
                )
                MiniPlayer(t.playerTitle, isPlaying = true, speedLabel = t.speed, onPlayPause = {}, onSpeed = {}, onClose = {})
            } else {
                TodayPill(
                    rank = 2,
                    count = 50,
                    caption = "",
                    progress = 1f,
                    rankReached = true,
                    reachedText = stringResource(R.string.rank_reached, rankShortName(2)),
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun AnfalRows(t: ShowcaseText) {
    SurahHeader(t.anfal, subtitle = t.anfalFacts)
    BasmalaRow()
    Spacer(Modifier.height(Spacing.sm))
    AyahRow(
        text = displayText(SampleAyahs.ANFAL_1, 1),
        reference = localizeDigits("8:1"),
        checkState = AyahCheckState.Unread,
        onCheck = {},
        accessibilityLabel = "${t.anfalLabel} ${formatNumber(1)}",
    )
    Hairline(Modifier.padding(horizontal = Spacing.screen))
    AyahRow(
        text = displayText(SampleAyahs.ANFAL_2, 2),
        reference = localizeDigits("8:2"),
        checkState = AyahCheckState.ReadToday,
        readCount = 2,
        starred = true,
        playing = true,
        onCheck = {},
        accessibilityLabel = "${t.anfalLabel} ${formatNumber(2)}",
    )
    PageDivider(t.pageDivider, actionLabel = t.markPage, onAction = {})
    AyahRow(
        text = displayText(SampleAyahs.ANFAL_3, 3),
        reference = localizeDigits("8:3"),
        checkState = AyahCheckState.CoveredEarlier,
        onCheck = {},
        accessibilityLabel = "${t.anfalLabel} ${formatNumber(3)}",
    )
}

@Composable
private fun KursiRows(t: ShowcaseText) {
    Spacer(Modifier.height(Spacing.sm))
    AyahRow(
        text = displayText(SampleAyahs.AYAT_AL_KURSI, 255),
        reference = localizeDigits("2:255"),
        checkState = AyahCheckState.ReadToday,
        starred = true,
        onCheck = {},
        accessibilityLabel = "${t.baqarahLabel} ${formatNumber(255)}",
    )
}

/**
 * Minimal stand-in for the reader's formatter (§5.4.2): a line break at the first space after
 * ۖ ۗ ۘ ۚ, then the ayah-end marker. The stored text itself is not altered.
 */
internal fun displayText(ayah: String, number: Int, breakAtPauseMarks: Boolean = true): String {
    val body = if (breakAtPauseMarks) ayah.replace(Regex("([ۖۗۘۚ]) "), "$1\n") else ayah
    return body + " " + Numerals.ayahEndMarker(number)
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun ReaderShowcasePreview() {
    QintarTheme(darkTheme = false) { ReaderShowcase() }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun ReaderShowcaseDarkPreview() {
    QintarTheme(darkTheme = true) { ReaderShowcase() }
}

@Preview(widthDp = 360, heightDp = 800, locale = "fa")
@Composable
private fun ReaderShowcaseRtlPreview() {
    QintarTheme(darkTheme = false) { ReaderShowcase(sample = ReaderShowcaseSample.AyatAlKursi) }
}
