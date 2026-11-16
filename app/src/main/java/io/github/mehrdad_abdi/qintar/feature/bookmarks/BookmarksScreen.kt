package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText
import io.github.mehrdad_abdi.qintar.ui.common.scheduleText
import io.github.mehrdad_abdi.qintar.ui.common.windowText
import io.github.mehrdad_abdi.qintar.ui.designsystem.BookmarkCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.CollapsibleRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.GroupHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.ReadState
import io.github.mehrdad_abdi.qintar.ui.designsystem.ScheduleChip
import io.github.mehrdad_abdi.qintar.ui.designsystem.SetCountDialog
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.GroupEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute

/** Test tag of the list of the Bookmarks tab. */
const val BOOKMARKS_LIST_TAG = "bookmarks-list"

/** Test tag of the add button's menu. */
const val BOOKMARKS_ADD_TAG = "bookmarks-add"

/**
 * The Bookmarks tab (§5.6): Now, Groups and All. A tap opens the reader; the ⋯ menu edits, logs,
 * moves, copies and deletes.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun BookmarksScreen(onNavigate: (route: Any) -> Unit, viewModel: BookmarksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val actions = remember(viewModel, onNavigate) { BookmarkActions(viewModel, onNavigate) }
    val snackbar = remember { SnackbarHostState() }

    val messages = mapOf(
        UndoKind.LOGGED to stringResource(R.string.bm_snack_logged),
        UndoKind.GROUP_LOGGED to stringResource(R.string.bm_snack_group_logged),
        UndoKind.COUNT_SET to stringResource(R.string.bm_snack_count_set),
        UndoKind.DELETED to stringResource(R.string.bm_snack_deleted),
    )
    val undoLabel = stringResource(R.string.action_undo)
    val notice = state.notice
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            val result = snackbar.showSnackbar(messages.getValue(notice.kind), actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndo() else viewModel.onNoticeShown(notice.id)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.tab_bookmarks), style = QintarType.Title, color = colors.onSurface, modifier = Modifier.weight(1f))
            AddMenu(onNavigate)
        }
        ViewSwitch(state.view, viewModel::onView)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!state.loading) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(BOOKMARKS_LIST_TAG),
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    when (state.view) {
                        BookmarksView.NOW -> nowView(state, actions, viewModel)
                        BookmarksView.GROUPS -> groupsView(state, actions)
                        BookmarksView.ALL -> allView(state, actions, viewModel)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    BookmarksDialogs(state.dialog, viewModel)
}

/** What the lists do with a bookmark or a group; one object so the rows stay short. */
internal class BookmarkActions(val viewModel: BookmarksViewModel, val navigate: (Any) -> Unit) {
    fun open(id: Long) = navigate(ReaderRoute.bookmark(id))
    fun edit(id: Long) = navigate(BookmarkEditorRoute(bookmarkId = id))
    fun readGroup(id: Long) = navigate(ReaderRoute.group(id))
    fun editGroup(id: Long) = navigate(GroupEditorRoute(id))
    fun addBookmark(groupId: Long? = null) = navigate(BookmarkEditorRoute(draftGroupId = groupId ?: BookmarkEditorRoute.NEW))
    fun addGroup() = navigate(GroupEditorRoute())
}

@Composable
private fun AddMenu(onNavigate: (Any) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Sizes.touchTarget).testTag(BOOKMARKS_ADD_TAG)) {
            Icon(Icons.Rounded.Add, stringResource(R.string.bm_add), tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bm_add_bookmark)) },
                onClick = { open = false; onNavigate(BookmarkEditorRoute()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bm_add_group)) },
                onClick = { open = false; onNavigate(GroupEditorRoute()) },
            )
        }
    }
}

@Composable
private fun ViewSwitch(view: BookmarksView, onView: (BookmarksView) -> Unit) {
    val labels = mapOf(
        BookmarksView.NOW to stringResource(R.string.bm_view_now),
        BookmarksView.GROUPS to stringResource(R.string.bm_view_groups),
        BookmarksView.ALL to stringResource(R.string.bm_view_all),
    )
    val description = stringResource(R.string.bm_views_label)
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm).semantics { contentDescription = description },
    ) {
        BookmarksView.entries.forEachIndexed { i, entry ->
            SegmentedButton(
                selected = entry == view,
                onClick = { onView(entry) },
                shape = SegmentedButtonDefaults.itemShape(i, BookmarksView.entries.size),
                label = { Text(labels.getValue(entry), style = QintarType.Label) },
            )
        }
    }
}

// ---- Now -----------------------------------------------------------------------------------------

