package io.github.mehrdad_abdi.qintar.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkState
import io.github.mehrdad_abdi.qintar.domain.service.CoverageBar
import io.github.mehrdad_abdi.qintar.domain.service.GroupState
import io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText
import io.github.mehrdad_abdi.qintar.ui.common.number
import io.github.mehrdad_abdi.qintar.ui.common.pagesText
import io.github.mehrdad_abdi.qintar.ui.common.rankCaption
import io.github.mehrdad_abdi.qintar.ui.common.rankReachedText
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.BookmarkRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.GroupHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
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
import io.github.mehrdad_abdi.qintar.ui.designsystem.StackedProgressRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekDayUi
import io.github.mehrdad_abdi.qintar.ui.designsystem.WeekStrip
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarksRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.InsightsRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmListRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReadRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.SettingsRoute
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Test tag of Today's scrolling column. */
const val TODAY_CONTENT_TAG = "today-content"

/**
 * Today (§5.2): the day's count and rank, the streak, the khatm to continue and the bookmarks that
 * are due now.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun TodayScreen(onNavigate: (route: Any) -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.reachedRank) {
        if (state.reachedRank != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .testTag(TODAY_CONTENT_TAG),
    ) {
        if (state.loading) return@Column
        val locale = LocalConfiguration.current.locales[0]
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                localizeDigits(DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(state.day)),
                style = QintarType.Title,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onNavigate(SettingsRoute) }, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.screen_settings), tint = colors.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))
            RankSummary(state)
            Spacer(Modifier.height(Spacing.xl))
            StreakCard(state, locale, onClick = { onNavigate(InsightsRoute) })
            Spacer(Modifier.height(Spacing.betweenSections))
            KhatmSection(state, onNavigate, viewModel)
            Spacer(Modifier.height(Spacing.betweenSections))
            NowSection(state, onNavigate)
            Spacer(Modifier.height(Spacing.xl))
            OutlinedButton(
                onClick = { onNavigate(ReadRoute) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
                border = BorderStroke(Sizes.hairline, colors.outlineVariant),
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(R.string.today_browse_quran), style = QintarType.Label)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ColumnScope.RankSummary(state: TodayUiState) {
    val progress = state.rank
    val distance = rankCaption(progress)
    val target = progress.next
    ProgressRing(progress = progress.ringFraction, contentDescription = "${formatNumber(progress.count)}, $distance") {
        RingCount(
            count = progress.count,
            caption = target?.let { stringResource(R.string.ring_of_next, formatNumber(it.threshold)) }.orEmpty(),
            secondaryCaption = distance.ifEmpty { null },
        )
    }
    Spacer(Modifier.height(Spacing.xl))
    val reached = state.reachedRank
    if (reached != null) {
        Text(rankReachedText(reached), style = QintarType.BodyStrong, color = QintarTheme.extendedColors.goldInk)
        Spacer(Modifier.height(Spacing.md))
    }
    if (progress.rank.number > 0) {
        RankChip(rank = progress.rank.number)
        Spacer(Modifier.height(Spacing.xl))
    }
    RankLadder(
        currentRank = progress.rank.number,
        goalRank = progress.goal?.number,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    )
}

@Composable
private fun StreakCard(state: TodayUiState, locale: Locale, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val streak = state.streak
    QCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Outlined.LocalFireDepartment, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            Text(
                when {
                    streak.current == 0 -> stringResource(R.string.today_streak_none)
                    streak.atRisk -> stringResource(R.string.today_streak_at_risk, formatNumber(streak.current))
                    else -> stringResource(R.string.today_streak_days, formatNumber(streak.current))
                },
                style = QintarType.Label,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        val days = state.week.map { d ->
            val name = d.day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            WeekDayUi(
                label = d.day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                rank = d.rank.number,
                count = d.total,
                isToday = d.isToday,
                isFuture = d.isFuture,
                contentDescription = stringResource(R.string.week_day_description, name, formatNumber(d.total)),
            )
        }
        WeekStrip(days)
    }
}

@Composable
private fun percent(bar: CoverageBar): String =
    stringResource(R.string.percent, formatNumber((bar.fraction * 100).toInt()))

@Composable
private fun KhatmSection(state: TodayUiState, onNavigate: (Any) -> Unit, viewModel: TodayViewModel) {
    val colors = MaterialTheme.colorScheme
    val khatm = state.khatm
    if (khatm == null) {
        val defaultName = stringResource(R.string.khatm_default_name)
        QCard(Modifier.fillMaxWidth()) {
            EmptyState(
                title = stringResource(R.string.today_no_khatm_title),
                message = stringResource(R.string.today_no_khatm_message),
                icon = Icons.Outlined.AutoStories,
                actionLabel = stringResource(R.string.today_start_khatm),
                onAction = { viewModel.onStartKhatm(defaultName) },
            )
        }
        return
    }
    SectionHeader(
        stringResource(R.string.today_continue_khatm),
        actionLabel = stringResource(R.string.read_khatms_all),
        onAction = { onNavigate(KhatmListRoute) },
    )
    Spacer(Modifier.height(Spacing.sm))
    QCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(khatm.name, style = QintarType.BodyStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.page_n, formatNumber(khatm.page)), style = QintarType.Numeric, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.md))
        if (khatm.dailyPages != null) {
            StackedProgressRow(
                progress = (khatm.todayGoalFraction ?: 0f).coerceIn(0f, 1f),
                valueText = stringResource(R.string.khatm_today_pages, pagesText(khatm.todayPages), pagesText(khatm.dailyPages.toDouble())),
                label = stringResource(R.string.today_label),
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        StackedProgressRow(surahName(khatm.surah), percent(khatm.surahBar), khatm.surahBar.fraction)
        Spacer(Modifier.height(Spacing.sm))
        StackedProgressRow(stringResource(R.string.juz_n, formatNumber(khatm.juz)), percent(khatm.juzBar), khatm.juzBar.fraction)
        Spacer(Modifier.height(Spacing.sm))
        StackedProgressRow(stringResource(R.string.khatm_whole_quran), percent(khatm.whole), khatm.whole.fraction)
        Spacer(Modifier.height(Spacing.lg))
        Button(
            onClick = { onNavigate(ReaderRoute.khatm(khatm.id)) },
            modifier = Modifier.align(Alignment.End).heightIn(min = Sizes.touchTarget),
            contentPadding = PaddingValues(horizontal = Spacing.xxl),
        ) { Text(stringResource(R.string.action_continue), style = QintarType.Label) }
    }
    for (other in state.otherKhatms) {
        Spacer(Modifier.height(Spacing.sm))
        QCard(Modifier.fillMaxWidth(), onClick = { onNavigate(ReaderRoute.khatm(other.id)) }) {
            StackedProgressRow(other.name, percent(other.whole), other.whole.fraction)
        }
    }
}

private fun readState(started: Boolean, done: Boolean) = when {
    done -> ReadState.Done
    started -> ReadState.Partial
    else -> ReadState.NotRead
}

@Composable
private fun trailing(item: BookmarkState): String {
    val p = item.progress
    return if (p.repeatTarget > 1 || p.started) {
        stringResource(R.string.progress_of, formatNumber(p.readings), formatNumber(p.required))
    } else {
        stringResource(R.string.ayahs_n, formatNumber(item.bookmark.range.size))
    }
}

@Composable
private fun window(group: GroupState): String? = group.group.schedule?.window?.let {
    val format = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    localizeDigits("${format.format(it.from)}–${format.format(it.to)}")
}

@Composable
private fun NowSection(state: TodayUiState, onNavigate: (Any) -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (state.now.isEmpty()) {
        if (!state.hasRoutine) {
            QCard(Modifier.fillMaxWidth()) {
                EmptyState(
                    title = stringResource(R.string.today_no_routine_title),
                    message = stringResource(R.string.today_no_routine_message),
                    icon = Icons.Outlined.EventRepeat,
                    actionLabel = stringResource(R.string.today_open_bookmarks),
                    onAction = { onNavigate(BookmarksRoute) },
                )
            }
        } else {
            SectionHeader(stringResource(R.string.today_available_now))
            Spacer(Modifier.height(Spacing.sm))
            QCard(Modifier.fillMaxWidth(), onClick = { onNavigate(BookmarksRoute) }) {
                Text(stringResource(R.string.today_nothing_due), style = QintarType.Body, color = colors.onSurfaceVariant)
            }
        }
        return
    }
    SectionHeader(
        stringResource(R.string.today_available_now),
        trailing = stringResource(R.string.today_n_left, formatNumber(state.remainingNow)),
    )
    Spacer(Modifier.height(Spacing.sm))
    QCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
        state.now.forEachIndexed { i, section ->
            if (i > 0) Hairline(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.xs))
            val group = section.group
            if (group != null) {
                GroupHeader(
                    name = group.group.name,
                    state = readState(group.progress.started, group.progress.done),
                    window = window(group),
                    onRead = { onNavigate(ReaderRoute.group(group.group.id)) },
                )
            } else if (state.now.size > 1) {
                Text(
                    stringResource(R.string.today_ungrouped),
                    style = QintarType.BodyStrong,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.card, vertical = Spacing.sm),
                )
            }
            for (item in section.items) {
                BookmarkRow(
                    title = bookmarkTitleText(item.title),
                    state = readState(item.state.progress.started, item.state.progress.done),
                    onClick = { onNavigate(ReaderRoute.bookmark(item.state.bookmark.id)) },
                    trailing = trailing(item.state),
                    progress = item.state.progress.fraction,
                )
            }
        }
    }
}
