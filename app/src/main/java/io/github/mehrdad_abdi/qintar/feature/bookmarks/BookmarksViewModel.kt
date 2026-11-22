package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.Batch
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkBoard
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkState
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.GroupState
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.playback.PlaybackOrigin
import io.github.mehrdad_abdi.qintar.playback.PlayRequest
import io.github.mehrdad_abdi.qintar.playback.PlayerController
import io.github.mehrdad_abdi.qintar.playback.PlayerState
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A bookmark as the lists show it: its state at this moment and its title as data. */
data class BookmarkItem(val state: BookmarkState, val title: BookmarkTitle) {
    val id: Long get() = state.bookmark.id
}

/** A group with its bookmarks. */
data class GroupItem(val state: GroupState, val items: List<BookmarkItem>) {
    val id: Long get() = state.group.id
}

/** A group a bookmark can be moved or copied to. */
data class GroupChoice(val id: Long, val name: String)

/** The list's filter (§5.6): every bookmark, or those not yet read today. */
enum class BookmarksFilter { ALL, UNREAD }

/** Key of a section in the list: a group's id, or [UNGROUPED] for the bookmarks without one. */
const val UNGROUPED = -1L

/** The playback origin of the list: audio started here plays every listed bookmark, in order. */
val BOOKMARKS_ORIGIN = PlaybackOrigin(ReaderEntry.BOOKMARKS)

/** One row of the Bookmarks list. */
sealed interface BookmarksRow {
    val key: String

    /** A group's header, or Ungrouped's when [group] is null. */
    data class Section(
        val group: GroupItem?,
        val bookmarkCount: Int,
        val doneCount: Int,
        val expanded: Boolean,
    ) : BookmarksRow {
        val sectionKey: Long get() = group?.id ?: UNGROUPED
        override val key: String get() = "section-$sectionKey"
    }

    data class Entry(val item: BookmarkItem, val expanded: Boolean) : BookmarksRow {
        override val key: String get() = "bookmark-${item.id}"
    }

    /** An ayah of an expanded bookmark. */
    data class Ayah(
        val bookmarkId: Long,
        val id: AyahId,
        val surah: Int,
        val number: Int,
        val sajda: SajdaType,
    ) : BookmarksRow {
        override val key: String get() = "ayah-$bookmarkId-${id.value}"
    }

    /** Under the last ayah of an expanded bookmark: "Mark read" for all its ayahs, until it is done. */
    data class End(val bookmarkId: Long, val done: Boolean) : BookmarksRow {
        override val key: String get() = "end-$bookmarkId"
    }
}

/** Where the player is in the list: an ayah of a bookmark. */
data class ListAudio(val bookmarkId: Long, val ayah: AyahId)

data class ListScrollRequest(val row: Int, val token: Int)

enum class UndoKind { LOGGED, GROUP_LOGGED, COUNT_SET, DELETED }

/** The snackbar with Undo. [id] tells a new notice from the one already shown. */
data class UndoNotice(val id: Int, val kind: UndoKind, val batch: Batch? = null, val bookmark: Bookmark? = null)

sealed interface BookmarksDialog {
    data class SetCount(val bookmarkId: Long, val title: BookmarkTitle, val current: Int) : BookmarksDialog

    /** Choose the group to move the bookmark to, or to copy it into when [duplicate]. */
    data class PickGroup(val bookmarkId: Long, val duplicate: Boolean, val choices: List<GroupChoice>, val canUngroup: Boolean) : BookmarksDialog

    data class DeleteGroup(val groupId: Long, val name: String, val bookmarkCount: Int) : BookmarksDialog
}

