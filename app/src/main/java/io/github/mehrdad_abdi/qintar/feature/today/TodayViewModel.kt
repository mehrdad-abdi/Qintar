package io.github.mehrdad_abdi.qintar.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkBoard
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkState
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.CalendarWeeks
import io.github.mehrdad_abdi.qintar.domain.service.CoverageBar
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.GroupState
import io.github.mehrdad_abdi.qintar.domain.service.KhatmManager
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.domain.service.KhatmSnapshot
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.service.RankProgress
import io.github.mehrdad_abdi.qintar.domain.service.Streak
import io.github.mehrdad_abdi.qintar.domain.service.StreakCalculator
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.ui.common.RankReachedDetector
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WeekDayState(
    val day: LocalDate,
    val total: Int,
    val rank: Rank,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/** A khatm as Today shows it: where it stands and the four bars of §4.6. */
data class KhatmCardState(
    val id: Long,
    val name: String,
    /** Page of the resume position. */
    val page: Int,
    val whole: CoverageBar,
    val surah: SurahInfo,
    val surahBar: CoverageBar,
    val juz: Int,
    val juzBar: CoverageBar,
    /** Null when the khatm has no daily goal. */
    val dailyPages: Float?,
    val todayPages: Double,
    val todayGoalFraction: Float?,
)

data class NowItem(val state: BookmarkState, val title: BookmarkTitle)

/** Bookmarks available at this moment and not done, under their group (null = ungrouped). */
/** How many unread bookmarks Today lists at most. */
const val NOW_LIMIT = 5

data class NowSectionState(val group: GroupState?, val items: List<NowItem>)

data class TodayUiState(
    val loading: Boolean = true,
    val day: LocalDate = LocalDate.MIN,
    val rank: RankProgress = RankCalculator.progress(0),
    /** Non-null for about three seconds after a threshold is crossed while Today is open. */
    val reachedRank: Rank? = null,
    val streak: Streak = Streak(0, false, 0),
    /** The seven days of the current week, starting on the user's first weekday. */
    val week: List<WeekDayState> = emptyList(),
    /** The primary khatm: the active one read most recently. Null = "Start a khatm". */
    val khatm: KhatmCardState? = null,
    val otherKhatms: List<KhatmCardState> = emptyList(),
    val now: List<NowSectionState> = emptyList(),
    val remainingNow: Int = 0,
    /** Whether there is any bookmark: decides which empty state the bookmarks card shows. */
    val hasBookmarks: Boolean = false,
) {
    /** Today lists only the first [NOW_LIMIT] of [remainingNow]. */
    val hasMoreNow: Boolean get() = remainingNow > now.sumOf { it.items.size }
}

/**
 * Today (§5.2): everything on it is derived, live, from the log, the khatms and the bookmark board,
 * and follows the reading day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel(
    private val index: Provider<QuranIndex>,
    private val prefs: PrefsStore,
    private val clock: DayClock,
    private val totals: DayTotals,
    private val khatmProgress: Provider<KhatmProgress>,
    private val khatms: KhatmManager,
    private val availability: Availability,
    private val io: CoroutineDispatcher,
    /** The device's first weekday, for the "System" week-start setting. */
    private val systemFirstDay: () -> DayOfWeek,
    /** Re-evaluates schedule windows; about once a minute is enough. */
    private val ticks: Flow<Unit>,
) : ViewModel() {

    @Inject
    constructor(
        index: Provider<QuranIndex>,
        prefs: PrefsStore,
        clock: DayClock,
        totals: DayTotals,
        khatmProgress: Provider<KhatmProgress>,
        khatms: KhatmManager,
        availability: Availability,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(
        index, prefs, clock, totals, khatmProgress, khatms, availability, io,
        systemFirstDay = { WeekFields.of(Locale.getDefault()).firstDayOfWeek },
        ticks = minuteTicker(),
    )

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private val rankReached = RankReachedDetector()
    private var reachedJob: Job? = null

    init {
        viewModelScope.launch {
            // The index and the khatm service wait for the index's first load: not on the main thread.
            val (index, progress) = withContext(io) { index.get() to khatmProgress.get() }
            combine(
                // Paired with the day they were computed for, so that week and streak never mix days.
                clock.todayFlow.flatMapLatest { day -> combine(totals.observeAllTotals(), availability.observe(ticks)) { t, b -> Triple(day, t, b) } },
                prefs.prefs,
                progress.observeActive(),
            ) { (day, dayTotals, board), userPrefs, active -> compute(day, dayTotals, board, userPrefs, active, index) }
                .collect { computed ->
                    _state.update { computed.copy(reachedRank = it.reachedRank) }
                    rankReached.onTotal(computed.day, computed.rank.count)?.let(::showReached)
                }
        }
    }

    private fun compute(
        day: LocalDate,
        dayTotals: Map<LocalDate, Int>,
        board: BookmarkBoard,
        userPrefs: UserPrefs,
        active: List<KhatmSnapshot>,
        index: QuranIndex,
    ): TodayUiState {
        val firstDay = userPrefs.weekStart.firstDay(systemFirstDay())
        val cards = active.map { it.toCard(index) }
        val unread = (board.groups.map { it to it.bookmarks } + (null to board.ungrouped))
            .map { (group, items) -> group to items.filter { !it.progress.done } }
            .filter { it.second.isNotEmpty() }
        return TodayUiState(
            loading = false,
            day = day,
            rank = RankCalculator.progress(dayTotals[day] ?: 0, userPrefs.goalRank),
            streak = StreakCalculator.streak(dayTotals, day),
            week = CalendarWeeks.weekContaining(day, firstDay).map { d ->
                val total = dayTotals[d] ?: 0
                WeekDayState(d, total, RankCalculator.rankFor(total), isToday = d == day, isFuture = d > day)
            },
            khatm = cards.firstOrNull(),
            otherKhatms = cards.drop(1),
            // Bookmarks not read today, under their group, then Ungrouped. Schedules are hidden for
            // now (owner, 2026-11-22), so a schedule neither adds nor hides one here.
            // Only the first few, so hundreds of bookmarks do not make Today endless (owner, 2026-11-25);
            // "Show all" opens the Bookmarks tab.
            now = firstNow(unread).map { (group, items) ->
                NowSectionState(group, items.map { NowItem(it, BookmarkTitles.of(it.bookmark, index)) })
            },
            remainingNow = unread.sumOf { it.second.size },
            hasBookmarks = board.all.isNotEmpty(),
        )
    }

    /** The first [NOW_LIMIT] bookmarks of [sections], in order, keeping their groups. */
    private fun <G, B> firstNow(sections: List<Pair<G, List<B>>>): List<Pair<G, List<B>>> {
        var room = NOW_LIMIT
        return sections.mapNotNull { (group, items) ->
            val taken = items.take(room)
            room -= taken.size
            if (taken.isEmpty()) null else group to taken
        }
    }

    private fun KhatmSnapshot.toCard(index: QuranIndex) = KhatmCardState(
        id = khatm.id,
        name = khatm.name,
        page = index.pageOf(resume ?: AyahId.LAST),
        whole = wholeBar,
        surah = index.surah(currentSurah),
        surahBar = surahBar,
        juz = currentJuz,
        juzBar = juzBar,
        dailyPages = khatm.dailyPages,
        todayPages = todayPages,
        todayGoalFraction = todayGoalFraction,
    )

    /** "Start a khatm" on the empty card. [name] is the localized default name. */
    fun onStartKhatm(name: String) {
        viewModelScope.launch {
            try {
                khatms.start(name)
            } catch (_: DomainFailure) {
            }
        }
    }

    private fun showReached(rank: Rank) {
        reachedJob?.cancel()
        reachedJob = viewModelScope.launch {
            _state.update { it.copy(reachedRank = rank) }
            delay(RankReachedDetector.SHOWN_FOR_MILLIS)
            _state.update { it.copy(reachedRank = null) }
        }
    }

    companion object {
        const val TICK_MILLIS = 60_000L

        fun minuteTicker(): Flow<Unit> = flow {
            while (true) {
                delay(TICK_MILLIS)
                emit(Unit)
            }
        }
    }
}
