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
import io.github.mehrdad_abdi.qintar.playback.AudioDownloads
import io.github.mehrdad_abdi.qintar.playback.DownloadState
import io.github.mehrdad_abdi.qintar.playback.PlayRequest
import io.github.mehrdad_abdi.qintar.playback.PlayerController
import io.github.mehrdad_abdi.qintar.playback.PlayerState
import io.github.mehrdad_abdi.qintar.playback.origin
import io.github.mehrdad_abdi.qintar.ui.common.GoToProblem
import io.github.mehrdad_abdi.qintar.ui.common.RankReachedDetector
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahCheckState
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute
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

    /** Every bookmark (the Bookmarks list's playback). */
    data object AllBookmarks : ReaderTitle
}

sealed interface ReaderDialog {
    val row: ReaderRow.Ayah

    /** A khatm tick that would leave [gap] minus the tapped ayah unread behind it (§4.2.1). */
    data class KhatmSkip(override val row: ReaderRow.Ayah, val gap: AyahRange) : ReaderDialog

    /** The star belongs to a bookmark that carries more than a star (§4.7). */
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

/** The ayah of this reader that is loaded in the player. */
/** What the player is on: an ayah, or the Bismillah before [ayah] (its ayah 1) when [basmala]. */
data class AudioPosition(val segment: Int, val ayah: AyahId, val basmala: Boolean = false)

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
    /** Ayahs in a bookmark, with the bookmark their filled star opens. */
    val bookmarkedBy: Map<AyahId, Long> = emptyMap(),
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
    /** Where the player is in this reader; null when nothing is loaded or the audio is another reader's. */
    val audioAt: AudioPosition? = null,
    /** Sound is coming out (or about to) at [audioAt]. */
    val audioPlaying: Boolean = false,
    val download: DownloadState = DownloadState.Idle,
) {
    val rows: List<ReaderRow> get() = model?.rows.orEmpty()

    /** The row whose ayah is being played: it is highlighted and its button pauses. */
    fun isPlaying(row: ReaderRow.Ayah): Boolean = audioPlaying && isAudioAt(row)

    fun isAudioAt(row: ReaderRow.Ayah): Boolean = audioAt?.let { !it.basmala && it.segment == row.segment && it.ayah == row.id } == true

    /** The Bismillah before a surah is its own playing row (owner, 2026-11-24). */
    fun isAudioAt(row: ReaderRow.Basmala): Boolean = audioAt?.let { it.basmala && it.segment == row.segment && it.ayah == model?.surahInfo(row.surah)?.firstAyahId } == true

    /** A passage smaller than the whole Quran can be downloaded as one. */
    val canDownloadPassage: Boolean get() = entry != ReaderEntry.KHATM && entry != ReaderEntry.BROWSE

    /** Readings of this row's ayah today, in this row's context. */
    fun count(row: ReaderRow.Ayah): Int = counts.getOrNull(row.segment)?.get(row.id) ?: 0

    /**
     * Whether the page that ends just above row [belowRow] has an ayah still unread in its context:
     * "Mark page" is offered only then. [belowRow] is a page divider's row, or the row count for the
     * last page of the scope.
     */
    /** "Mark page" at the divider [row]: not inside a bookmark, which has its own Mark read at its end. */
    fun pageMarkOffered(row: Int): Boolean {
        val divider = model?.rows?.getOrNull(row) ?: return false
        if (model.segments.getOrNull(divider.segment)?.context is ReadContext.Bookmark) return false
        return pageAboveHasUnread(row)
    }

    /** Whether the bookmark of [segment] has an ayah not read today: its Mark read is offered. */
    fun segmentHasUnread(segment: Int): Boolean =
        model?.rows?.any { it is ReaderRow.Ayah && it.segment == segment && checkState(it) == AyahCheckState.Unread } == true

    /** Whether the surah that ends at the [SurahEnd][ReaderRow.SurahEnd] row [endRow] has an unread ayah. */
    fun surahHasUnread(endRow: Int): Boolean {
        val rows = model?.rows ?: return false
        var i = endRow - 1
        while (i >= 0) {
            when (val row = rows[i]) {
                is ReaderRow.Ayah -> if (checkState(row) == AyahCheckState.Unread) return true
                is ReaderRow.SurahHeader, is ReaderRow.SegmentHeader -> return false
                else -> Unit
            }
            i--
        }
        return false
    }

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
    private val player: PlayerController,
    private val downloads: AudioDownloads,
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

    /** Rows on screen, as the list last reported them. */
    private var visibleRows = IntRange.EMPTY

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
            library.observeBookmarkedAyahs().collect { map -> _state.update { it.copy(bookmarkedBy = map) } }
        }
        viewModelScope.launch {
            downloads.state.collect { download -> _state.update { it.copy(download = download) } }
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
            // Back from the mini player: the reader opens where the audio is.
            val audio = audioPosition(player.state.value).takeIf { route.startAyahId == ReaderRoute.UNSET }
            val audioRow = audio?.let { opened.model.indexOf(it.segment, it.ayah) }
            firstVisible.value = audio?.ayah ?: opened.start
            _state.update {
                it.copy(
                    phase = ReaderPhase.READY,
                    title = opened.title,
                    segmentTitles = opened.segmentTitles,
                    model = opened.model,
                    initialRowIndex = audioRow?.let(opened.model::blockStart) ?: opened.startRow,
                    assumedCoveredUpTo = opened.assumedCoveredUpTo,
                    scopeSurahs = opened.scopeSurahs,
                    scopeJuz = opened.scopeJuz,
                )
            }
            opened.model.ayahAtOrAfter(audioRow ?: opened.startRow)?.let { loadPagesAround(it, it) }
            observeReadState(opened.model.segments)
            if (route.entry == ReaderEntry.BOOKMARK || route.entry == ReaderEntry.GROUP || route.entry == ReaderEntry.BOOKMARKS) observeHeaders(opened.model.segments)
            if (route.entry == ReaderEntry.KHATM) observeKhatmPosition()
            if (route.entry == ReaderEntry.BROWSE) rememberBrowsePosition()
            player.state.collect(::onPlayerState)
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
            ReaderEntry.BOOKMARKS -> {
                val listed = availability.board().listed
                if (listed.isEmpty()) return null
                val model = ReaderRowModel.build(listed.map { it.segment }, index, segmentHeaders = true)
                val first = listed.firstOrNull { !it.progress.done } ?: listed.first()
                val segment = listed.indexOf(first)
                Opened(
                    model = model,
                    title = ReaderTitle.AllBookmarks,
                    start = first.progress.firstBelowTarget ?: first.bookmark.range.start,
                    startRow = model.rows.indexOfFirst { it.segment == segment },
                    scopeStarts = listed.map { it.bookmark.range.start },
                    segmentTitles = listed.map { BookmarkTitles.of(it.bookmark, index) },
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
        visibleRows = firstRow..lastRow.coerceAtLeast(firstRow)
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

    /** "Mark surah read": one reading for each unread ayah of [surah] inside the segment's scope. */
    fun onMarkSurah(surah: Int, segment: Int) = act {
        val target = model?.segments?.getOrNull(segment) ?: return@act
        val whole = quran?.surahRange(surah) ?: return@act
        val from = maxOf(whole.start.value, target.range.start.value)
        val to = minOf(whole.end.value, target.range.end.value)
        if (from > to) return@act
        offerUndo(tracker.markRange(AyahRange(AyahId(from), AyahId(to)), target.context, ReadMethod.BULK, RangeMode.FILL))
    }

    /** "Mark read" at the end of a bookmark: one reading for each of its ayahs not read today. */
    fun onMarkSegment(segment: Int) = act {
        val target = model?.segments?.getOrNull(segment) ?: return@act
        offerUndo(tracker.markRange(target.range, target.context, ReadMethod.BULK, RangeMode.FILL))
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

    // ---- Audio (§7.8) ------------------------------------------------------------------------------

    /** Where the player is, if what it plays was started from this reader. */
    private fun audioPosition(p: PlayerState): AudioPosition? {
        val item = p.item ?: return null
        return if (p.origin == route.origin()) AudioPosition(item.segment, item.ayah, item.basmala) else null
    }

    /**
     * The playing ayah is highlighted, and the list follows it while the listener stays with it: the
     * ayah before it was on screen and the new one is not. Scrolling away stops the following.
     */
    private fun onPlayerState(p: PlayerState) {
        val model = model ?: return
        val before = _state.value.audioAt
        val now = audioPosition(p)
        _state.update { it.copy(audioAt = now, audioPlaying = now != null && (p.playing || p.loading)) }
        if (now == null || now == before) return
        val row = rowOf(model, now) ?: return
        val previousRow = before?.let { rowOf(model, it) } ?: return
        val shown = visibleRows
        if (previousRow in shown && (row >= shown.last || row < shown.first)) {
            _state.update { it.copy(scrollRequest = ScrollRequest(model.blockStart(row), ++scrollToken)) }
        }
    }

    /** The row of [at]: its ayah, or the Bismillah row just before it. */
    private fun rowOf(model: ReaderRowModel, at: AudioPosition): Int? =
        model.indexOf(at.segment, at.ayah)?.let { if (at.basmala && model.rows.getOrNull(it - 1) is ReaderRow.Basmala) it - 1 else it }

    /** The play button of a row: pause or resume when it is the ayah in the player, otherwise play from it. */
    fun onPlay(row: ReaderRow.Ayah) {
        val model = model ?: return
        if (_state.value.isAudioAt(row)) {
            player.togglePlayPause()
        } else {
            player.play(PlayRequest(route.origin(), model.segments, row.segment, row.id))
        }
    }

    /** The audio menu's "Play from here": from the first ayah on screen. */
    fun onPlayFromHere() {
        val model = model ?: return
        val row = model.ayahAtOrAfter(visibleRows.first.coerceAtLeast(0)) ?: return
        player.play(PlayRequest(route.origin(), model.segments, row.segment, row.id))
    }

    /** The player's title was tapped in this reader: back to the ayah being played. */
    fun onShowPlaying() {
        val at = _state.value.audioAt ?: return
        val model = model ?: return
        val row = model.indexOf(at.segment, at.ayah) ?: return
        _state.update { it.copy(scrollRequest = ScrollRequest(model.blockStart(row), ++scrollToken)) }
    }

    /** "Download this surah for offline": the whole surah of the first ayah on screen. */
    fun onDownloadSurah() {
        val index = quran ?: return
        val ayah = firstVisible.value ?: return
        downloads.download(listOf(index.surahRange(index.ref(ayah).surah)))
    }

    /** "Download this passage for offline": everything this reader shows. */
    fun onDownloadPassage() {
        val model = model ?: return
        if (_state.value.canDownloadPassage) downloads.download(model.segments.map { it.range })
    }

    fun onDownloadCancel() = downloads.cancel()

    /** The outcome of a download has been shown. */
    fun onDownloadNoticeShown() = downloads.acknowledge()

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

    /** "Today's readings": the list of today's readings, where one can be deleted (owner, 2026-11-22). */
    fun todayReadingsRoute(): DayDetailRoute = DayDetailRoute(clock.today().toString())

    /**
     * Where the star of [row] leads (owner, 2026-11-22): the editor of the bookmark the ayah is in, or
     * a new bookmark filled with the ayah, where a description and the end of the range can be added.
     */
    fun starTarget(row: ReaderRow.Ayah): BookmarkEditorRoute {
        val id = _state.value.bookmarkedBy[row.id]
        return if (id != null) BookmarkEditorRoute(bookmarkId = id) else BookmarkEditorRoute.forAyah(row.id.value)
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
