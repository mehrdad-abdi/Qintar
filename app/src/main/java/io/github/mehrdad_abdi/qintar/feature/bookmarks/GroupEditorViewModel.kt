package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure.InvalidInput.Field
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLine
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLines
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitles
import io.github.mehrdad_abdi.qintar.ui.common.ScheduleForm
import io.github.mehrdad_abdi.qintar.ui.common.weekOrder
import io.github.mehrdad_abdi.qintar.ui.navigation.GroupEditorRoute
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why a group field was refused. */
enum class GroupProblem(@StringRes val message: Int) {
    NAME_MISSING(R.string.bm_err_name),
    DESCRIPTION_TOO_LONG(R.string.bm_err_description),
    SCHEDULE_INVALID(R.string.bm_err_schedule),
}

/** One bookmark in the group's list. */
data class GroupMember(val id: Long, val title: BookmarkTitle)

data class GroupEditorUiState(
    val phase: EditorPhase = EditorPhase.LOADING,
    val isNew: Boolean = true,
    val name: String = "",
    val description: String = "",
    val schedule: ScheduleForm = ScheduleForm(),
    val weekDays: List<DayOfWeek> = weekOrder(DayOfWeek.MONDAY),
    /** In the order they will have once saved; removed ones are gone from this list. */
    val members: List<GroupMember> = emptyList(),
    /** Bookmarks without a group, in list order: tapping one adds it (a bookmark is in one group at most). */
    val candidates: List<GroupMember> = emptyList(),
    /** New bookmarks, one per line in the forms of "Go to" (2:255, 2:255-257, s36, p300), created on Save. */
    val bulkText: String = "",
    /** Line numbers of [bulkText] that are not a bookmark; Save waits until they are fixed. */
    val bulkProblems: List<Int> = emptyList(),
    val problems: Map<String, GroupProblem> = emptyMap(),
    val savedId: Long? = null,
) {
    fun problem(field: String): GroupProblem? = problems[field]

    companion object {
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val SCHEDULE = "schedule"
    }
}

/**
 * Add or edit a group (§5.6): name, description, schedule, and — when editing — its bookmarks, which
 * can be reordered and taken out of the group. Member changes are applied on Save.
 */
