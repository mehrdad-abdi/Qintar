package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.Batch
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.AyahText
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import io.github.mehrdad_abdi.qintar.domain.service.BoardItem
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkBoard
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkState
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.domain.service.GroupState
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** An entry of the list's top level: a group, or a bookmark without one (owner, 2026-11-25). */
sealed interface TopItem {
    data class OfGroup(val group: GroupItem) : TopItem

    data class Loose(val item: BookmarkItem) : TopItem
}

/** One row of the Bookmarks list: a group's card or a bookmark's card. No ayahs: they are read in the reader (notes item 47). */
sealed interface BookmarksRow {
    val key: String

    /** A group's card. */
    data class Section(
        val group: GroupItem,
        val bookmarkCount: Int,
        val doneCount: Int,
        val expanded: Boolean,
    ) : BookmarksRow {
        val sectionKey: Long get() = group.id
        override val key: String get() = "section-$sectionKey"
    }

    /** A bookmark's card; [inGroup] when it is listed under its group, indented. */
    data class Entry(val item: BookmarkItem, val inGroup: Boolean) : BookmarksRow {
        override val key: String get() = "bookmark-${item.id}"
    }
}

/**
 * The Quran text a bookmark's card shows (owner, 2026-11-26): [head] is the start of the passage and
 * [tail] its last ayah; [tail] is null when [head] is the whole passage. Each is formatted as the
 * reader formats it, flowing (no breaks at pause marks). The card shows all of a passage that fits in
 * three lines, otherwise a line of the start, "…", and a line of the end.
 */
data class Glimpse(val head: String, val tail: String?)

enum class UndoKind { LOGGED, GROUP_LOGGED, DELETED }

/** The snackbar with Undo. [id] tells a new notice from the one already shown. */
data class UndoNotice(val id: Int, val kind: UndoKind, val batch: Batch? = null, val bookmark: Bookmark? = null)

sealed interface BookmarksDialog {
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
    /** The list's top level in the chosen order: groups and bookmarks without one. */
    val top: List<TopItem> = emptyList(),
    val sort: io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort = io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort.MUSHAF,
    /** Every bookmark, whatever the filter. */
    val all: List<BookmarkItem> = emptyList(),
    /** What the list shows, in order. */
    val rows: List<BookmarksRow> = emptyList(),
    /** The text of each passage's card, by passage, loaded for the cards on screen. */
    val glimpses: Map<AyahRange, Glimpse> = emptyMap(),
    val notice: UndoNotice? = null,
    val dialog: BookmarksDialog? = null,
) {
    val hasBookmarks: Boolean get() = all.isNotEmpty()

    /** The bookmarks the list shows, in its order: what "Read all" opens. */
    val listed: List<BookmarkItem> get() = rows.flatMap { row ->
        when (row) {
            is BookmarksRow.Entry -> listOf(row.item)
            // A folded group still counts: its bookmarks are part of the list.
            is BookmarksRow.Section -> if (row.expanded) emptyList() else row.group.items.filter { filter == BookmarksFilter.ALL || !it.state.progress.done }
        }
    }
}