data class BookmarksUiState(
    val loading: Boolean = true,
    val filter: BookmarksFilter = BookmarksFilter.ALL,
    /** Groups in their custom order. */
    val groups: List<GroupItem> = emptyList(),
    val ungrouped: List<BookmarkItem> = emptyList(),
    /** Every bookmark, whatever the filter. */
    val all: List<BookmarkItem> = emptyList(),
    /** What the list shows, in order. */
    val rows: List<BookmarksRow> = emptyList(),
    /** Quran text of the ayahs of expanded bookmarks. */
    val texts: Map<AyahId, String> = emptyMap(),
    /** Today's readings per bookmark and ayah, counted under that bookmark. */
    val counts: Map<Long, Map<AyahId, Int>> = emptyMap(),
    val audio: ListAudio? = null,
    val audioPlaying: Boolean = false,
    val fontSp: Int = io.github.mehrdad_abdi.qintar.domain.model.UserPrefs().quranFontSp,
    val breakAtPauseMarks: Boolean = true,
    val scrollRequest: ListScrollRequest? = null,
    val notice: UndoNotice? = null,
    val dialog: BookmarksDialog? = null,
) {
    val hasBookmarks: Boolean get() = all.isNotEmpty()

    fun count(row: BookmarksRow.Ayah): Int = counts[row.bookmarkId]?.get(row.id) ?: 0

    fun isAudioAt(row: BookmarksRow.Ayah): Boolean = audio?.let { it.bookmarkId == row.bookmarkId && it.ayah == row.id } == true
}

