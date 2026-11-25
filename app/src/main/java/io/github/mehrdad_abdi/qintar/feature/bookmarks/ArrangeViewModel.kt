package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.ArrangeKey
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BoardItem
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkBoard
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line of the Arrange screen: a bookmark (its [title]) or a group (its [groupName] and [count]). */
data class ArrangeLine(val key: ArrangeKey, val title: BookmarkTitle? = null, val groupName: String? = null, val count: Int = 0) {
    val isGroup: Boolean get() = key is ArrangeKey.Group
}

data class ArrangeUiState(
    val loading: Boolean = true,
    /** The group being arranged; null for the list's top level. */
    val groupName: String? = null,
    val lines: List<ArrangeLine> = emptyList(),
)

/**
 * The Arrange screen (owner, 2026-11-25): only titles, moved by dragging. Each finished drag is
 * saved and turns the list's order to "My order", keeping everything else where it was shown.
 */
@HiltViewModel(assistedFactory = ArrangeViewModel.Factory::class)
class ArrangeViewModel @AssistedInject constructor(
    @Assisted private val route: ArrangeRoute,
    private val index: Provider<QuranIndex>,
    private val availability: Availability,
    private val library: BookmarkLibrary,
    private val prefs: PrefsStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ArrangeRoute): ArrangeViewModel
    }

    private val _state = MutableStateFlow(ArrangeUiState())
    val state: StateFlow<ArrangeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val quran = withContext(io) { index.get() }
            val board = availability.board()
            fun line(item: BoardItem): ArrangeLine = when (item) {
                is BoardItem.Group -> ArrangeLine(ArrangeKey.Group(item.state.group.id), groupName = item.state.group.name, count = item.state.bookmarks.size)
                is BoardItem.Loose -> ArrangeLine(ArrangeKey.Bookmark(item.state.bookmark.id), title = BookmarkTitles.of(item.state.bookmark, quran))
            }
            if (route.groupId == ArrangeRoute.TOP) {
                _state.update { it.copy(loading = false, lines = board.items.map(::line)) }
            } else {
                val group = board.groups.firstOrNull { it.group.id == route.groupId }
                _state.update {
                    it.copy(
                        loading = false,
                        groupName = group?.group?.name,
                        lines = group?.bookmarks.orEmpty().map { b -> ArrangeLine(ArrangeKey.Bookmark(b.bookmark.id), title = BookmarkTitles.of(b.bookmark, quran)) },
                    )
                }
            }
        }
    }

    /** While dragging: the line at [from] now stands at [to]. */
    fun onMove(from: Int, to: Int) = _state.update {
        val lines = it.lines.toMutableList()
        if (from !in lines.indices || to !in lines.indices) return@update it
        lines.add(to, lines.removeAt(from))
        it.copy(lines = lines)
    }

    /** A drag ended: the new order is saved, and the list shows "My order". */
    fun onMoveDone() {
        val lines = _state.value.lines
        viewModelScope.launch {
            val board = availability.board()
            // Switching to My order keeps what was shown: the current order becomes the custom one first.
            if (board.sort != BookmarkSort.CUSTOM) keepAsShown(board)
            if (route.groupId == ArrangeRoute.TOP) library.arrange(lines.map { it.key }) else library.reorder(lines.map { it.key.id })
            prefs.update { it.copy(bookmarkSort = BookmarkSort.CUSTOM) }
        }
    }

    private suspend fun keepAsShown(board: BookmarkBoard) {
        library.arrange(
            board.items.map { item ->
                when (item) {
                    is BoardItem.Group -> ArrangeKey.Group(item.state.group.id)
                    is BoardItem.Loose -> ArrangeKey.Bookmark(item.state.bookmark.id)
                }
            },
        )
        for (group in board.groups) library.reorder(group.bookmarks.map { it.bookmark.id })
    }
}
