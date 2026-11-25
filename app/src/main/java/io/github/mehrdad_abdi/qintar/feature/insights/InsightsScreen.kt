package io.github.mehrdad_abdi.qintar.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.ui.common.dateText
import io.github.mehrdad_abdi.qintar.ui.common.number
import io.github.mehrdad_abdi.qintar.ui.common.pagesText
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.DayBarChart
import io.github.mehrdad_abdi.qintar.ui.designsystem.DayBarUi
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.MonthDayUi
import io.github.mehrdad_abdi.qintar.ui.designsystem.MonthGrid
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankMark
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankThresholds
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.StackedProgressRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.StatTile
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankName
import io.github.mehrdad_abdi.qintar.ui.designsystem.rankShortName
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReadRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Test tags of the sections. */
const val INSIGHTS_CONTENT_TAG = "insights-content"
const val INSIGHTS_CALENDAR_TAG = "insights-calendar"
const val INSIGHTS_REVISIT_TAG = "insights-revisit"

/**
 * Insights (§5.7): period overview, calendar and Revisit.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun InsightsScreen(onNavigate: (route: Any) -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(state, viewModel, onNavigate)
}

@Composable
internal fun InsightsContent(state: InsightsUiState, viewModel: InsightsViewModel, onNavigate: (Any) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .testTag(INSIGHTS_CONTENT_TAG),
    ) {
        Text(
            stringResource(R.string.tab_insights),
            style = QintarType.Title,
            color = colors.onSurface,
            modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md),
        )
        if (state.loading) return@Column
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            if (state.isNewUser) {
                EmptyState(
                    title = stringResource(R.string.insights_empty_title),
                    message = stringResource(R.string.insights_empty_message),
                    icon = Icons.Outlined.BarChart,
                    actionLabel = stringResource(R.string.insights_empty_action),
                    onAction = { onNavigate(ReadRoute) },
                )
            } else {
                PeriodSwitch(state.period, viewModel::onPeriod)
                OverviewSection(state, onDay = { onNavigate(DayDetailRoute(it.toString())) })
                RankDistributionSection(state)
                SourceSection(state.sources)
            }
            state.calendar?.let { calendar ->
                CalendarSection(
                    calendar = calendar,
                    onPrevious = viewModel::onPreviousMonth,
                    onNext = viewModel::onNextMonth,
                    onDay = { onNavigate(DayDetailRoute(it.toString())) },
                )
            }
            if (!state.isNewUser) {
                RevisitSection(state.revisit, viewModel::onToggleRevisit, onBookmark = { onNavigate(ReaderRoute.bookmark(it)) })
            }
            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

@Composable
private fun PeriodSwitch(period: InsightsPeriod, onPeriod: (InsightsPeriod) -> Unit) {
    val labels = mapOf(
        InsightsPeriod.WEEK to stringResource(R.string.insights_period_week),
        InsightsPeriod.MONTH to stringResource(R.string.insights_period_month),
        InsightsPeriod.YEAR to stringResource(R.string.insights_period_year),
        InsightsPeriod.ALL to stringResource(R.string.insights_period_all),
    )
    val description = stringResource(R.string.insights_period_label)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        InsightsPeriod.entries.forEachIndexed { i, entry ->
            SegmentedButton(
                selected = entry == period,
                onClick = { onPeriod(entry) },
                shape = SegmentedButtonDefaults.itemShape(i, InsightsPeriod.entries.size),
                label = { Text(labels.getValue(entry), style = QintarType.Label, maxLines = 1) },
            )
        }
    }
}

// ---- overview ---------------------------------------------------------------------------------------------

@Composable
internal fun OverviewSection(state: InsightsUiState, onDay: (LocalDate) -> Unit) {
    val summary = state.summary
    val tiles = buildList {
        add(formatNumber(summary.totalAyahs) to stringResource(R.string.insights_total))
        add(formatNumber(summary.activeDays) to stringResource(R.string.insights_active_days))
        add(pagesText(summary.averagePerActiveDay) to stringResource(R.string.insights_average))
        add(
            formatNumber(summary.bestDayTotal) to (
                summary.bestDay?.let { stringResource(R.string.insights_best_day_on, shortDate(it)) }
                    ?: stringResource(R.string.insights_best_day)
                ),
        )
        add(formatNumber(state.streak.current) to stringResource(R.string.insights_streak_current))
        add(formatNumber(state.streak.longest) to stringResource(R.string.insights_streak_longest))
        val goal = state.goal
        val met = state.goalMetDays
        if (goal != null && met != null) {
            add(
                stringResource(R.string.insights_goal_value, formatNumber(met), formatNumber(state.periodDays)) to
                    stringResource(R.string.insights_goal_met, rankShortName(goal.number)),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { (value, label) -> StatTile(value, label, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    QCard(Modifier.fillMaxWidth()) {
        val bars = state.bars.mapIndexed { i, bar -> barUi(bar, i, state.period) }
        DayBarChart(
            bars = bars,
            thresholds = if (state.period.daily) RankThresholds else emptyList(),
            onBarClick = if (state.period.daily) ({ i -> onDay(state.bars[i].start) }) else null,
        )
    }
}

@Composable
private fun barUi(bar: InsightsBar, index: Int, period: InsightsPeriod): DayBarUi {
    val locale = LocalConfiguration.current.locales[0]
    val month = io.github.mehrdad_abdi.qintar.ui.common.LocalCalendarDates.current.monthOf(bar.start)
    val label = when (period) {
        InsightsPeriod.WEEK -> bar.start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        InsightsPeriod.MONTH -> if (index % 5 == 0) io.github.mehrdad_abdi.qintar.ui.common.dayOfMonthText(bar.start) else ""
        InsightsPeriod.YEAR -> io.github.mehrdad_abdi.qintar.ui.common.monthName(month.month, short = true)
        InsightsPeriod.ALL -> if (index % 3 == 0) io.github.mehrdad_abdi.qintar.ui.common.monthName(month.month, short = true) else ""
    }
    val name = if (bar.monthly) {
        io.github.mehrdad_abdi.qintar.ui.common.monthTitle(month)
    } else {
        io.github.mehrdad_abdi.qintar.ui.common.calendarDate(bar.start, weekday = true, year = false)
    }
    val description = if (bar.rank.number > 0) {
        stringResource(R.string.insights_bar_description_rank, name, formatNumber(bar.count), rankName(bar.rank.number))
    } else {
        stringResource(R.string.insights_bar_description, name, formatNumber(bar.count))
    }
    return DayBarUi(label, bar.count, bar.rank.number, description)
}

@Composable
private fun shortDate(day: LocalDate): String {
    return io.github.mehrdad_abdi.qintar.ui.common.calendarDate(day, year = false, shortMonth = true)
}

@Composable
internal fun RankDistributionSection(state: InsightsUiState) {
    val colors = MaterialTheme.colorScheme
    SectionHeader(stringResource(R.string.insights_rank_days_title))
    QCard(Modifier.fillMaxWidth()) {
        state.rankDays.forEach { (rank, days) ->
            RankDaysRow(rank, rankShortName(rank.number), days)
        }
        if (state.belowRankDays > 0) {
            RankDaysRow(Rank.NONE, stringResource(R.string.insights_below_first_rank), state.belowRankDays)
        }
        Text(
            stringResource(R.string.insights_rank_days_note),
            style = QintarType.Caption,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun RankDaysRow(rank: Rank, name: String, days: Int) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.insights_rank_days_row, name, formatNumber(days))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RankMark(rank.number, size = Sizes.rankMarkSmall, contentDescription = null)
        Text(name, style = QintarType.Body, color = if (days > 0) colors.onSurface else colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatNumber(days), style = QintarType.Numeric, color = colors.onSurfaceVariant)
    }
}

@Composable
internal fun SourceSection(sources: SourceSplitUi) {
    SectionHeader(stringResource(R.string.insights_sources_title))
    QCard(Modifier.fillMaxWidth()) {
        if (sources.total == 0) {
            Text(stringResource(R.string.insights_sources_none), style = QintarType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@QCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SourceKind.entries.forEach { kind ->
                val count = sources.bySource.getValue(kind)
                if (count > 0) {
                    StackedProgressRow(sourceName(kind), formatNumber(count), count / sources.total.toFloat())
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MethodKind.entries.forEach { kind ->
                val count = sources.byMethod.getValue(kind)
                if (count > 0) {
                    StackedProgressRow(methodName(kind), formatNumber(count), count / sources.total.toFloat(), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun sourceName(kind: SourceKind): String = stringResource(
    when (kind) {
        SourceKind.KHATM -> R.string.source_khatm
        SourceKind.BOOKMARK -> R.string.source_bookmark
        SourceKind.BROWSE -> R.string.source_browse
        SourceKind.RANDOM -> R.string.source_random
        SourceKind.MANUAL -> R.string.source_manual
    },
)

@Composable
private fun methodName(kind: MethodKind): String = stringResource(
    when (kind) {
        MethodKind.TAPPED -> R.string.method_tapped
        MethodKind.BULK -> R.string.method_bulk
        MethodKind.LISTENED -> R.string.method_listened
        MethodKind.MANUAL -> R.string.method_manual
        MethodKind.IMPORTED -> R.string.method_imported
    },
)

// ---- calendar --------------------------------------------------------------------------------------------

@Composable
internal fun CalendarSection(
    calendar: CalendarState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val title = io.github.mehrdad_abdi.qintar.ui.common.monthTitle(calendar.month)
    val days = calendar.days.map { day ->
        val name = io.github.mehrdad_abdi.qintar.ui.common.calendarDate(day.date, year = false)
        MonthDayUi(
            dayLabel = io.github.mehrdad_abdi.qintar.ui.common.dayOfMonthText(day.date),
            rank = day.rank.number,
            count = day.total,
            isToday = day.isToday,
            isFuture = day.isFuture,
            contentDescription = when {
                day.isFuture -> name
                day.rank.number > 0 -> stringResource(R.string.insights_bar_description_rank, name, formatNumber(day.total), rankName(day.rank.number))
                else -> stringResource(R.string.insights_bar_description, name, formatNumber(day.total))
            },
        )
    }
    Column(modifier.testTag(INSIGHTS_CALENDAR_TAG), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.insights_calendar_title))
        QCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.insights_previous_month), tint = colors.onSurface)
                }
                Text(
                    title,
                    style = QintarType.BodyStrong,
                    color = colors.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = calendar.canGoNext, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        stringResource(R.string.insights_next_month),
                        tint = if (calendar.canGoNext) colors.onSurface else colors.outlineVariant,
                    )
                }
            }
            MonthGrid(
                weekdayLabels = calendar.weekdays.map { weekdayLabel(it, locale) },
                leadingBlanks = calendar.leadingBlanks,
                days = days,
                onDayClick = { index -> onDay(calendar.days[index].date) },
            )
        }
    }
}

private fun weekdayLabel(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.NARROW, locale)

// ---- revisit ---------------------------------------------------------------------------------------------

@Composable
internal fun RevisitSection(
    revisit: RevisitState,
    onToggle: () -> Unit,
    onBookmark: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (revisit.items.isEmpty()) return
    Column(modifier.testTag(INSIGHTS_REVISIT_TAG), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.insights_revisit_title))
        Text(stringResource(R.string.insights_revisit_bookmarks), style = QintarType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.card)) {
            revisit.items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RevisitRow(item, onBookmark)
            }
        }
        if (revisit.total > revisit.items.size || revisit.expanded) {
            TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(
                    stringResource(if (revisit.expanded) R.string.insights_show_less else R.string.insights_show_more),
                    style = QintarType.Label,
                )
            }
        }
    }
}

@Composable
private fun RevisitRow(item: RevisitItem, onBookmark: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val lastRead = item.lastRead
    val detail = if (lastRead == null) stringResource(R.string.insights_bookmark_never_read) else stringResource(R.string.insights_last_read, dateText(lastRead))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button) { onBookmark(item.bookmarkId) }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText(item.title), style = QintarType.BodyStrong, color = colors.onSurface)
            Text(detail, style = QintarType.Caption, color = colors.onSurfaceVariant)
        }
    }
}