/**
 * The Bookmarks tab (§5.6): one list of groups, then Ungrouped, each bookmark with its ayahs inline.
 * Two levels fold: a group hides its bookmarks, a bookmark hides its ayahs. Ticks count under the
 * bookmark; play goes on through every following bookmark of the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val index: Provider<QuranIndex>,
    private val availability: Availability,
    private val library: BookmarkLibrary,
    private val tracker: ReadingTracker,
    private val log: ReadLogStore,
    private val text: QuranTextSource,
    private val prefs: PrefsStore,
    private val clock: DayClock,
    private val player: PlayerController,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(BookmarksUiState())
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    private var board: BookmarkBoard? = null
    private var quran: QuranIndex? = null
    private var noticeCounter = 0
    private var scrollToken = 0

    /** Sections folded by the user; on open every group is open (owner's decision). */
    private val collapsedSections = HashSet<Long>()

    /** Bookmarks showing their ayahs; on open none is. */
    private val expandedBookmarks = HashSet<Long>()

    /** Under Unread: bookmarks already shown, kept when read until the screen is left. */
    private val keptUnread = HashSet<Long>()

    private val loadedTexts = HashMap<AyahId, String>()
    private val loadingTexts = HashSet<Long>()

    /** Rows on screen, as the list last reported them. */
    private var visibleRows = IntRange.EMPTY

    init {
        viewModelScope.launch {
            prefs.prefs.collect { p -> _state.update { it.copy(fontSp = p.quranFontSp, breakAtPauseMarks = p.breakAtPauseMarks) } }
        }
        viewModelScope.launch {
            clock.todayFlow.flatMapLatest { day -> log.observeBookmarkAyahCounts(day) }.collect { list ->
                val counts = HashMap<Long, HashMap<AyahId, Int>>()
                for (c in list) counts.getOrPut(c.bookmarkId) { HashMap() }[c.ayahId] = c.count
                _state.update { it.copy(counts = counts) }
            }
        }
        viewModelScope.launch {
            val index = withContext(io) { index.get() }
            quran = index
            launch { player.state.collect(::onPlayerState) }
            availability.observe().collect { board ->
                this@BookmarksViewModel.board = board
                _state.update { board.toUi(index, it) }
                rebuild()
            }
        }
    }

    // ---- the list ------------------------------------------------------------------------------

    fun onFilter(filter: BookmarksFilter) {
        keptUnread.clear()
        _state.update { it.copy(filter = filter) }
        rebuild()
    }

    /** The screen was left: bookmarks read meanwhile leave the Unread list. */
    fun onScreenLeft() {
        if (keptUnread.isEmpty()) return
        keptUnread.clear()
        rebuild()
    }

    fun onToggleSection(sectionKey: Long) {
        if (!collapsedSections.remove(sectionKey)) collapsedSections += sectionKey
        rebuild()
    }

    fun onToggleBookmark(bookmarkId: Long) {
        if (!expandedBookmarks.remove(bookmarkId)) expandedBookmarks += bookmarkId
        rebuild()
    }

    fun onVisibleRows(first: Int, last: Int) {
        visibleRows = first..last
    }

    fun onScrollHandled(request: ListScrollRequest) = _state.update { if (it.scrollRequest == request) it.copy(scrollRequest = null) else it }

    /** What the filter lets through, in list order, with their sections. */
    private fun visibleSections(state: BookmarksUiState): List<Pair<GroupItem?, List<BookmarkItem>>> {
        fun pass(item: BookmarkItem): Boolean = when (state.filter) {
            BookmarksFilter.ALL -> true
            BookmarksFilter.UNREAD -> (!item.state.progress.done || item.id in keptUnread).also { if (it) keptUnread += item.id }
        }
        val sections = state.groups.map { it to it.items.filter(::pass) } + (null to state.ungrouped.filter(::pass))
        return when (state.filter) {
            // Every group is listed, even an empty one; Ungrouped only when it has bookmarks.
            BookmarksFilter.ALL -> sections.filter { (group, items) -> group != null || items.isNotEmpty() }
            BookmarksFilter.UNREAD -> sections.filter { it.second.isNotEmpty() }
        }
    }

    private fun rebuild() {
        val index = quran ?: return
        val state = _state.value
        val rows = ArrayList<BookmarksRow>()
        for ((group, items) in visibleSections(state)) {
            val all = group?.items ?: state.ungrouped
            val key = group?.id ?: UNGROUPED
            val open = key !in collapsedSections
            rows += BookmarksRow.Section(group, all.size, all.count { it.state.progress.done }, open)
            if (!open) continue
            for (item in items) {
                val expanded = item.id in expandedBookmarks
                rows += BookmarksRow.Entry(item, expanded)
                if (!expanded) continue
                loadTexts(item)
                for (id in item.state.bookmark.range) {
                    val ref = index.ref(id)
                    rows += BookmarksRow.Ayah(item.id, id, ref.surah, ref.ayah, index.sajdaOf(id))
                }
                rows += BookmarksRow.End(item.id, item.state.progress.done)
            }
        }
        _state.update { it.copy(rows = rows) }
    }

    private fun loadTexts(item: BookmarkItem) {
        val range = item.state.bookmark.range
        if (range.start in loadedTexts && range.end in loadedTexts) return
        if (!loadingTexts.add(item.id)) return
        viewModelScope.launch {
            val ayahs = runCatching { withContext(io) { text.ayahs(range) } }.getOrNull()
            loadingTexts.remove(item.id)
            if (ayahs != null) {
                ayahs.forEach { loadedTexts[it.id] = it.text }
                _state.update { it.copy(texts = HashMap(loadedTexts)) }
            }
        }
    }

    // ---- reading -------------------------------------------------------------------------------

    /** The check of an inline ayah: one more reading, under its bookmark. */
    fun onCheck(row: BookmarksRow.Ayah) = act {
        tracker.mark(row.id, ReadContext.Bookmark(row.bookmarkId), ReadMethod.TAP)
    }

    /** "Mark read" at the end of a bookmark: one reading for each of its ayahs not read today, with Undo. */
    fun onMarkBookmarkRead(bookmarkId: Long) = act {
        val bookmark = bookmarkOf(bookmarkId) ?: return@act
        val batch = tracker.markRange(bookmark.range, ReadContext.Bookmark(bookmark.id), ReadMethod.BULK, RangeMode.FILL)
        notify(UndoKind.LOGGED, batch = batch)
    }

    // ---- audio ---------------------------------------------------------------------------------

    /**
     * What a play started here plays: the listed bookmarks in list order — under Unread only those
     * not read yet — and always [from], the one asked for.
     */
    private fun playlist(from: Long): List<BookmarkItem> {
        val state = _state.value
        val listed = visibleSections(state).flatMap { it.second }
        return listed.filter { it.id == from || state.filter == BookmarksFilter.ALL || !it.state.progress.done }
    }

    private fun play(bookmarkId: Long, ayah: AyahId?) {
        val list = playlist(bookmarkId)
        val at = list.indexOfFirst { it.id == bookmarkId }
        if (at < 0) return
        val progress = list[at].state
        val start = ayah ?: progress.progress.firstBelowTarget ?: progress.bookmark.range.start
        player.play(PlayRequest(BOOKMARKS_ORIGIN, list.map { it.state.segment }, at, start))
    }

    /** The play button of a bookmark: from its first unread ayah, then on through the list. */
    fun onPlayBookmark(bookmarkId: Long) = play(bookmarkId, null)

    /** The play button of a section: from its first bookmark not read today. */
    fun onPlaySection(sectionKey: Long) {
        val state = _state.value
        val items = if (sectionKey == UNGROUPED) state.ungrouped else state.groups.firstOrNull { it.id == sectionKey }?.items.orEmpty()
        val first = items.firstOrNull { !it.state.progress.done } ?: items.firstOrNull() ?: return
        play(first.id, null)
    }

    /** The play button of an ayah row: pause or resume when it is the ayah in the player, otherwise play from it. */
    fun onPlayAyah(row: BookmarksRow.Ayah) {
        if (_state.value.isAudioAt(row)) player.togglePlayPause() else play(row.bookmarkId, row.id)
    }

    /**
     * The playing ayah is highlighted; its bookmark (and section) opens so it can be seen, and the
     * list follows it while the listener stays with it.
     */
    private fun onPlayerState(p: PlayerState) {
        val item = p.item
        val now = if (p.origin == BOOKMARKS_ORIGIN && item != null) {
            (item.context as? ReadContext.Bookmark)?.let { ListAudio(it.id, item.ayah) }
        } else {
            null
        }
        val before = _state.value.audio
        _state.update { it.copy(audio = now, audioPlaying = now != null && (p.playing || p.loading)) }
        if (now == null || now == before) return
        if (before?.bookmarkId != now.bookmarkId) {
            val section = _state.value.all.firstOrNull { it.id == now.bookmarkId }?.state?.bookmark?.groupId ?: UNGROUPED
            val changed = expandedBookmarks.add(now.bookmarkId) or collapsedSections.remove(section)
            if (changed) rebuild()
        }
        val rows = _state.value.rows
        val row = rows.indexOfFirst { it is BookmarksRow.Ayah && it.bookmarkId == now.bookmarkId && it.id == now.ayah }
        if (row < 0) return
        val previous = before?.let { b -> rows.indexOfFirst { it is BookmarksRow.Ayah && it.bookmarkId == b.bookmarkId && it.id == b.ayah } } ?: -1
        val shown = visibleRows
        if (before == null || (previous in shown && (row >= shown.last || row < shown.first))) {
            _state.update { it.copy(scrollRequest = ListScrollRequest(row, ++scrollToken)) }
        }
    }

    // ---- bookmark actions ----------------------------------------------------------------------

    /** "Log a full reading": one more reading of every ayah of the passage, with one Undo. */
    fun onLogFullReading(bookmarkId: Long) = act {
        val bookmark = bookmarkOf(bookmarkId) ?: return@act
        val batch = tracker.markRange(bookmark.range, ReadContext.Bookmark(bookmark.id), ReadMethod.BULK, RangeMode.ADD)
        notify(UndoKind.LOGGED, batch = batch)
    }

    fun onSetCountRequested(bookmarkId: Long) {
        val item = itemOf(bookmarkId) ?: return
        _state.update { it.copy(dialog = BookmarksDialog.SetCount(bookmarkId, item.title, item.state.progress.fullReadings)) }
    }

    /** Every ayah of the passage gets exactly [count] readings today. */
    fun onSetCount(count: Int) = act {
        val dialog = takeDialog<BookmarksDialog.SetCount>() ?: return@act
        val bookmark = bookmarkOf(dialog.bookmarkId) ?: return@act
        val batch = tracker.setRangeCount(bookmark.range, ReadContext.Bookmark(bookmark.id), clock.today(), count)
        if (batch != null) notify(UndoKind.COUNT_SET, batch = batch)
    }

    /** Deletes the bookmark; Undo [BookmarkLibrary.restore]s it. Its readings were never touched. */
    fun onDelete(bookmarkId: Long) = act {
        val deleted = library.delete(bookmarkId)
        notify(UndoKind.DELETED, bookmark = deleted)
    }

    fun onMoveRequested(bookmarkId: Long) = requestGroup(bookmarkId, duplicate = false)

    fun onDuplicateRequested(bookmarkId: Long) = requestGroup(bookmarkId, duplicate = true)

    private fun requestGroup(bookmarkId: Long, duplicate: Boolean) {
        val item = itemOf(bookmarkId) ?: return
        val own = item.state.bookmark.groupId
        val choices = board?.groups.orEmpty().filter { it.group.id != own }.map { GroupChoice(it.group.id, it.group.name) }
        _state.update { it.copy(dialog = BookmarksDialog.PickGroup(bookmarkId, duplicate, choices, canUngroup = own != null)) }
    }

    /** [groupId] null = Ungrouped. */
    fun onGroupChosen(groupId: Long?) = act {
        val dialog = takeDialog<BookmarksDialog.PickGroup>() ?: return@act
        if (dialog.duplicate) library.duplicateToGroup(dialog.bookmarkId, groupId) else library.moveToGroup(dialog.bookmarkId, groupId)
    }

    // ---- group actions -------------------------------------------------------------------------

    /** One more reading of every bookmark of the group, as one action with one Undo. */
    fun onLogGroupReading(groupId: Long) = act {
        val group = board?.groups?.firstOrNull { it.group.id == groupId } ?: return@act
        if (group.bookmarks.isEmpty()) return@act
        val batch = tracker.markSegments(group.segments, ReadMethod.BULK, RangeMode.ADD)
        notify(UndoKind.GROUP_LOGGED, batch = batch)
    }

    fun onDeleteGroupRequested(groupId: Long) {
        val group = board?.groups?.firstOrNull { it.group.id == groupId } ?: return
        _state.update { it.copy(dialog = BookmarksDialog.DeleteGroup(groupId, group.group.name, group.bookmarks.size)) }
    }

    /** [deleteBookmarks] false keeps the group's bookmarks, ungrouped. Readings are kept either way. */
    fun onDeleteGroupConfirmed(deleteBookmarks: Boolean) = act {
        val dialog = takeDialog<BookmarksDialog.DeleteGroup>() ?: return@act
        library.deleteGroup(dialog.groupId, deleteBookmarks)
    }

    // ---- notice and dialogs --------------------------------------------------------------------

    fun onUndo() = act {
        val notice = _state.value.notice ?: return@act
        _state.update { it.copy(notice = null) }
        notice.batch?.let { tracker.undo(it) }
        notice.bookmark?.let { library.restore(it) }
    }

    /** The snackbar went away without Undo. */
    fun onNoticeShown(id: Int) = _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    fun onDialogDismissed() = _state.update { it.copy(dialog = null) }

    private fun notify(kind: UndoKind, batch: Batch? = null, bookmark: Bookmark? = null) {
        _state.update { it.copy(notice = UndoNotice(++noticeCounter, kind, batch, bookmark)) }
    }

    private fun itemOf(bookmarkId: Long): BookmarkItem? = _state.value.all.firstOrNull { it.id == bookmarkId }

    private fun bookmarkOf(bookmarkId: Long): Bookmark? = itemOf(bookmarkId)?.state?.bookmark

    private inline fun <reified T : BookmarksDialog> takeDialog(): T? {
        val dialog = _state.value.dialog as? T
        _state.update { it.copy(dialog = null) }
        return dialog
    }

    private fun act(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (_: DomainFailure) {
            // The bookmark or group was removed meanwhile; the board shows the truth.
        }
    }
}

internal fun BookmarkBoard.toUi(index: QuranIndex, previous: BookmarksUiState): BookmarksUiState {
    val cache = HashMap<Long, BookmarkItem>()
    fun item(state: BookmarkState) = cache.getOrPut(state.bookmark.id) { BookmarkItem(state, BookmarkTitles.of(state.bookmark, index)) }
    fun items(states: List<BookmarkState>) = states.map(::item)
    return previous.copy(
        loading = false,
        groups = groups.map { GroupItem(it, items(it.bookmarks)) },
        ungrouped = items(ungrouped),
        all = items(all),
    )
}