private fun LazyListScope.nowView(state: BookmarksUiState, actions: BookmarkActions, viewModel: BookmarksViewModel) {
    if (!state.hasBookmarks) {
        item(key = "none") { NoBookmarks(actions) }
        return
    }
    if (state.now.isEmpty()) {
        item(key = "nothing-now") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    title = stringResource(R.string.bm_now_empty_title),
                    message = stringResource(R.string.bm_now_empty_message),
                    icon = Icons.Outlined.EventRepeat,
                    actionLabel = stringResource(R.string.bm_show_groups),
                    onAction = { viewModel.onView(BookmarksView.GROUPS) },
                )
                TextButton(onClick = { viewModel.onView(BookmarksView.ALL) }) { Text(stringResource(R.string.bm_show_all), style = QintarType.Label) }
            }
        }
    }
    state.now.forEachIndexed { sectionIndex, section ->
        item(key = "now-header-$sectionIndex-${section.group?.id}") {
            val group = section.group
            if (group != null) {
                GroupHeader(
                    name = group.state.group.name,
                    state = group.state.progress.readState(),
                    window = group.state.group.schedule?.window?.let { windowText(it) },
                    onRead = { actions.readGroup(group.id) },
                )
            } else if (state.now.size > 1) {
                Text(stringResource(R.string.bm_ungrouped), style = QintarType.BodyStrong, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        section.items.forEach { entry -> item(key = "now-${entry.id}") { BookmarkItemCard(entry, actions) } }
    }
    laterSection("later", R.string.bm_later_today, state.laterToday, actions)
    laterSection("earlier", R.string.bm_earlier_today, state.earlierToday, actions)
    laterSection("done", R.string.bm_done_today, state.doneToday, actions)
}

/** A collapsed section: the later, earlier and done bookmarks. */
private fun LazyListScope.laterSection(key: String, title: Int, items: List<BookmarkItem>, actions: BookmarkActions) {
    if (items.isEmpty()) return
    item(key = "$key-header") {
        var expanded by rememberSaveable(key) { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CollapsibleRow(stringResource(title), items.size, expanded, onToggle = { expanded = !expanded })
            if (expanded) items.forEach { BookmarkItemCard(it, actions) }
        }
    }
}

// ---- Groups --------------------------------------------------------------------------------------

private fun LazyListScope.groupsView(state: BookmarksUiState, actions: BookmarkActions) {
    if (state.groups.isEmpty() && state.ungrouped.isEmpty()) {
        item(key = "no-groups") {
            EmptyState(
                title = stringResource(R.string.bm_groups_empty_title),
                message = stringResource(R.string.bm_groups_empty_message),
                icon = Icons.Outlined.FolderOpen,
                actionLabel = stringResource(R.string.bm_add_group),
                onAction = actions::addGroup,
            )
        }
        return
    }
    state.groups.forEach { group -> item(key = "group-${group.id}") { GroupSection(group, actions) } }
    if (state.ungrouped.isNotEmpty()) {
        item(key = "ungrouped-header") {
            Text(stringResource(R.string.bm_ungrouped), style = QintarType.BodyStrong, color = MaterialTheme.colorScheme.onSurface)
        }
        state.ungrouped.forEach { entry -> item(key = "ungrouped-${entry.id}") { BookmarkItemCard(entry, actions) } }
    }
    item(key = "add-group") {
        TextButton(onClick = actions::addGroup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bm_add_group), style = QintarType.Label)
        }
    }
}

@Composable
private fun GroupSection(group: GroupItem, actions: BookmarkActions) {
    var menu by remember { mutableStateOf(false) }
    val progress = group.state.progress
    val schedule = group.state.group.schedule
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupHeader(
                name = group.state.group.name,
                state = progress.readState(),
                progressText = if (progress.bookmarkCount > 0) {
                    stringResource(R.string.bm_group_progress, formatNumber(progress.bookmarksDone), formatNumber(progress.bookmarkCount))
                } else {
                    null
                },
                onRead = if (progress.bookmarkCount > 0) ({ actions.readGroup(group.id) }) else null,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.bm_group_menu_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bm_menu_edit)) },
                        onClick = { menu = false; actions.editGroup(group.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bm_group_menu_log)) },
                        enabled = progress.bookmarkCount > 0,
                        onClick = { menu = false; actions.viewModel.onLogGroupReading(group.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bm_group_menu_delete)) },
                        onClick = { menu = false; actions.viewModel.onDeleteGroupRequested(group.id) },
                    )
                }
            }
        }
        if (schedule != null) {
            ScheduleChip(scheduleText(schedule), Modifier.padding(horizontal = Spacing.card))
        }
        group.state.group.description?.let {
            Text(it, style = QintarType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.padding(horizontal = Spacing.card))
        }
        if (group.items.isEmpty()) {
            Text(
                stringResource(R.string.bm_group_empty),
                style = QintarType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.card),
            )
        }
        group.items.forEach { BookmarkItemCard(it, actions) }
        TextButton(onClick = { actions.addBookmark(group.id) }) { Text(stringResource(R.string.bm_add_bookmark), style = QintarType.Label) }
    }
}

// ---- All -----------------------------------------------------------------------------------------

private fun LazyListScope.allView(state: BookmarksUiState, actions: BookmarkActions, viewModel: BookmarksViewModel) {
    if (!state.hasBookmarks) {
        item(key = "none") { NoBookmarks(actions) }
        return
    }
    item(key = "sort") {
        val labels = mapOf(
            BookmarkSort.MUSHAF to stringResource(R.string.bm_sort_mushaf),
            BookmarkSort.CUSTOM to stringResource(R.string.bm_sort_custom),
            BookmarkSort.RECENT to stringResource(R.string.bm_sort_recent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BookmarkSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { viewModel.onSort(sort) },
                    label = { Text(labels.getValue(sort), style = QintarType.Label) },
                )
            }
        }
    }
    state.all.forEach { entry -> item(key = "all-${entry.id}") { BookmarkItemCard(entry, actions) } }
}