/**
 * The Bookmarks tab (§5.6): one list of groups and bookmarks without a group, in the chosen order.
 * It is an index (owner, 2026-11-26, notes item 47): reading, counting and playing happen in the
 * reader, which a bookmark, a group or "Read all" opens. A group folds its bookmarks; each bookmark
 * shows a glimpse of its text, loaded only for the cards on screen and only from the start and the
 * end of the passage, so a long or much-repeated bookmark costs no more than a short one (item 43).
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val index: Provider<QuranIndex>,
    private val availability: Availability,
    private val library: BookmarkLibrary,
    private val tracker: ReadingTracker,
    private val text: QuranTextSource,
    private val prefs: PrefsStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(BookmarksUiState())
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    private var board: BookmarkBoard? = null
    private var quran: QuranIndex? = null
    private var noticeCounter = 0

    /** Sections folded by the user; on open every group is open (owner's decision). */
    private val collapsedSections = HashSet<Long>()

    /** Under Unread: bookmarks already shown, kept when read until the screen is left. */
    private val keptUnread = HashSet<Long>()

    private val glimpses = HashMap<AyahRange, Glimpse>()
    private val loadingGlimpses = HashSet<AyahRange>()

    init {
        viewModelScope.launch {
            val index = withContext(io) { index.get() }
            quran = index
            availability.observe().collect { board ->
                this@BookmarksViewModel.board = board
                _state.update { board.toUi(index, it) }
                rebuild()
            }
        }
    }

    // ---- the list ------------------------------------------------------------------------------

    /** The list's order: Quran order, newest first, or as arranged (owner, 2026-11-25). */
    fun onSort(sort: io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort) {
        viewModelScope.launch { prefs.update { it.copy(bookmarkSort = sort) } }
    }

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

    /** What the filter lets through, in list order: each group with its bookmarks, or a bookmark alone (null). */
    private fun visibleSections(state: BookmarksUiState): List<Pair<GroupItem?, List<BookmarkItem>>> {
        fun pass(item: BookmarkItem): Boolean = when (state.filter) {
            BookmarksFilter.ALL -> true
            BookmarksFilter.UNREAD -> (!item.state.progress.done || item.id in keptUnread).also { if (it) keptUnread += item.id }
        }
        val sections = state.top.map { top ->
            when (top) {
                is TopItem.OfGroup -> top.group to top.group.items.filter(::pass)
                is TopItem.Loose -> null to listOf(top.item).filter(::pass)
            }
        }
        return when (state.filter) {
            // Every group is listed, even an empty one.
            BookmarksFilter.ALL -> sections.filter { (group, items) -> group != null || items.isNotEmpty() }
            BookmarksFilter.UNREAD -> sections.filter { it.second.isNotEmpty() }
        }
    }

    private fun rebuild() {
        val state = _state.value
        val rows = ArrayList<BookmarksRow>()
        for ((group, items) in visibleSections(state)) {
            if (group != null) {
                val open = group.id !in collapsedSections
                rows += BookmarksRow.Section(group, group.items.size, group.items.count { it.state.progress.done }, open)
                if (!open) continue
            }
            for (item in items) rows += BookmarksRow.Entry(item, inGroup = group != null)
        }
        _state.update { it.copy(rows = rows) }
    }

    /** "Read all": the reader on every bookmark the list shows, in its order; under Unread only the unread ones. */
    fun readAllRoute(): ReaderRoute =
        ReaderRoute.bookmarks(unreadOnly = _state.value.filter == BookmarksFilter.UNREAD)

    // ---- the text on the cards -------------------------------------------------------------------

    /**
     * A card came on screen: load its glimpse, once per passage. A passage of up to
     * [GLIMPSE_WHOLE_AYAHS] ayahs is read whole; of a longer one only the first [GLIMPSE_AYAHS] ayahs
     * and the last, whatever its length.
     */
    fun onGlimpseNeeded(bookmarkId: Long) {
        val range = itemOf(bookmarkId)?.state?.bookmark?.range ?: return
        if (range in glimpses || !loadingGlimpses.add(range)) return
        val index = quran ?: return
        viewModelScope.launch {
            val glimpse = runCatching {
                withContext(io) {
                    // A short passage is read whole and the card decides whether it fits; a long one by its start.
                    val headSize = if (range.size <= GLIMPSE_WHOLE_AYAHS) range.size else GLIMPSE_AYAHS
                    val head = AyahRange(range.start, AyahId(range.start.value + headSize - 1))
                    fun flowing(list: List<AyahText>) =
                        list.joinToString(" ") { AyahTextFormatter.format(it.text, index.ref(it.id).ayah, breakAtPauseMarks = false) }
                    Glimpse(
                        head = flowing(text.ayahs(head)),
                        tail = if (head.end == range.end) null else flowing(text.ayahs(AyahRange.single(range.end))),
                    )
                }
            }.getOrNull()
            loadingGlimpses.remove(range)
            if (glimpse != null) {
                glimpses[range] = glimpse
                _state.update { it.copy(glimpses = HashMap(glimpses)) }
            }
        }
    }

    // ---- bookmark actions ----------------------------------------------------------------------

    /** "Log a full reading": one more reading of every ayah of the passage, with one Undo. */
    fun onLogFullReading(bookmarkId: Long) = act {
        val bookmark = bookmarkOf(bookmarkId) ?: return@act
        val batch = tracker.markRange(bookmark.range, ReadContext.Bookmark(bookmark.id), ReadMethod.BULK, RangeMode.ADD)
        notify(UndoKind.LOGGED, batch = batch)
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

    companion object {
        /** A passage of at most this many ayahs is read whole for its card (al-Ikhlas, an-Nas…). */
        const val GLIMPSE_WHOLE_AYAHS = 8

        /** Of a longer passage, how many ayahs from its start are read; with the last, this + 1. */
        const val GLIMPSE_AYAHS = 3
    }
}

internal fun BookmarkBoard.toUi(index: QuranIndex, previous: BookmarksUiState): BookmarksUiState {
    val cache = HashMap<Long, BookmarkItem>()
    fun item(state: BookmarkState) = cache.getOrPut(state.bookmark.id) { BookmarkItem(state, BookmarkTitles.of(state.bookmark, index)) }
    fun items(states: List<BookmarkState>) = states.map(::item)
    val groupItems = groups.map { GroupItem(it, items(it.bookmarks)) }
    val byId = groupItems.associateBy { it.id }
    return previous.copy(
        loading = false,
        groups = groupItems,
        ungrouped = items(ungrouped),
        all = items(all),
        top = this.items.mapNotNull { top ->
            when (top) {
                is BoardItem.Group -> byId[top.state.group.id]?.let { TopItem.OfGroup(it) }
                is BoardItem.Loose -> TopItem.Loose(item(top.state))
            }
        },
        sort = sort,
    )
}
