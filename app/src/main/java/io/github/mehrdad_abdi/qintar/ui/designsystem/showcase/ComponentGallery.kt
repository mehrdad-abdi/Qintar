package io.github.mehrdad_abdi.qintar.ui.designsystem.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahCheckState
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.BasmalaRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.BookmarkCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.BookmarkRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.CollapsibleRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.CountField
import io.github.mehrdad_abdi.qintar.ui.designsystem.DayBarChart
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.GroupHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.MiniPlayer
import io.github.mehrdad_abdi.qintar.ui.designsystem.MonthGrid
import io.github.mehrdad_abdi.qintar.ui.designsystem.PageDivider
import io.github.mehrdad_abdi.qintar.ui.designsystem.PositionBar
import io.github.mehrdad_abdi.qintar.ui.designsystem.ProgressBarRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.ProgressRing
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankChip
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankCount
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankLadder
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankMark
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankMarkState
import io.github.mehrdad_abdi.qintar.ui.designsystem.ReadState
import io.github.mehrdad_abdi.qintar.ui.designsystem.RingCount
import io.github.mehrdad_abdi.qintar.ui.designsystem.ScheduleChip
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.StatTile
import io.github.mehrdad_abdi.qintar.ui.designsystem.SurahHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.TodayPill
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekDayUi
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekStrip
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekdayChips
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.designsystem.previewBars
import io.github.mehrdad_abdi.qintar.ui.designsystem.previewMonth
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankShortName

@Composable
private fun GalleryColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        content = content,
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Every rank mark at 32, 24 and 16dp, reached and unreached. The 16dp row is the §6.4 check. */
@Composable
fun RankMarkGallery(modifier: Modifier = Modifier) {
    GalleryColumn(modifier) {
        for ((size, state) in listOf(
            32.dp to RankMarkState.Reached,
            Sizes.rankMark to RankMarkState.Reached,
            Sizes.rankMarkSmall to RankMarkState.Reached,
            Sizes.rankMark to RankMarkState.Unreached,
            Sizes.rankMarkSmall to RankMarkState.Unreached,
        )) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Caption(localizeDigits("${size.value.toInt()}"))
                for (rank in 0..RankCount) RankMark(rank, size = size, state = state)
            }
        }
        QCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (rank in 0..RankCount) RankMark(rank, size = Sizes.rankMarkSmall)
            }
        }
        for (rank in 1..RankCount) RankChip(rank)
        RankLadder(currentRank = 4, goalRank = 5, modifier = Modifier.fillMaxWidth())
    }
}

/** Today, bookmark and general components. */
@Composable
fun ComponentGalleryA(modifier: Modifier = Modifier) {
    val t = showcaseText()
    GalleryColumn(modifier) {
        val distance = stringResource(R.string.rank_distance, formatNumber(3), rankShortName(2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(0f, size = 96.dp, strokeWidth = 8.dp)
            ProgressRing(0.4f, size = 96.dp, strokeWidth = 8.dp)
            ProgressRing(1f, size = 96.dp, strokeWidth = 8.dp)
        }
        QCard(Modifier.fillMaxWidth()) {
            WeekStrip(
                t.weekdays.mapIndexed { i, label ->
                    WeekDayUi(label, rank = listOf(7, 6, 5, 0, 2, 1, 0)[i], count = 1040, isToday = i == 5, isFuture = i == 6)
                },
            )
        }
        TodayPill(rank = 0, count = 4, caption = stringResource(R.string.rank_distance, formatNumber(6), rankShortName(1)), progress = 0.4f)
        TodayPill(rank = 1, count = 47, caption = distance, progress = 0.94f)
        TodayPill(rank = 2, count = 50, caption = "", progress = 1f, rankReached = true, reachedText = stringResource(R.string.rank_reached, rankShortName(2)))

        SectionHeader(t.availableNow, trailing = t.left)
        BookmarkCard(t.ayatAlKursi, ReadState.NotRead, {}, description = t.description, schedule = t.nightly, meta = t.oneAyah)
        BookmarkCard(t.kahf, ReadState.Partial, {}, schedule = t.fridays, meta = t.hundredTenAyahs, progressText = t.kahfProgress, progress = 42f / 110f)
        BookmarkCard(t.mulk, ReadState.Done, {}, description = t.description, schedule = t.nightly, meta = t.thirtyAyahs)
        QCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
            GroupHeader(t.morning, ReadState.Partial, window = t.morningWindow, progressText = localizeDigits("1 / 2"), onRead = {})
            BookmarkRow(t.ayatAlKursi, ReadState.Done, {}, trailing = t.oneAyah)
            BookmarkRow(t.ikhlas, ReadState.Partial, {}, trailing = t.ikhlasProgress, progress = 0.37f)
            BookmarkRow(t.kahf, ReadState.NotRead, {}, trailing = t.hundredTenAyahs, schedule = t.fridays)
            Hairline()
            CollapsibleRow(t.laterToday, 3, expanded = false, onToggle = {})
        }
        QCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            GroupHeader(t.morning, ReadState.Done, window = t.morningWindow, progressText = localizeDigits("2 / 2"))
        }
        QCard(Modifier.fillMaxWidth()) {
            ProgressBarRow(0.33f, t.khatmPercent)
            ProgressBarRow(0.625f, t.khatmToday, label = t.today, modifier = Modifier.padding(top = Spacing.sm))
            ScheduleChip(t.fridays, Modifier.padding(top = Spacing.md))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(localizeDigits("12,480"), t.totalAyahs, Modifier.weight(1f))
            StatTile(localizeDigits("212"), t.activeDays, Modifier.weight(1f))
        }
        QCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            EmptyState(t.emptyTitle, message = t.emptyMessage, icon = Icons.Outlined.AutoStories, actionLabel = t.emptyAction, onAction = {})
        }
    }
}

