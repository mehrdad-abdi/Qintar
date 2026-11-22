package io.github.mehrdad_abdi.qintar.feature.insights

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.model.Tally
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.surahOf
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.domain.service.Streak
import io.github.mehrdad_abdi.qintar.domain.service.StreakCalculator
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DayPhase { LOADING, READY, INVALID }

/** Whose readings a group of entries is. A deleted khatm or bookmark keeps its entries, with no name. */
sealed interface EntryLabel {
    data class Khatm(val name: String?) : EntryLabel
    data class Bookmark(val title: BookmarkTitle?) : EntryLabel
    data object Browse : EntryLabel
    data object Random : EntryLabel
    data object Manual : EntryLabel
}

/**
 * Consecutive ayahs read the same number of times on the day: "Al-Baqarah 1–7", or "×2" when each ayah
 * was read twice. A run, or one ayah of it, can be deleted.
 */
/** One ayah of a run. */
data class EntryAyah(val id: AyahId, val surah: SurahInfo, val number: Int)

data class EntryRun(
    val range: AyahRange,
    /** Readings of each ayah of the run. */
    val readings: Int,
    val firstSurah: SurahInfo,
    val firstAyah: Int,
    val lastSurah: SurahInfo,
    val lastAyah: Int,
    /** Its ayahs one by one, shown when the run is opened. */
    val ayahs: List<EntryAyah> = emptyList(),
) {
    val total: Int get() = range.size * readings
}

/** Everything one context contributed to the day. */
data class EntryGroup(
    val context: ReadContext,
    val label: EntryLabel,
    val total: Int,
    val runs: List<EntryRun>,
    val methods: Set<ReadMethod>,
)

enum class DayMessage(@StringRes val text: Int) {
    FUTURE_DAY(R.string.day_refusal_future),
    COUNT_OUT_OF_RANGE(R.string.log_err_count),
}

enum class UndoKind { DELETED, TALLY_CHANGED, TALLY_DELETED }

/** The snackbar with Undo. [id] tells a new notice from the one already shown. */
data class UndoNotice(val id: Int, val kind: UndoKind)

data class DayDetailUiState(
    val phase: DayPhase = DayPhase.LOADING,
    val day: LocalDate? = null,
    val today: LocalDate? = null,
    val total: Int = 0,
    val rank: Rank = Rank.NONE,
    /** The streak as of today, so that a change to this day shows in it. */
    val streak: Streak = Streak(0, false, 0),
    /** Whether this day's streak-qualifying total was reached: the day counts toward a streak. */
    val qualifiesForStreak: Boolean = false,
    val groups: List<EntryGroup> = emptyList(),
    val tallies: List<Tally> = emptyList(),
    val message: DayMessage? = null,
    val notice: UndoNotice? = null,
) {
    /** Only a day that is not after today can be changed. */
    val canEdit: Boolean get() = day != null && today != null && day <= today
    val isToday: Boolean get() = day != null && day == today
    val isFuture: Boolean get() = day != null && today != null && day > today
    val isEmpty: Boolean get() = groups.isEmpty() && tallies.isEmpty()
}