@Composable
private fun NoBookmarks(actions: BookmarkActions) {
    EmptyState(
        title = stringResource(R.string.bm_none_title),
        message = stringResource(R.string.bm_none_message),
        icon = Icons.Outlined.BookmarkBorder,
        actionLabel = stringResource(R.string.bm_add_bookmark),
        onAction = { actions.addBookmark() },
    )
}

// ---- One bookmark --------------------------------------------------------------------------------

private fun io.github.mehrdad_abdi.qintar.domain.service.GroupProgress.readState() = when {
    done -> ReadState.Done
    started -> ReadState.Partial
    else -> ReadState.NotRead
}

/** A bookmark as a card with its ⋯ menu; a long press opens the menu too. */
@Composable
internal fun BookmarkItemCard(item: BookmarkItem, actions: BookmarkActions, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val progress = item.state.progress
    val bookmark = item.state.bookmark
    val state = when {
        progress.done -> ReadState.Done
        progress.started -> ReadState.Partial
        else -> ReadState.NotRead
    }
    val progressText = when {
        progress.repeatTarget > 1 -> stringResource(
            R.string.progress_of,
            formatNumber(minOf(progress.fullReadings, progress.repeatTarget)),
            formatNumber(progress.repeatTarget),
        )
        progress.started -> stringResource(R.string.progress_of, formatNumber(progress.readings), formatNumber(progress.required))
        else -> null
    }
    val ayahs = stringResource(R.string.ayahs_n, formatNumber(bookmark.range.size))
    val meta = if (progress.repeatTarget > 1) "$ayahs · ${stringResource(R.string.times_n, formatNumber(progress.repeatTarget))}" else ayahs
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        BookmarkCard(
            title = bookmarkTitleText(item.title),
            state = state,
            onClick = { actions.open(item.id) },
            modifier = Modifier.weight(1f),
            description = bookmark.description,
            schedule = item.state.schedule?.let { scheduleText(it) },
            meta = meta,
            progressText = progressText,
            progress = progress.fraction,
            onLongClick = { menu = true },
        )
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.bm_menu_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val vm = actions.viewModel
                DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_edit)) }, onClick = { menu = false; actions.edit(item.id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_log)) }, onClick = { menu = false; vm.onLogFullReading(item.id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_set_count)) }, onClick = { menu = false; vm.onSetCountRequested(item.id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_move)) }, onClick = { menu = false; vm.onMoveRequested(item.id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_duplicate)) }, onClick = { menu = false; vm.onDuplicateRequested(item.id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_delete)) }, onClick = { menu = false; vm.onDelete(item.id) })
            }
        }
    }
}

// ---- Dialogs -------------------------------------------------------------------------------------

@Composable
private fun BookmarksDialogs(dialog: BookmarksDialog?, viewModel: BookmarksViewModel) {
    when (dialog) {
        null -> Unit
        is BookmarksDialog.SetCount -> SetCountDialog(
            initialCount = dialog.current,
            onConfirm = viewModel::onSetCount,
            onDismiss = viewModel::onDialogDismissed,
            title = stringResource(R.string.set_count_title),
            supportingText = bookmarkTitleText(dialog.title) + " · " + stringResource(R.string.bm_set_count_support),
        )
        is BookmarksDialog.PickGroup -> AlertDialog(
            onDismissRequest = viewModel::onDialogDismissed,
            title = { Text(stringResource(if (dialog.duplicate) R.string.bm_duplicate_title else R.string.bm_move_title), style = QintarType.Title) },
            text = {
                Column {
                    dialog.choices.forEach { choice ->
                        TextButton(onClick = { viewModel.onGroupChosen(choice.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(choice.name, style = QintarType.Body, modifier = Modifier.weight(1f))
                        }
                    }
                    if (dialog.canUngroup) {
                        TextButton(onClick = { viewModel.onGroupChosen(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.bm_no_group), style = QintarType.Body, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::onDialogDismissed) { Text(stringResource(R.string.action_cancel)) } },
        )
        is BookmarksDialog.DeleteGroup -> AlertDialog(
            onDismissRequest = viewModel::onDialogDismissed,
            title = { Text(stringResource(R.string.bm_delete_group_title, dialog.name), style = QintarType.Title) },
            text = {
                Column {
                    Text(stringResource(R.string.bm_delete_group_message, formatNumber(dialog.bookmarkCount)), style = QintarType.Body)
                    Spacer(Modifier.height(Spacing.sm))
                    TextButton(onClick = { viewModel.onDeleteGroupConfirmed(deleteBookmarks = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bm_delete_group_keep), style = QintarType.Label, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { viewModel.onDeleteGroupConfirmed(deleteBookmarks = true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bm_delete_group_all), style = QintarType.Label, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::onDialogDismissed) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
