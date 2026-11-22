package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.Batch
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
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
import io.github.mehrdad_abdi.qintar.feature.today.TodayViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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

/** The bookmarks of the Now view that share a header: a group, or Ungrouped when [group] is null. */
data class NowSectionItem(val group: GroupItem?, val items: List<BookmarkItem>)

/** A group a bookmark can be moved or copied to. */
data class GroupChoice(val id: Long, val name: String)

enum class BookmarksView { NOW, GROUPS, ALL }

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
    val view: BookmarksView = BookmarksView.NOW,
    val sort: BookmarkSort = BookmarkSort.MUSHAF,
    /** Available at this moment and not done, under their group. */
    val now: List<NowSectionItem> = emptyList(),
    val laterToday: List<BookmarkItem> = emptyList(),
    val earlierToday: List<BookmarkItem> = emptyList(),
    val doneToday: List<BookmarkItem> = emptyList(),
    /** Groups in their custom order. */
    val groups: List<GroupItem> = emptyList(),
    val ungrouped: List<BookmarkItem> = emptyList(),
    val all: List<BookmarkItem> = emptyList(),
    val notice: UndoNotice? = null,
    val dialog: BookmarksDialog? = null,
) {
    val hasBookmarks: Boolean get() = all.isNotEmpty()
}

/**
 * The Bookmarks tab (§5.6): the board of [Availability] in three views, and the actions of the ⋯
 * menus. Nothing here locks a bookmark: a time window only decides what is listed first.
 */
@HiltViewModel
class BookmarksViewModel(
    private val index: Provider<QuranIndex>,
    private val availability: Availability,
    private val library: BookmarkLibrary,
    private val tracker: ReadingTracker,
    private val prefs: PrefsStore,
    private val clock: DayClock,
    private val io: CoroutineDispatcher,
    /** Re-evaluates schedule windows; about once a minute is enough. */
    private val ticks: Flow<Unit>,
) : ViewModel() {

    @Inject
    constructor(
        index: Provider<QuranIndex>,
        availability: Availability,
        library: BookmarkLibrary,
        tracker: ReadingTracker,
        prefs: PrefsStore,
        clock: DayClock,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(index, availability, library, tracker, prefs, clock, io, TodayViewModel.minuteTicker())

    private val _state = MutableStateFlow(BookmarksUiState())
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    private var board: BookmarkBoard? = null
    private var noticeCounter = 0

    init {
        viewModelScope.launch {
            val index = withContext(io) { index.get() }
            availability.observe(ticks).collect { board ->
                this@BookmarksViewModel.board = board
                _state.update { board.toUi(index, it) }
            }
        }
    }

    fun onView(view: BookmarksView) = _state.update { it.copy(view = view) }

    /** The sort mode is a persisted preference; the board re-emits in the new order. */
    fun onSort(sort: BookmarkSort) {
        viewModelScope.launch { prefs.update { it.copy(bookmarkSort = sort) } }
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
    fun group(state: GroupState) = GroupItem(state, items(state.bookmarks))
    val groupItems = groups.map(::group)
    val byId = groupItems.associateBy { it.id }
    return previous.copy(
        loading = false,
        sort = sort,
        now = now.map { section ->
            NowSectionItem(section.group?.let { byId.getValue(it.group.id) }, items(section.items))
        },
        laterToday = items(laterToday),
        earlierToday = items(earlierToday),
        doneToday = items(doneToday),
        groups = groupItems,
        ungrouped = items(ungrouped),
        all = items(all),
    )
}