@HiltViewModel(assistedFactory = GroupEditorViewModel.Factory::class)
class GroupEditorViewModel(
    private val route: GroupEditorRoute,
    private val index: Provider<QuranIndex>,
    private val library: BookmarkLibrary,
    private val availability: Availability,
    private val prefs: PrefsStore,
    private val io: CoroutineDispatcher,
    private val systemFirstDay: () -> DayOfWeek,
) : ViewModel() {

    @AssistedInject
    constructor(
        @Assisted route: GroupEditorRoute,
        index: Provider<QuranIndex>,
        library: BookmarkLibrary,
        availability: Availability,
        prefs: PrefsStore,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(route, index, library, availability, prefs, io, { WeekFields.of(Locale.getDefault()).firstDayOfWeek })

    @AssistedFactory
    interface Factory {
        fun create(route: GroupEditorRoute): GroupEditorViewModel
    }

    private val _state = MutableStateFlow(GroupEditorUiState(isNew = route.groupId == GroupEditorRoute.NEW))
    val state: StateFlow<GroupEditorUiState> = _state.asStateFlow()

    private var quranIndex: QuranIndex? = null

    /** The group's bookmarks as they were when the editor opened. */
    private var original: List<Long> = emptyList()

    /** Where each bookmark stands in "Add bookmarks", so one taken out goes back to its place. */
    private var offerOrder: Map<Long, Int> = emptyMap()

    init {
        viewModelScope.launch {
            val quran = withContext(io) { index.get() }
            val userPrefs = prefs.prefs.first()
            val weekDays = weekOrder(userPrefs.weekStart.firstDay(systemFirstDay()))
            val board = availability.board()
            quranIndex = quran
            fun candidatesBesides(members: Set<Long>) =
                board.ungrouped.filter { it.bookmark.id !in members }.map { GroupMember(it.bookmark.id, BookmarkTitles.of(it.bookmark, quran)) }
            if (route.groupId == GroupEditorRoute.NEW) {
                val candidates = candidatesBesides(emptySet())
                offerOrder = candidates.withIndex().associate { (i, c) -> c.id to i }
                _state.update { it.copy(phase = EditorPhase.READY, weekDays = weekDays, candidates = candidates) }
                return@launch
            }
            val group = board.groups.firstOrNull { it.group.id == route.groupId }
            if (group == null) {
                _state.update { it.copy(phase = EditorPhase.NOT_FOUND) }
                return@launch
            }
            // The group's own order: the custom order, whatever sort mode the lists are in.
            val members = Availability.sorted(group.bookmarks.map { it.bookmark }, BookmarkSort.CUSTOM)
            original = members.map { it.id }
            // The group's own bookmarks, if taken out, come after the ungrouped ones, in the group's order.
            offerOrder = (board.ungrouped.map { it.bookmark.id } + original).withIndex().associate { (i, id) -> id to i }
            _state.update {
                it.copy(
                    phase = EditorPhase.READY,
                    isNew = false,
                    name = group.group.name,
                    description = group.group.description.orEmpty(),
                    schedule = ScheduleForm.of(group.group.schedule),
                    weekDays = weekDays,
                    members = members.map { m -> GroupMember(m.id, BookmarkTitles.of(m, quran)) },
                    candidates = candidatesBesides(members.mapTo(HashSet()) { m -> m.id }),
                )
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, problems = it.problems - GroupEditorUiState.NAME) }

    fun onDescription(value: String) = _state.update {
        val tooLong = value.trim().length > Bookmark.MAX_DESCRIPTION
        it.copy(
            description = value,
            problems = if (tooLong) it.problems + (GroupEditorUiState.DESCRIPTION to GroupProblem.DESCRIPTION_TOO_LONG) else it.problems - GroupEditorUiState.DESCRIPTION,
        )
    }

    fun onSchedule(form: ScheduleForm) = _state.update {
        it.copy(
            schedule = form,
            problems = if (form.problem != null) it.problems + (GroupEditorUiState.SCHEDULE to GroupProblem.SCHEDULE_INVALID) else it.problems - GroupEditorUiState.SCHEDULE,
        )
    }

    fun onMoveUp(position: Int) = swap(position, position - 1)

    fun onMoveDown(position: Int) = swap(position, position + 1)

    private fun swap(from: Int, to: Int) = _state.update {
        if (from !in it.members.indices || to !in it.members.indices) return@update it
        val list = it.members.toMutableList()
        list[from] = list[to].also { list[to] = list[from] }
        it.copy(members = list)
    }

    /** Takes the bookmark out of the group on Save; the bookmark itself is kept, ungrouped. */
    fun onRemove(bookmarkId: Long) = _state.update {
        val member = it.members.firstOrNull { m -> m.id == bookmarkId } ?: return@update it
        it.copy(members = it.members - member, candidates = (it.candidates + member).sortedBy { c -> offerOrder[c.id] ?: Int.MAX_VALUE })
    }

    fun onBulkText(value: String) = _state.update { it.copy(bulkText = value, bulkProblems = emptyList()) }

    /** Adds an ungrouped bookmark at the end of the group, on Save. */
    fun onAdd(bookmarkId: Long) = _state.update {
        val candidate = it.candidates.firstOrNull { c -> c.id == bookmarkId } ?: return@update it
        it.copy(members = it.members + candidate, candidates = it.candidates - candidate)
    }

    fun onSave() {
        var current = _state.value
        if (current.phase != EditorPhase.READY) return
        val problems = HashMap(current.problems)
        if (current.name.isBlank()) problems[GroupEditorUiState.NAME] = GroupProblem.NAME_MISSING
        if (current.schedule.problem != null) problems[GroupEditorUiState.SCHEDULE] = GroupProblem.SCHEDULE_INVALID
        val lines = quranIndex?.let { BookmarkLines.parse(current.bulkText, it) }.orEmpty()
        val bulkProblems = lines.filterIsInstance<BookmarkLine.Problem>().map { it.line }
        if (problems.isNotEmpty() || bulkProblems.isNotEmpty()) {
            _state.update { it.copy(problems = problems, bulkProblems = bulkProblems) }
            return
        }
        val drafts = lines.filterIsInstance<BookmarkLine.Ok>().map { it.draft }
        // "Once" belongs to a bookmark: the group's schedule never carries it.
        val schedule = current.schedule.copy(once = false).toSchedule()
        viewModelScope.launch {
            try {
                val id = if (current.isNew) {
                    library.createGroup(current.name, current.description, schedule).id.also { applyMembers(it, current) }
                } else {
                    library.updateGroup(route.groupId, current.name, current.description, schedule).id.also { applyMembers(it, current) }
                }
                // The new ones from the bulk box, after the chosen ones, in the order typed.
                drafts.forEach { library.create(it.copy(groupId = id)) }
                _state.update { it.copy(savedId = id) }
            } catch (failure: DomainFailure.InvalidInput) {
                val problem = when (failure.field) {
                    Field.NAME -> GroupEditorUiState.NAME to GroupProblem.NAME_MISSING
                    Field.DESCRIPTION -> GroupEditorUiState.DESCRIPTION to GroupProblem.DESCRIPTION_TOO_LONG
                    else -> GroupEditorUiState.SCHEDULE to GroupProblem.SCHEDULE_INVALID
                }
                _state.update { it.copy(problems = it.problems + problem) }
            } catch (_: DomainFailure.GroupNotFound) {
                _state.update { it.copy(phase = EditorPhase.NOT_FOUND) }
            }
        }
    }

    /** Removes the dropped bookmarks, moves the added ones in, and gives them the chosen order. */
    private suspend fun applyMembers(groupId: Long, current: GroupEditorUiState) {
        val kept = current.members.map { it.id }
        original.filter { it !in kept }.forEach { library.moveToGroup(it, null) }
        kept.filter { it !in original }.forEach { library.moveToGroup(it, groupId) }
        if (kept != original.filter { it in kept }) {
            // Keep the members in the slots they already occupy in the custom order, so that the
            // bookmarks outside the group do not move.
            val everyone = Availability.sorted(availability.board().all.map { it.bookmark }, BookmarkSort.CUSTOM).map { it.id }
            val queue = kept.iterator()
            library.reorder(everyone.map { id -> if (id in kept) queue.next() else id })
        }
    }
}
