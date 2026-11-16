package io.github.mehrdad_abdi.qintar.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Batch
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Segment
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.BulkRangeResolver
import io.github.mehrdad_abdi.qintar.domain.service.DayTotals
import io.github.mehrdad_abdi.qintar.domain.service.GoToParser
import io.github.mehrdad_abdi.qintar.domain.service.GoToResult
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.domain.service.RankCalculator
import io.github.mehrdad_abdi.qintar.domain.service.RankProgress
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.domain.service.StarResult
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.ui.common.GoToProblem
import io.github.mehrdad_abdi.qintar.ui.common.RankReachedDetector
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahCheckState
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReaderPhase { LOADING, READY, NOT_FOUND }

/** What the top bar names. */
sealed interface ReaderTitle {
    /** A khatm's or a group's name. */
    data class Name(val text: String) : ReaderTitle
    data class OfBookmark(val title: BookmarkTitle) : ReaderTitle

    /** Browse and Random: the surah being read. */
    data object CurrentSurah : ReaderTitle
}

sealed interface ReaderDialog {
    val row: ReaderRow.Ayah

    /** "Set count…": [current] is today's count in the row's context. */
    data class SetCount(override val row: ReaderRow.Ayah, val current: Int) : ReaderDialog

    /** A khatm tick that would leave [gap] minus the tapped ayah unread behind it (§4.2.1). */
    data class KhatmSkip(override val row: ReaderRow.Ayah, val gap: AyahRange) : ReaderDialog

    /** The star belongs to a bookmark that carries more than a star (§4.7). */
    data class ConfirmUnstar(override val row: ReaderRow.Ayah, val bookmark: Bookmark) : ReaderDialog
}

/** What a sheet over the reader shows. */
enum class ReaderSheet {
    /** The position bar's sheet: jump to a surah, juz, page or ayah within the scope. */
    JUMP,

    /** The "Aa" panel. */
    TEXT,
}

/** A bookmark's header in the reader: its description and today's progress against its repeat target. */
data class SegmentHeaderInfo(
    val description: String?,
    /** Σ of today's counts, each capped at the target. */
    val readings: Int,
    val required: Int,
    /** Complete readings today ("37 / 100"). */
    val fullReadings: Int,
    val repeatTarget: Int,
    val done: Boolean,
)

/** Asks the list to scroll to [row]; [token] tells one request from the next. */
data class ScrollRequest(val row: Int, val token: Int)

data class ReaderUiState(
    val entry: ReaderEntry,
    val phase: ReaderPhase = ReaderPhase.LOADING,
    val title: ReaderTitle = ReaderTitle.CurrentSurah,
    /** Group entry: the title of each segment's bookmark. Empty otherwise. */
    val segmentTitles: List<BookmarkTitle> = emptyList(),
    val model: ReaderRowModel? = null,
    /** Where the list opens. */
    val initialRowIndex: Int = 0,
    /** Stored text of the ayahs loaded so far; a row without an entry is still loading. */
    val texts: Map<AyahId, String> = emptyMap(),
    /** Per segment: today's readings in that segment's context. */
    val counts: List<Map<AyahId, Int>> = emptyList(),
    /** Khatm entry: ayahs with an event of this khatm on any day. */
    val covered: Set<AyahId> = emptySet(),
    val assumedCoveredUpTo: Int = 0,
    val starred: Set<AyahId> = emptySet(),
    val fontSp: Int = UserPrefs.DEFAULT_FONT_SP,
    val breakAtPauseMarks: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Today's total and rank: the pill. */
    val today: RankProgress = RankCalculator.progress(0),
    /** Non-null for about three seconds after a threshold is crossed. */
    val reachedRank: Rank? = null,
    val dialog: ReaderDialog? = null,
    /** The last bulk mark, while its Undo is offered. */
    val bulk: Batch? = null,
    /** Per segment of a bookmark or group reader: header details; null where there is none. */
    val headers: List<SegmentHeaderInfo?> = emptyList(),
    val sheet: ReaderSheet? = null,
    val jumpText: String = "",
    val jumpProblem: GoToProblem? = null,
    /** Surahs and juz the scope touches: what the position sheet offers. */
    val scopeSurahs: List<SurahInfo> = emptyList(),
    val scopeJuz: List<Int> = emptyList(),
    /** Khatm entry: the resume position, and "first unread" when it is elsewhere (§4.6). */
    val resume: AyahId? = null,
    val firstUnread: AyahId? = null,
    val scrollRequest: ScrollRequest? = null,
) {
    val rows: List<ReaderRow> get() = model?.rows.orEmpty()

    /** Readings of this row's ayah today, in this row's context. */
    fun count(row: ReaderRow.Ayah): Int = counts.getOrNull(row.segment)?.get(row.id) ?: 0

    /**
     * Whether the page that ends just above row [belowRow] has an ayah still unread in its context:
     * "Mark page" is offered only then. [belowRow] is a page divider's row, or the row count for the
     * last page of the scope.
     */
    fun pageAboveHasUnread(belowRow: Int): Boolean {
        val rows = model?.rows ?: return false
        var i = minOf(belowRow, rows.size) - 1
        while (i >= 0) {
            when (val row = rows[i]) {
                is ReaderRow.Ayah -> if (checkState(row) == AyahCheckState.Unread) return true
                is ReaderRow.PageDivider, is ReaderRow.SegmentHeader -> return false
                else -> Unit
            }
            i--
        }
        return false
    }

    fun checkState(row: ReaderRow.Ayah): AyahCheckState = when {
        count(row) > 0 -> AyahCheckState.ReadToday
        row.context is ReadContext.Khatm && (row.id.value <= assumedCoveredUpTo || row.id in covered) ->
            AyahCheckState.CoveredEarlier
        else -> AyahCheckState.Unread
    }
}

