package io.github.mehrdad_abdi.qintar.feature.insights

import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import io.github.mehrdad_abdi.qintar.domain.service.CalMonth
import io.github.mehrdad_abdi.qintar.domain.service.CalendarDates
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.CalendarWeeks
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.InsightsCalculator
import io.github.mehrdad_abdi.qintar.domain.service.PeriodSummary
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.service.SourceSplit
import io.github.mehrdad_abdi.qintar.domain.service.Streak
import io.github.mehrdad_abdi.qintar.domain.service.StreakCalculator
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkBoard
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The period switch of Insights → Overview (§5.7). */
enum class InsightsPeriod {
    WEEK, MONTH, YEAR, ALL;

    /** First day of the period ending [today]; null = from the beginning. */
    fun from(today: LocalDate): LocalDate? = when (this) {
        WEEK -> today.minusDays(6)
        MONTH -> today.minusDays(29)
        // Twelve calendar months ending with this one, so that the bars are whole months.
        YEAR -> today.withDayOfMonth(1).minusMonths(11)
        ALL -> null
    }

    /** Whether the chart draws one bar per day (otherwise one per month). */
    val daily: Boolean get() = this == WEEK || this == MONTH
}

/**
 * One bar of the overview chart. For the short periods [start] is the day and [monthly] is false; for a
 * year or everything it is the first day of the month and [count] is the month's total, coloured by the
 * average of its active days.
 */
data class InsightsBar(val start: LocalDate, val count: Int, val rank: Rank, val monthly: Boolean)

/** One day of the calendar. */
data class CalendarDay(val date: LocalDate, val total: Int, val rank: Rank, val isToday: Boolean, val isFuture: Boolean)

data class CalendarState(
    /** A month of the chosen calendar (R-SET-5). */
    val month: CalMonth,
    /** The seven weekdays, in the user's week-start order. */
    val weekdays: List<DayOfWeek>,
    val leadingBlanks: Int,
    val days: List<CalendarDay>,
    val canGoNext: Boolean,
)

enum class SourceKind { KHATM, BOOKMARK, BROWSE, RANDOM, MANUAL }

enum class MethodKind { TAPPED, BULK, LISTENED, MANUAL, IMPORTED }

/** Where the period's ayahs came from (R-INS-4); a tally counts as a manual entry. */
data class SourceSplitUi(
    val total: Int,
    val bySource: Map<SourceKind, Int>,
    val byMethod: Map<MethodKind, Int>,
)

/** A bookmark offered by Revisit (§4.8): when it was last read from the bookmark; null = never. */
data class RevisitItem(val bookmarkId: Long, val title: BookmarkTitle, val lastRead: LocalDate?)

data class RevisitState(
    /** Never read first, then the ones read longest ago. */
    val items: List<RevisitItem> = emptyList(),
    val total: Int = 0,
    val expanded: Boolean = false,
)

data class InsightsUiState(
    val loading: Boolean = true,
    val period: InsightsPeriod = InsightsPeriod.WEEK,
    val today: LocalDate = LocalDate.MIN,
    /** Nothing has ever been read or logged. */
    val isNewUser: Boolean = true,
    val summary: PeriodSummary = InsightsCalculator.summary(emptyMap()),
    val streak: Streak = Streak(0, false, 0),
    val goal: Rank? = null,
    /** Days of the period that reached [goal]; null without a goal. */
    val goalMetDays: Int? = null,
    /** Days of the period the goal could have been met on (for "22 of 30"). */
    val periodDays: Int = 0,
    val bars: List<InsightsBar> = emptyList(),
    /** Days at each of the seven ranks, lowest first. */
    val rankDays: List<Pair<Rank, Int>> = emptyList(),
    /** Active days below the first rank. */
    val belowRankDays: Int = 0,
    val sources: SourceSplitUi = SourceSplitUi(0, emptyMap(), emptyMap()),
    val calendar: CalendarState? = null,
    val revisit: RevisitState = RevisitState(),
)