/**
 * One day of the calendar (R-INS-2, R-TRK-6): its total and rank, what it is made of, and, for any day
 * that is not in the future, the edits: change the count of an entry, delete an entry or tally, with
 * Undo. Adding a reading goes through [LogReadingViewModel]. Ranks and streak follow from the log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DayDetailViewModel.Factory::class)
class DayDetailViewModel @AssistedInject constructor(
    @Assisted private val route: DayDetailRoute,
    private val index: Provider<QuranIndex>,
    private val log: ReadLogStore,
    private val totals: DayTotals,
    private val khatmStore: KhatmStore,
    private val bookmarkStore: BookmarkStore,
    private val tracker: ReadingTracker,
    private val clock: DayClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: DayDetailRoute): DayDetailViewModel
    }

    private val _state = MutableStateFlow(DayDetailUiState())
    val state: StateFlow<DayDetailUiState> = _state.asStateFlow()

    private var undoAction: (suspend () -> Unit)? = null
    private var noticeSeq = 0

    init {
        val day = try {
            LocalDate.parse(route.isoDate)
        } catch (_: DateTimeParseException) {
            null
        }
        if (day == null) {
            _state.value = DayDetailUiState(phase = DayPhase.INVALID)
        } else {
            viewModelScope.launch {
                val quran = withContext(io) { index.get() }
                val labels = combine(khatmStore.observeKhatms(), bookmarkStore.observeBookmarks()) { khatms, bookmarks ->
                    Labels(
                        khatms = khatms.associate { it.id to it.name },
                        bookmarks = bookmarks.associate { it.id to BookmarkTitles.of(it, quran) },
                    )
                }
                combine(
                    totals.observeAllTotals(),
                    clock.todayFlow,
                    log.observeEventsOfDay(day),
                    log.observeTalliesOfDay(day),
                    labels,
                ) { all, today, events, tallies, names ->
                    val total = all[day] ?: 0
                    val groups = groups(events, names, quran)
                    val streak = StreakCalculator.streak(all, today)
                    _state.update {
                        it.copy(
                            phase = DayPhase.READY,
                            day = day,
                            today = today,
                            total = total,
                            rank = RankCalculator.rankFor(total),
                            streak = streak,
                            qualifiesForStreak = StreakCalculator.qualifies(total),
                            groups = groups,
                            tallies = tallies.sortedBy { t -> t.id },
                        )
                    }
                }.collect { }
            }
        }
    }

    private class Labels(val khatms: Map<Long, String>, val bookmarks: Map<Long, BookmarkTitle>)

    // ---- edits -----------------------------------------------------------------------------------------

    /** Deletes the readings of [range] (a run, or one ayah of it) on this day under [context], with Undo. */
    fun onDeleteRun(context: ReadContext, range: AyahRange) {
        val day = _state.value.day ?: return
        act {
            val batch = tracker.setRangeCount(range, context, day, 0)
            if (batch != null) offerUndo(UndoKind.DELETED) { tracker.undo(batch) }
        }
    }

    /** Removes everything that [context] contributed to the day, as one Undo. */
    fun onDeleteGroup(context: ReadContext) {
        val day = _state.value.day ?: return
        val group = _state.value.groups.firstOrNull { it.context == context } ?: return
        act {
            val batches = group.runs.mapNotNull { run -> tracker.setRangeCount(run.range, context, day, 0) }
            if (batches.isNotEmpty()) offerUndo(UndoKind.DELETED) { batches.asReversed().forEach { tracker.undo(it) } }
        }
    }

    fun onUpdateTally(id: Long, count: Int, note: String?) {
        val before = _state.value.tallies.firstOrNull { it.id == id } ?: return
        act {
            tracker.updateTally(id, count, note)
            offerUndo(UndoKind.TALLY_CHANGED) { tracker.updateTally(id, before.count, before.note) }
        }
    }

    fun onDeleteTally(id: Long) {
        val before = _state.value.tallies.firstOrNull { it.id == id } ?: return
        act {
            tracker.deleteTally(id)
            offerUndo(UndoKind.TALLY_DELETED) { tracker.addTally(before.day, before.count, before.note) }
        }
    }

    /** Takes back the last change the snackbar offers to undo. */
    fun onUndo() {
        val action = undoAction ?: return
        undoAction = null
        _state.update { it.copy(notice = null) }
        act(action)
    }

    /** The snackbar went away without Undo. */
    fun onNoticeShown(id: Int) {
        _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }
        if (_state.value.notice == null) undoAction = null
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun offerUndo(kind: UndoKind, action: suspend () -> Unit) {
        undoAction = action
        _state.update { it.copy(notice = UndoNotice(++noticeSeq, kind)) }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: DomainFailure.FutureDay) {
                _state.update { it.copy(message = DayMessage.FUTURE_DAY) }
            } catch (_: DomainFailure.CountOutOfRange) {
                _state.update { it.copy(message = DayMessage.COUNT_OUT_OF_RANGE) }
            }
        }
    }

    companion object {
        private val kindOrder = ContextKind.entries.associateWith { it.ordinal }

        /** The day's events grouped by context, each group's ayahs collapsed into runs. */
        private fun groups(events: List<ReadEvent>, names: Labels, quran: QuranIndex): List<EntryGroup> =
            events.groupBy { it.context }
                .map { (context, rows) ->
                    EntryGroup(
                        context = context,
                        label = when (context) {
                            is ReadContext.Khatm -> EntryLabel.Khatm(names.khatms[context.id])
                            is ReadContext.Bookmark -> EntryLabel.Bookmark(names.bookmarks[context.id])
                            ReadContext.Browse -> EntryLabel.Browse
                            ReadContext.Random -> EntryLabel.Random
                            ReadContext.Log -> EntryLabel.Manual
                        },
                        total = rows.size,
                        runs = runs(rows.map { it.ayahId }, quran),
                        methods = rows.mapTo(linkedSetOf()) { it.method },
                    )
                }
                .sortedWith(compareBy({ kindOrder.getValue(it.context.kind) }, { it.context.id ?: 0L }))

        /** Consecutive ayahs with the same number of readings form one run. */
        fun runs(ayahs: List<AyahId>, quran: QuranIndex): List<EntryRun> {
            val perAyah = ayahs.groupingBy { it.value }.eachCount().toSortedMap()
            val out = ArrayList<EntryRun>()
            var start = -1
            var end = -1
            var readings = 0
            fun flush() {
                if (start < 0) return
                val first = AyahId(start)
                val last = AyahId(end)
                val ref1 = quran.ref(first)
                val ref2 = quran.ref(last)
                val range = AyahRange(first, last)
                val ayahs = range.map { id -> EntryAyah(id, quran.surahOf(id), quran.ref(id).ayah) }
                out += EntryRun(range, readings, quran.surahOf(first), ref1.ayah, quran.surahOf(last), ref2.ayah, ayahs)
            }
            for ((ayah, count) in perAyah) {
                if (start >= 0 && ayah == end + 1 && count == readings) {
                    end = ayah
                } else {
                    flush()
                    start = ayah
                    end = ayah
                    readings = count
                }
            }
            flush()
            return out
        }
    }
}