/**
 * The reader (§5.4). The route says how it was entered; from that come the segments (passage +
 * counting context), the opening position and the title. Every write goes through [ReadingTracker];
 * the reading day is never held here — counts follow `DayClock.todayFlow`, and the tracker asks the
 * clock at the moment of writing.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val route: ReaderRoute,
    private val index: Provider<QuranIndex>,
    private val text: QuranTextSource,
    private val log: ReadLogStore,
    private val prefs: PrefsStore,
    private val clock: DayClock,
    private val tracker: ReadingTracker,
    private val totals: DayTotals,
    private val khatmProgress: Provider<KhatmProgress>,
    private val availability: Availability,
    private val library: BookmarkLibrary,
    private val bulk: Provider<BulkRangeResolver>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ReaderRoute): ReaderViewModel
    }

    private val _state = MutableStateFlow(ReaderUiState(route.entry))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var model: ReaderRowModel? = null

    /** Per segment: where "mark up to here" may start at the earliest (§4.2.2). */
    private var scopeStarts: List<AyahId> = emptyList()

    private var quran: QuranIndex? = null
    private var scrollToken = 0

    private val firstVisible = MutableStateFlow<AyahId?>(null)
    private val pages = LinkedHashMap<Int, Map<AyahId, String>>(16, 0.75f, true)
    private val pagesLoading = HashSet<Int>()
    private val rankReached = RankReachedDetector()
    private var reachedJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.prefs.collect { p ->
                _state.update {
                    it.copy(
                        fontSp = p.quranFontSp,
                        breakAtPauseMarks = p.breakAtPauseMarks,
                        keepScreenOn = p.keepScreenOn,
                        today = RankCalculator.progress(it.today.count, p.goalRank),
                    )
                }
            }
        }
        viewModelScope.launch {
            clock.todayFlow.flatMapLatest { day -> totals.observeDay(day).map { day to it } }.collect { (day, total) ->
                _state.update { it.copy(today = RankCalculator.progress(total, it.today.goal)) }
                rankReached.onTotal(day, total)?.let(::showReached)
            }
        }
        viewModelScope.launch {
            library.observeStarred().collect { starred -> _state.update { it.copy(starred = starred) } }
        }
        viewModelScope.launch {
            val opened = withContext(io) { open() }
            if (opened == null) {
                _state.update { it.copy(phase = ReaderPhase.NOT_FOUND) }
                return@launch
            }
            model = opened.model
            quran = opened.index
            scopeStarts = opened.scopeStarts
            firstVisible.value = opened.start
            _state.update {
                it.copy(
                    phase = ReaderPhase.READY,
                    title = opened.title,
                    segmentTitles = opened.segmentTitles,
                    model = opened.model,
                    initialRowIndex = opened.startRow,
                    assumedCoveredUpTo = opened.assumedCoveredUpTo,
                    scopeSurahs = opened.scopeSurahs,
                    scopeJuz = opened.scopeJuz,
                )
            }
            opened.model.ayahAtOrAfter(opened.startRow)?.let { loadPagesAround(it, it) }
            observeReadState(opened.model.segments)
            if (route.entry == ReaderEntry.BOOKMARK || route.entry == ReaderEntry.GROUP) observeHeaders(opened.model.segments)
            if (route.entry == ReaderEntry.KHATM) observeKhatmPosition()
            if (route.entry == ReaderEntry.BROWSE) rememberBrowsePosition()
        }
    }

    // ---- Opening --------------------------------------------------------------------------------

    private class Opened(
        val model: ReaderRowModel,
        val title: ReaderTitle,
        val start: AyahId,
        val startRow: Int,
        val scopeStarts: List<AyahId>,
        val segmentTitles: List<BookmarkTitle> = emptyList(),
        val assumedCoveredUpTo: Int = 0,
        val index: QuranIndex,
        val scopeSurahs: List<SurahInfo> = emptyList(),
        val scopeJuz: List<Int> = emptyList(),
    )

    private suspend fun open(): Opened? {
        val opened = openRoute() ?: return null
        val index = opened.index
        val ranges = opened.model.segments.map { it.range }
        val surahs = ranges.flatMap { index.ref(it.start).surah..index.ref(it.end).surah }.distinct().map(index::surah)
        val juz = ranges.flatMap { index.juzOf(it.start)..index.juzOf(it.end) }.distinct()
        return Opened(
            opened.model, opened.title, opened.start, opened.startRow, opened.scopeStarts, opened.segmentTitles,
            opened.assumedCoveredUpTo, index, surahs, juz,
        )
    }

    private suspend fun openRoute(): Opened? {
        val index = index.get()
        val requested = AyahId.orNull(route.startAyahId)
        fun single(
            segment: Segment,
            title: ReaderTitle,
            start: AyahId,
            scopeStart: AyahId,
            assumed: Int = 0,
            header: BookmarkTitle? = null,
        ): Opened {
            // A bookmark's own header (title, description, progress) stands above its first ayah.
            val model = ReaderRowModel.build(listOf(segment), index, segmentHeaders = header != null)
            val at = start.takeIf { it in segment.range } ?: segment.range.start
            val row = if (header != null && at == segment.range.start) 0 else model.blockStart(model.indexOf(0, at)!!)
            return Opened(
                model, title, at, row, listOf(scopeStart),
                segmentTitles = listOfNotNull(header), assumedCoveredUpTo = assumed, index = index,
            )
        }
        return when (route.entry) {
            ReaderEntry.KHATM -> {
                val snapshot = khatmProgress.get().snapshot(route.id) ?: return null
                val start = requested ?: snapshot.resume ?: AyahId.FIRST
                single(
                    Segment(ReadContext.Khatm(route.id), AyahRange.ALL),
                    ReaderTitle.Name(snapshot.khatm.name),
                    start,
                    bulk.get().scopeStart(start, snapshot.resume),
                    snapshot.khatm.assumedCoveredUpTo,
                )
            }
            ReaderEntry.BOOKMARK -> {
                val state = availability.board().state(route.id) ?: return null
                val title = BookmarkTitles.of(state.bookmark, index)
                single(
                    state.segment,
                    ReaderTitle.OfBookmark(title),
                    requested ?: state.progress.firstBelowTarget ?: state.bookmark.range.start,
                    state.bookmark.range.start,
                    header = title,
                )
            }
            ReaderEntry.GROUP -> {
                val group = availability.board().groups.firstOrNull { it.group.id == route.id } ?: return null
                if (group.bookmarks.isEmpty()) return null
                val model = ReaderRowModel.build(group.segments, index, segmentHeaders = true)
                val first = group.firstNotDone ?: group.bookmarks.first()
                val segment = group.bookmarks.indexOf(first)
                val start = first.progress.firstBelowTarget ?: first.bookmark.range.start
                Opened(
                    model = model,
                    title = ReaderTitle.Name(group.group.name),
                    start = start,
                    // A group opens on the header of its first bookmark that is not done.
                    startRow = (model.indexOf(segment, start)!!).let { row ->
                        if (start == first.bookmark.range.start) model.rows.indexOfFirst { it.segment == segment } else model.blockStart(row)
                    },
                    scopeStarts = group.segments.map { it.range.start },
                    segmentTitles = group.bookmarks.map { BookmarkTitles.of(it.bookmark, index) },
                    index = index,
                )
            }
            ReaderEntry.BROWSE -> {
                val start = requested ?: prefs.prefs.first().lastBrowseAyahId ?: AyahId.FIRST
                single(Segment(ReadContext.Browse, AyahRange.ALL), ReaderTitle.CurrentSurah, start, bulk.get().scopeStart(start))
            }
            ReaderEntry.RANDOM -> {
                val first = AyahId.orNull(route.scopeFirstAyahId) ?: requested ?: return null
                val last = AyahId.orNull(route.scopeLastAyahId)?.takeIf { it >= first } ?: first
                single(Segment(ReadContext.Random, AyahRange(first, last)), ReaderTitle.CurrentSurah, requested ?: first, first)
            }
        }
    }

    private fun observeReadState(segments: List<Segment>) {
        viewModelScope.launch {
            // Re-keyed by the reading day: a reader left open across the boundary shows the new day.
            clock.todayFlow
                .flatMapLatest { day ->
                    combine(segments.map { log.observeAyahCounts(it.context, day, it.range) }) { it.toList() }
                }
                .collect { counts -> _state.update { it.copy(counts = counts) } }
        }
        val khatm = segments.singleOrNull()?.context as? ReadContext.Khatm ?: return
        viewModelScope.launch {
            log.observeDistinctAyahs(khatm, AyahRange.ALL).collect { covered -> _state.update { it.copy(covered = covered) } }
        }
    }

    /** Bookmark and group readers: each segment's description and today's progress, live. */
    private fun observeHeaders(segments: List<Segment>) {
        viewModelScope.launch {
            availability.observe().collect { board ->
                val infos = segments.map { segment ->
                    val id = (segment.context as? ReadContext.Bookmark)?.id ?: return@map null
                    board.state(id)?.let { state ->
                        val p = state.progress
                        SegmentHeaderInfo(
                            description = state.bookmark.description?.takeIf { it.isNotBlank() },
                            readings = p.readings,
                            required = p.required,
                            fullReadings = p.fullReadings,
                            repeatTarget = p.repeatTarget,
                            done = p.done,
                        )
                    }
                }
                _state.update { it.copy(headers = infos) }
            }
        }
    }

    /** Khatm reader: where the khatm resumes and where its first unread ayah is. */
    private fun observeKhatmPosition() {
        viewModelScope.launch {
            val progress = withContext(io) { khatmProgress.get() }
            progress.observe(route.id).filterNotNull().collect { snapshot ->
                _state.update {
                    it.copy(
                        resume = snapshot.resume,
                        firstUnread = snapshot.firstUnread.takeIf { snapshot.firstUnreadDiffersFromResume },
                    )
                }
            }
        }
    }

    private fun rememberBrowsePosition() {
        viewModelScope.launch {
            firstVisible.filterNotNull().distinctUntilChanged().debounce(BROWSE_POSITION_DEBOUNCE_MILLIS).collect { ayah ->
                prefs.update { it.copy(lastBrowseAyahId = ayah) }
            }
        }
    }

    // ---- Text, loaded page by page as rows come into view (§7.7) ----------------------------------

    /** The list reports which rows are on screen. */
    fun onVisibleRows(firstRow: Int, lastRow: Int) {
        val model = model ?: return
        val first = model.ayahAtOrAfter(firstRow) ?: return
        val last = model.ayahAtOrAfter(lastRow.coerceAtLeast(firstRow)) ?: first
        firstVisible.value = first.id
        loadPagesAround(first, last)
    }

    private fun loadPagesAround(first: ReaderRow.Ayah, last: ReaderRow.Ayah) {
        val lo = minOf(first.page, last.page)
        val hi = maxOf(first.page, last.page)
        val wanted = (lo - PREFETCH_PAGES).coerceAtLeast(1)..(hi + PREFETCH_PAGES).coerceAtMost(PAGE_COUNT)
        for (page in wanted) {
            if (pages[page] != null || !pagesLoading.add(page)) continue
            viewModelScope.launch {
                val loaded = runCatching { withContext(io) { text.page(page) } }
                pagesLoading.remove(page)
                val ayahs = loaded.getOrNull() ?: return@launch
                pages[page] = ayahs.associate { it.id to it.text }
                val eldest = pages.keys.iterator()
                while (pages.size > CACHED_PAGES) {
                    eldest.next()
                    eldest.remove()
                }
                publishTexts()
            }
        }
    }

    private fun publishTexts() {
        val all = HashMap<AyahId, String>()
        for (page in pages.values) all.putAll(page)
        _state.update { it.copy(texts = all) }
    }

    // ---- Counting -------------------------------------------------------------------------------

    /** The check: +1, always — except that a khatm tick that skips ahead asks first (§4.2.1). */
    fun onCheck(row: ReaderRow.Ayah) = act {
        val context = row.context
        if (context is ReadContext.Khatm) {
            val gap = tracker.khatmGap(context.id, row.id)
            if (gap != null) {
                _state.update { it.copy(dialog = ReaderDialog.KhatmSkip(row, gap)) }
                return@act
            }
        }
        tracker.mark(row.id, context, ReadMethod.TAP)
    }

    /** "Mark n ayahs": fills the whole unread run that ends at the tapped ayah. */
    fun onSkipMarkAll() = act {
        val dialog = takeDialog<ReaderDialog.KhatmSkip>() ?: return@act
        offerUndo(tracker.markRange(dialog.gap, dialog.row.context, ReadMethod.BULK, RangeMode.FILL))
    }

    /** "Only this ayah": the khatm is resumed from a jumped-to position. */
    fun onSkipOnlyThis() = act {
        val dialog = takeDialog<ReaderDialog.KhatmSkip>() ?: return@act
        tracker.mark(dialog.row.id, dialog.row.context, ReadMethod.TAP)
    }

    fun onSetCountRequested(row: ReaderRow.Ayah) {
        _state.update { it.copy(dialog = ReaderDialog.SetCount(row, it.count(row))) }
    }

    fun onSetCount(count: Int) = act {
        val dialog = takeDialog<ReaderDialog.SetCount>() ?: return@act
        tracker.setCount(dialog.row.id, dialog.row.context, clock.today(), count)
    }

    /** From the start of the unread run through this ayah, one reading each; offers Undo. */
    fun onMarkUpToHere(row: ReaderRow.Ayah) = act {
        val scopeStart = scopeStarts.getOrNull(row.segment) ?: row.id
        val range = bulk.get().upToHere(row.id, row.context, scopeStart)
        offerUndo(tracker.markRange(range, row.context, ReadMethod.BULK, RangeMode.FILL))
    }

    fun onUndoBulk() = act {
        val batch = _state.value.bulk ?: return@act
        _state.update { it.copy(bulk = null) }
        tracker.undo(batch)
    }

    /** The snackbar went away without Undo. */
    fun onBulkNoticeShown(batch: Batch) {
        _state.update { if (it.bulk == batch) it.copy(bulk = null) else it }
    }

    private fun offerUndo(batch: Batch) {
        if (batch.added > 0) _state.update { it.copy(bulk = batch) }
    }

    // ---- Mark page, log a full reading ---------------------------------------------------------------

    /** "Mark page": the unread ayahs of [page] that lie in the scope of segment [segment]; offers Undo. */
    fun onMarkPage(page: Int, segment: Int) = act {
        val model = model ?: return@act
        val target = model.segments.getOrNull(segment) ?: return@act
        val range = bulk.get().page(page, target.range) ?: return@act
        offerUndo(tracker.markRange(range, target.context, ReadMethod.BULK, RangeMode.FILL))
    }

    /** "Log a full reading" of one bookmark: one more reading of every ayah of its passage; one Undo. */
    fun onLogFullReading(segment: Int) = act {
        val target = model?.segments?.getOrNull(segment) ?: return@act
        offerUndo(tracker.markRange(target.range, target.context, ReadMethod.BULK, RangeMode.ADD))
    }

    /** The same for every bookmark of the group at once, as one action with one Undo. */
    fun onLogGroupReading() = act {
        val segments = model?.segments ?: return@act
        offerUndo(tracker.markSegments(segments, ReadMethod.BULK, RangeMode.ADD))
    }

    // ---- Position sheet --------------------------------------------------------------------------

    fun onSheet(sheet: ReaderSheet?) {
        _state.update { it.copy(sheet = sheet, jumpText = "", jumpProblem = null) }
    }

    fun onJumpText(text: String) {
        _state.update { it.copy(jumpText = text, jumpProblem = null) }
    }

    /** The sheet's go-to field: `2:255`, `255` in the surah being read, `p300`, `j12`; must lie in the scope. */
    fun onJumpSubmit() {
        val index = quran ?: return
        val model = model ?: return
        val text = _state.value.jumpText
        val currentSurah = firstVisible.value?.let { index.ref(it).surah }
        when (val result = GoToParser.parse(text, currentSurah, index)) {
            is GoToResult.Invalid -> _state.update { it.copy(jumpProblem = GoToProblem.of(result.reason)) }
            else -> {
                // A page or juz may begin before the scope does: land on the first ayah of it that is in scope.
                val wanted = when (result) {
                    is GoToResult.Page -> index.pageRange(result.page)
                    is GoToResult.Juz -> index.juzRange(result.juz)
                    else -> null
                }
                val target = wanted?.let { w -> model.segments.firstNotNullOfOrNull { it.range.intersect(w)?.start } } ?: result.target
                if (target == null || !scrollToAyah(target)) {
                    _state.update { it.copy(jumpProblem = GoToProblem.OUTSIDE_SCOPE) }
                }
            }
        }
    }

    fun onJumpToAyah(ayah: AyahId) {
        scrollToAyah(ayah)
    }

    fun onJumpToSurah(number: Int) {
        val index = quran ?: return
        val wanted = index.surahRange(number)
        model?.segments?.firstNotNullOfOrNull { it.range.intersect(wanted)?.start }?.let(::scrollToAyah)
    }

    fun onJumpToJuz(number: Int) {
        val index = quran ?: return
        val wanted = index.juzRange(number)
        model?.segments?.firstNotNullOfOrNull { it.range.intersect(wanted)?.start }?.let(::scrollToAyah)
    }

    /** The list has scrolled to the request. */
    fun onScrollHandled(request: ScrollRequest) {
        _state.update { if (it.scrollRequest == request) it.copy(scrollRequest = null) else it }
    }

    private fun scrollToAyah(ayah: AyahId): Boolean {
        val model = model ?: return false
        val row = model.find(ayah) ?: return false
        _state.update { it.copy(scrollRequest = ScrollRequest(model.blockStart(row), ++scrollToken), sheet = null) }
        return true
    }

    // ---- "Aa" panel ------------------------------------------------------------------------------

    /** Live while dragging; stored in the preferences, which the reader follows. */
    fun onFontSize(sp: Int) {
        val clamped = sp.coerceIn(UserPrefs.MIN_FONT_SP, UserPrefs.MAX_FONT_SP)
        _state.update { it.copy(fontSp = clamped) }
        viewModelScope.launch { prefs.update { it.copy(quranFontSp = clamped) } }
    }

    fun onBreakAtPauseMarks(on: Boolean) {
        viewModelScope.launch { prefs.update { it.copy(breakAtPauseMarks = on) } }
    }

    fun onKeepScreenOn(on: Boolean) {
        viewModelScope.launch { prefs.update { it.copy(keepScreenOn = on) } }
    }

    // ---- Star -----------------------------------------------------------------------------------

    fun onStar(row: ReaderRow.Ayah) = act {
        val result = library.toggleStar(row.id)
        if (result is StarResult.NeedsConfirmation) {
            _state.update { it.copy(dialog = ReaderDialog.ConfirmUnstar(row, result.bookmark)) }
        }
    }

    fun onUnstarConfirmed() = act {
        val dialog = takeDialog<ReaderDialog.ConfirmUnstar>() ?: return@act
        library.toggleStar(dialog.row.id, confirmed = true)
    }

    fun onDialogDismissed() {
        _state.update { it.copy(dialog = null) }
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private inline fun <reified T : ReaderDialog> takeDialog(): T? {
        val dialog = _state.value.dialog as? T
        _state.update { it.copy(dialog = null) }
        return dialog
    }

    /** A user action. A domain failure (a bookmark deleted meanwhile, …) leaves the screen as it is. */
    private fun act(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (_: DomainFailure) {
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
        /** Pages of text kept in memory (§7.7). */
        const val CACHED_PAGES = 30
        const val PREFETCH_PAGES = 1
        const val BROWSE_POSITION_DEBOUNCE_MILLIS = 500L
        private const val PAGE_COUNT = 604
    }
}