/**
 * Insights (§5.7): the overview of a period, the month calendar and Revisit, all derived from the log
 * and following the reading day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel(
    private val index: Provider<QuranIndex>,
    private val availability: Availability,
    private val log: ReadLogStore,
    private val totals: DayTotals,
    private val prefs: PrefsStore,
    private val clock: DayClock,
    @IoDispatcher private val io: CoroutineDispatcher,
    /** The device's first weekday, for the "System" week-start setting. */
    private val systemFirstDay: () -> DayOfWeek,
) : ViewModel() {

    @Inject
    constructor(
        index: Provider<QuranIndex>,
        availability: Availability,
        log: ReadLogStore,
        totals: DayTotals,
        prefs: PrefsStore,
        clock: DayClock,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(index, availability, log, totals, prefs, clock, io, { WeekFields.of(Locale.getDefault()).firstDayOfWeek })

    private val period = MutableStateFlow(InsightsPeriod.WEEK)

    /** Null = the current month, following the reading day. */
    private val month = MutableStateFlow<CalMonth?>(null)

    /** The calendar of the settings; months step in it. */
    private var dates = CalendarDates(CalendarSystem.GREGORIAN)
    private val revisitExpanded = MutableStateFlow(false)

    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val quran = withContext(io) { index.get() }
            val overview = combine(totals.observeAllTotals(), clock.todayFlow, prefs.prefs, period, month) { all, today, userPrefs, p, m ->
                val chosen = CalendarDates(userPrefs.calendarSystem, userPrefs.hijriAdjustDays)
                // Another calendar names other months: back to the current one.
                val changed = chosen.system != dates.system
                if (changed) month.value = null
                dates = chosen
                Overview(all, today, userPrefs.goalRank, userPrefs.weekStart.firstDay(systemFirstDay()), p, m.takeIf { !changed }, chosen)
            }
            val split = combine(period, clock.todayFlow, ::Pair).flatMapLatest { (p, today) ->
                totals.observeSourceSplit(p.from(today), today)
            }
            val revisitFlow = combine(availability.observe(), log.observeBookmarkLastRead(), revisitExpanded) { board, lastRead, expanded ->
                revisitState(board, lastRead, expanded, quran)
            }
            combine(overview, split, revisitFlow) { o, s, r -> build(o, s, r) }.collect { next -> _state.value = next }
        }
    }

    private class Overview(
        val all: Map<LocalDate, Int>,
        val today: LocalDate,
        val goal: Rank?,
        val firstDay: DayOfWeek,
        val period: InsightsPeriod,
        val month: CalMonth?,
        val dates: CalendarDates,
    )

    fun onPeriod(value: InsightsPeriod) {
        period.value = value
    }

    fun onPreviousMonth() = month.update { dates.plus(it ?: dates.monthOf(clock.today()), -1) }

    fun onNextMonth() = month.update { current ->
        val now = dates.monthOf(clock.today())
        dates.plus(current ?: now, 1).takeIf { it <= now }
    }

    /** Back to the current month. */
    fun onThisMonth() {
        month.value = null
    }

    fun onToggleRevisit() = revisitExpanded.update { !it }

    // ---- derivation ---------------------------------------------------------------------------------------

    private fun build(o: Overview, split: SourceSplit, revisit: RevisitState): InsightsUiState {
        val today = o.today
        val from = o.period.from(today)
        val summary = InsightsCalculator.summary(o.all, from, today)
        val firstActive = o.all.keys.minOrNull()
        val goalFrom = from ?: firstActive ?: today
        val goalMet = o.goal?.let { InsightsCalculator.goalMetDays(o.all, it, goalFrom, today) }
        val shown = o.month ?: o.dates.monthOf(today)
        return InsightsUiState(
            loading = false,
            period = o.period,
            today = today,
            isNewUser = o.all.isEmpty(),
            summary = summary,
            streak = StreakCalculator.streak(o.all, today),
            goal = o.goal,
            goalMetDays = goalMet,
            periodDays = (today.toEpochDay() - goalFrom.toEpochDay() + 1).toInt().coerceAtLeast(1),
            bars = bars(o.all, o.period, today, firstActive, o.dates),
            rankDays = Rank.ladder.map { it to (summary.rankDistribution[it] ?: 0) },
            belowRankDays = summary.rankDistribution[Rank.NONE] ?: 0,
            sources = sources(split),
            calendar = calendar(o.all, shown, today, o.firstDay, o.dates),
            revisit = revisit,
        )
    }

    private fun revisitState(board: BookmarkBoard, lastRead: Map<Long, LocalDate>, expanded: Boolean, quran: QuranIndex): RevisitState {
        val limit = if (expanded) EXPANDED_ITEMS else COLLAPSED_ITEMS
        val ranked = board.listed.map { RevisitItem(it.bookmark.id, BookmarkTitles.of(it.bookmark, quran), lastRead[it.bookmark.id]) }
            .sortedWith(compareBy(nullsFirst()) { it.lastRead })
        return RevisitState(items = ranked.take(limit), total = ranked.size, expanded = expanded)
    }

    companion object {
        const val COLLAPSED_ITEMS = 5
        const val EXPANDED_ITEMS = 20
        private const val MAX_MONTH_BARS = 36

        fun bars(
            all: Map<LocalDate, Int>,
            period: InsightsPeriod,
            today: LocalDate,
            firstActive: LocalDate?,
            dates: CalendarDates = CalendarDates(CalendarSystem.GREGORIAN),
        ): List<InsightsBar> {
            if (period.daily) {
                val from = period.from(today)!!
                return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.map { day ->
                    val count = all[day] ?: 0
                    InsightsBar(day, count, RankCalculator.rankFor(count), monthly = false)
                }.toList()
            }
            // Months of the chosen calendar.
            val last = dates.monthOf(today)
            val first = when (period) {
                InsightsPeriod.YEAR -> dates.plus(last, -11)
                else -> maxOf(dates.monthOf(firstActive ?: today), dates.plus(last, -(MAX_MONTH_BARS - 1)))
            }
            val byMonth = all.filterValues { it > 0 }.entries.groupBy({ dates.monthOf(it.key) }, { it.value })
            return generateSequence(first) { dates.plus(it, 1) }.takeWhile { it <= last }.map { month ->
                val days = byMonth[month].orEmpty()
                val total = days.sum()
                val average = if (days.isEmpty()) 0 else total / days.size
                InsightsBar(dates.firstDay(month), total, RankCalculator.rankFor(average), monthly = true)
            }.toList()
        }

        fun sources(split: SourceSplit): SourceSplitUi {
            val bySource = linkedMapOf(
                SourceKind.KHATM to (split.byContext[ContextKind.KHATM] ?: 0),
                SourceKind.BOOKMARK to (split.byContext[ContextKind.BOOKMARK] ?: 0),
                SourceKind.BROWSE to (split.byContext[ContextKind.BROWSE] ?: 0),
                SourceKind.RANDOM to (split.byContext[ContextKind.RANDOM] ?: 0),
                SourceKind.MANUAL to ((split.byContext[ContextKind.LOG] ?: 0) + split.tallied),
            )
            val byMethod = linkedMapOf(
                MethodKind.TAPPED to (split.byMethod[ReadMethod.TAP] ?: 0),
                MethodKind.BULK to (split.byMethod[ReadMethod.BULK] ?: 0),
                MethodKind.LISTENED to (split.byMethod[ReadMethod.AUDIO] ?: 0),
                MethodKind.MANUAL to ((split.byMethod[ReadMethod.MANUAL] ?: 0) + split.tallied),
                MethodKind.IMPORTED to (split.byMethod[ReadMethod.IMPORT] ?: 0),
            )
            return SourceSplitUi(split.total, bySource, byMethod)
        }

        fun calendar(
            all: Map<LocalDate, Int>,
            month: CalMonth,
            today: LocalDate,
            firstDay: DayOfWeek,
            dates: CalendarDates = CalendarDates(CalendarSystem.GREGORIAN),
        ): CalendarState {
            val grid = CalendarWeeks.monthGrid(dates.firstDay(month), dates.lastDay(month), firstDay)
            val days = grid.flatten().filterNotNull().map { date ->
                val total = all[date] ?: 0
                CalendarDay(date, total, RankCalculator.rankFor(total), isToday = date == today, isFuture = date > today)
            }
            return CalendarState(
                month = month,
                weekdays = CalendarWeeks.weekdays(firstDay),
                leadingBlanks = grid.first().takeWhile { it == null }.size,
                days = days,
                canGoNext = month < dates.monthOf(today),
            )
        }
    }
}