/** Reader, input and insight components. */
@Composable
fun ComponentGalleryB(modifier: Modifier = Modifier) {
    val t = showcaseText()
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        PositionBar(t.position, {}, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg), secondary = t.juz)
        SurahHeader(t.anfal, subtitle = t.anfalFacts)
        BasmalaRow()
        AyahRow(displayText(SampleAyahs.ANFAL_3, 3), localizeDigits("8:3"), AyahCheckState.Unread, {})
        Hairline(Modifier.padding(horizontal = Spacing.screen))
        AyahRow(displayText(SampleAyahs.ANFAL_3, 30), localizeDigits("8:30"), AyahCheckState.ReadToday, {}, readCount = 1, starred = true)
        Hairline(Modifier.padding(horizontal = Spacing.screen))
        AyahRow(displayText(SampleAyahs.ANFAL_3, 286), localizeDigits("2:286"), AyahCheckState.ReadToday, {}, readCount = 12, playing = true, sajdaLabel = t.sajdah)
        Hairline(Modifier.padding(horizontal = Spacing.screen))
        AyahRow(displayText(SampleAyahs.ANFAL_3, 75), localizeDigits("8:75"), AyahCheckState.CoveredEarlier, {})
        PageDivider(t.pageDivider, juzLabel = t.juzStart, actionLabel = t.markPage, onAction = {})
        GalleryColumn(Modifier) {
            MiniPlayer(t.playerTitle, isPlaying = false, speedLabel = t.speed, onPlayPause = {}, onSpeed = {}, onClose = {})
            CountField(3, {}, Modifier.align(Alignment.CenterHorizontally))
            WeekdayChips(t.weekdays, setOf(0, 6), {}, contentDescriptions = t.weekdayNames)
            QCard(Modifier.fillMaxWidth()) { DayBarChart(previewBars.map { it.copy(label = localizeDigits(it.label)) }) }
            QCard(Modifier.fillMaxWidth()) { MonthGrid(t.weekdays, leadingBlanks = 2, days = previewMonth.map { it.copy(dayLabel = localizeDigits(it.dayLabel)) }) }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun RankMarkGalleryPreview() {
    QintarTheme(darkTheme = false) { RankMarkGallery() }
}

@Preview(widthDp = 360)
@Composable
private fun ComponentGalleryAPreview() {
    QintarTheme(darkTheme = false) { ComponentGalleryA() }
}

@Preview(widthDp = 360, locale = "fa")
@Composable
private fun ComponentGalleryBDarkRtlPreview() {
    QintarTheme(darkTheme = true) { ComponentGalleryB() }
}
