package io.github.mehrdad_abdi.qintar.ui.designsystem.showcase

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.designsystem.BookmarkRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.CollapsibleRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.GroupHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.ProgressBarRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.ProgressRing
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankChip
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankLadder
import io.github.mehrdad_abdi.qintar.ui.designsystem.ReadState
import io.github.mehrdad_abdi.qintar.ui.designsystem.RingCount
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekDayUi
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekStrip
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankShortName
import io.github.mehrdad_abdi.qintar.ui.navigation.QintarBottomBar
import io.github.mehrdad_abdi.qintar.ui.navigation.TopLevelDestination

/**
 * The Today wireframe of DESIGN.md §5.2, built only from design-system components with fixed
 * sample data. It is the visual reference for the Today feature and the subject of the
 * screenshot tests; it is not a screen of the app. It lays out at its full height (no scrolling)
 * so that one screenshot shows everything.
 */
@Composable
fun TodayShowcase(modifier: Modifier = Modifier) {
    val t = showcaseText()
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(colors.background)) {
        // Top bar: the date in the user's calendar, reminders, settings.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.date, style = QintarType.Title, color = colors.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = {}, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.NotificationsNone, t.notifications, tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = {}, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.Settings, t.settings, tint = colors.onSurfaceVariant)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))
            val distance = stringResource(R.string.rank_distance, formatNumber(3), rankShortName(2))
            ProgressRing(progress = 47f / 50f, contentDescription = "${formatNumber(47)}, $distance") {
                RingCount(count = 47, caption = t.ofNext, secondaryCaption = distance)
            }
            Spacer(Modifier.height(Spacing.xl))
            RankChip(rank = 1)
            Spacer(Modifier.height(Spacing.xl))
            RankLadder(currentRank = 1, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg))

            Spacer(Modifier.height(Spacing.xl))
            QCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
                    Text(t.streak, style = QintarType.Label, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(Spacing.md))
                WeekStrip(sampleWeek(t))
            }

            Spacer(Modifier.height(Spacing.betweenSections))
            SectionHeader(t.continueKhatm)
            Spacer(Modifier.height(Spacing.sm))
            QCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.khatmName, style = QintarType.BodyStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
                    Text(t.khatmPage, style = QintarType.Numeric, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(Spacing.md))
                ProgressBarRow(progress = 0.33f, valueText = t.khatmPercent)
                Spacer(Modifier.height(Spacing.sm))
                ProgressBarRow(progress = 0.625f, valueText = t.khatmToday, label = t.today)
                Spacer(Modifier.height(Spacing.lg))
                Button(
                    onClick = {},
                    modifier = Modifier.align(Alignment.End).heightIn(min = Sizes.touchTarget),
                    contentPadding = PaddingValues(horizontal = Spacing.xxl),
                ) { Text(t.continueAction, style = QintarType.Label) }
            }

            Spacer(Modifier.height(Spacing.betweenSections))
            SectionHeader(t.availableNow, trailing = t.left)
            Spacer(Modifier.height(Spacing.sm))
            QCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                GroupHeader(t.morning, ReadState.NotRead, window = t.morningWindow, onRead = {})
                BookmarkRow(t.ayatAlKursi, ReadState.NotRead, onClick = {}, trailing = t.oneAyah)
                BookmarkRow(t.ikhlas, ReadState.Partial, onClick = {}, trailing = t.ikhlasProgress, progress = 0.37f)
                Hairline(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.xs))
                Text(
                    t.ungrouped,
                    style = QintarType.BodyStrong,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.card, vertical = Spacing.sm),
                )
                BookmarkRow(t.kahf, ReadState.Partial, onClick = {}, trailing = t.kahfProgress, schedule = t.fridays, progress = 42f / 110f)
                Hairline(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.xs))
                CollapsibleRow(t.laterToday, 3, expanded = false, onToggle = {})
                CollapsibleRow(t.done, 1, expanded = false, onToggle = {})
            }

            Spacer(Modifier.height(Spacing.xl))
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(t.logReading, style = QintarType.Label)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
        QintarBottomBar(TopLevelDestination.Today) {}
    }
}

private fun sampleWeek(t: ShowcaseText): List<WeekDayUi> {
    val ranks = listOf(3, 3, 1, 4, 2, 0)
    return ranks.mapIndexed { i, rank -> WeekDayUi(t.weekdays[i], rank, contentDescription = t.weekdayNames[i]) } +
        WeekDayUi(t.weekdays[6], rank = 1, count = 47, isToday = true, contentDescription = t.weekdayNames[6])
}

@Preview(widthDp = 360)
@Composable
private fun TodayShowcasePreview() {
    QintarTheme(darkTheme = false) { TodayShowcase() }
}

@Preview(widthDp = 360)
@Composable
private fun TodayShowcaseDarkPreview() {
    QintarTheme(darkTheme = true) { TodayShowcase() }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun TodayShowcaseRtlPreview() {
    QintarTheme(darkTheme = false) { TodayShowcase() }
}
