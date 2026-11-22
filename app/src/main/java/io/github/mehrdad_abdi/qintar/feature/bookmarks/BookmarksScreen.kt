package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahCheckState
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.QProgressBar
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.ReadState
import io.github.mehrdad_abdi.qintar.ui.designsystem.ReadStateIndicator
import io.github.mehrdad_abdi.qintar.ui.designsystem.SetCountDialog
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.ayahCount
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.GroupEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute

/** Test tag of the list of the Bookmarks tab. */
const val BOOKMARKS_LIST_TAG = "bookmarks-list"

/** Test tag of the add button's menu. */
const val BOOKMARKS_ADD_TAG = "bookmarks-add"

/**
 * The Bookmarks tab (§5.6): All / Unread, then one list of groups and Ungrouped. A group folds its
 * bookmarks, a bookmark folds its ayahs, so a whole set is read by scrolling, without opening each.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun BookmarksScreen(onNavigate: (route: Any) -> Unit, viewModel: BookmarksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Bookmarks read under Unread stay until the screen is left.
    DisposableEffect(viewModel) { onDispose { viewModel.onScreenLeft() } }

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
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.let { (it.firstOrNull()?.index ?: 0) to (it.lastOrNull()?.index ?: 0) } }
            .collect { (first, last) -> viewModel.onVisibleRows(first, last) }
    }
    val scroll = state.scrollRequest
    LaunchedEffect(scroll) {
        if (scroll != null) {
            listState.animateScrollToItem(scroll.row)
            viewModel.onScrollHandled(scroll)
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
        FilterRow(state.filter, viewModel::onFilter)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!state.loading) {
                if (!state.hasBookmarks && state.groups.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bm_none_title),
                        message = stringResource(R.string.bm_none_message),
                        icon = Icons.Outlined.BookmarkBorder,
                        actionLabel = stringResource(R.string.bm_add_bookmark),
                        onAction = { onNavigate(BookmarkEditorRoute()) },
                        modifier = Modifier.padding(Spacing.screen),
                    )
                } else if (state.rows.isEmpty()) {
                    Text(
                        stringResource(R.string.bm_unread_empty),
                        style = QintarType.Body,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag(BOOKMARKS_LIST_TAG),
                        contentPadding = PaddingValues(bottom = Spacing.lg),
                    ) {
                        itemsIndexed(state.rows, key = { _, row -> row.key }) { _, row ->
                            when (row) {
                                is BookmarksRow.Section -> SectionRow(row, viewModel, onNavigate)
                                is BookmarksRow.Entry -> EntryRow(row, viewModel, onNavigate)
                                is BookmarksRow.Ayah -> InlineAyah(row, state, viewModel)
                                is BookmarksRow.End -> EndRow(row, viewModel)
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    BookmarksDialogs(state.dialog, viewModel)
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
private fun FilterRow(filter: BookmarksFilter, onFilter: (BookmarksFilter) -> Unit) {
    val labels = mapOf(
        BookmarksFilter.ALL to stringResource(R.string.bm_filter_all),
        BookmarksFilter.UNREAD to stringResource(R.string.bm_filter_unread),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BookmarksFilter.entries.forEach { entry ->
            FilterChip(
                selected = entry == filter,
                onClick = { onFilter(entry) },
                label = { Text(labels.getValue(entry), style = QintarType.Label) },
            )
        }
    }
}

private fun readState(done: Boolean, started: Boolean) = when {
    done -> ReadState.Done
    started -> ReadState.Partial
    else -> ReadState.NotRead
}

/** Two lines of a long description; a tap shows all of it, another folds it again. */
@Composable
private fun ExpandableDescription(text: String, color: Color, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Column(
        modifier
            .animateContentSize()
            .then(
                if (overflows || expanded) {
                    Modifier.clickable(role = Role.Button) { expanded = !expanded }.semantics { stateDescription = state }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text,
            style = QintarType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                stringResource(if (expanded) R.string.bm_description_less else R.string.bm_description_more),
                style = QintarType.Label,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---- Section (a group or Ungrouped) --------------------------------------------------------------

@Composable
private fun SectionRow(row: BookmarksRow.Section, vm: BookmarksViewModel, onNavigate: (Any) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val group = row.group
    val done = row.bookmarkCount > 0 && row.doneCount == row.bookmarkCount
    val name = group?.state?.group?.name ?: stringResource(R.string.bm_ungrouped)
    val expandedText = stringResource(if (row.expanded) R.string.state_expanded else R.string.state_collapsed)
    var menu by remember { mutableStateOf(false) }
    val ink = if (done) colors.onPrimaryContainer else colors.onSurface
    Column(Modifier.fillMaxWidth().background(if (done) colors.primaryContainer else colors.surfaceContainer)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { vm.onToggleSection(row.sectionKey) }
                .semantics { stateDescription = expandedText }
                .heightIn(min = Sizes.touchTarget)
                .padding(start = Spacing.screen, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (row.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = ink)
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(name, style = QintarType.BodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                if (row.bookmarkCount > 0) {
                    Text(
                        stringResource(R.string.bm_group_progress, formatNumber(row.doneCount), formatNumber(row.bookmarkCount)),
                        style = QintarType.Caption,
                        color = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    )
                }
            }
            if (done) ReadStateIndicator(ReadState.Done)
            if (row.bookmarkCount > 0) {
                IconButton(onClick = { vm.onPlaySection(row.sectionKey) }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.bm_play_group, name), tint = ink)
                }
            }
            if (group != null) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.bm_group_menu_more), tint = colors.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_edit)) }, onClick = { menu = false; onNavigate(GroupEditorRoute(group.id)) })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bm_add_bookmark)) },
                            onClick = { menu = false; onNavigate(BookmarkEditorRoute(draftGroupId = group.id)) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bm_group_menu_log)) },
                            enabled = row.bookmarkCount > 0,
                            onClick = { menu = false; vm.onLogGroupReading(group.id) },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.bm_group_menu_delete)) }, onClick = { menu = false; vm.onDeleteGroupRequested(group.id) })
                    }
                }
            }
        }
        group?.state?.group?.description?.let {
            ExpandableDescription(
                it,
                if (done) colors.onPrimaryContainer else colors.onSurfaceVariant,
                Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.sm),
            )
        }
        if (row.expanded && row.bookmarkCount == 0) {
            Text(
                stringResource(R.string.bm_group_empty),
                style = QintarType.Body,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.sm),
            )
        }
    }
}

// ---- Bookmark ------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(row: BookmarksRow.Entry, vm: BookmarksViewModel, onNavigate: (Any) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val item = row.item
    val progress = item.state.progress
    val bookmark = item.state.bookmark
    val state = readState(progress.done, progress.started)
    val done = state == ReadState.Done
    val ink = if (done) colors.onPrimaryContainer else colors.onSurface
    val quiet = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant
    val expandedText = stringResource(if (row.expanded) R.string.state_expanded else R.string.state_collapsed)
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(if (done) colors.primaryContainer else colors.background)) {
        HorizontalDivider(color = colors.outlineVariant, thickness = Sizes.hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(role = Role.Button, onLongClick = { menu = true }) { vm.onToggleBookmark(item.id) }
                .semantics { stateDescription = expandedText }
                .heightIn(min = Sizes.touchTarget)
                .padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (row.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = quiet)
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(bookmarkTitleText(item.title), style = QintarType.BodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = if (progress.started && !done) {
                    stringResource(R.string.progress_of, formatNumber(progress.readings), formatNumber(progress.required))
                } else {
                    ayahCount(bookmark.range.size)
                }
                Text(meta, style = QintarType.Caption, color = quiet)
            }
            if (done) ReadStateIndicator(ReadState.Done)
            IconButton(onClick = { vm.onPlayBookmark(item.id) }, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.bm_play_bookmark, bookmarkTitleText(item.title)), tint = ink)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.bm_menu_more), tint = quiet)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_open)) }, onClick = { menu = false; onNavigate(ReaderRoute.bookmark(item.id)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_edit)) }, onClick = { menu = false; onNavigate(BookmarkEditorRoute(bookmarkId = item.id)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_set_count)) }, onClick = { menu = false; vm.onSetCountRequested(item.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_move)) }, onClick = { menu = false; vm.onMoveRequested(item.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_delete)) }, onClick = { menu = false; vm.onDelete(item.id) })
                }
            }
        }
        bookmark.description?.let {
            ExpandableDescription(it, quiet, Modifier.fillMaxWidth().padding(start = Spacing.screen + Spacing.xl, end = Spacing.screen, bottom = Spacing.xs))
        }
        if (state == ReadState.Partial) {
            QProgressBar(progress.fraction, Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs), height = Spacing.xxs)
        } else {
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun InlineAyah(row: BookmarksRow.Ayah, state: BookmarksUiState, vm: BookmarksViewModel) {
    val stored = state.texts[row.id]
    val shown = remember(stored, row.number, state.breakAtPauseMarks) {
        stored?.let { AyahTextFormatter.format(it, row.number, state.breakAtPauseMarks) } ?: " "
    }
    val count = state.count(row)
    val reference = localizeDigits("${row.surah}:${row.number}")
    AyahRow(
        text = shown,
        reference = reference,
        checkState = if (count > 0) AyahCheckState.ReadToday else AyahCheckState.Unread,
        readCount = count,
        sajdaLabel = if (row.sajda != SajdaType.NONE) stringResource(R.string.sajda_label) else null,
        fontSizeSp = state.fontSp,
        onCheck = { vm.onCheck(row) },
        playing = state.audioPlaying && state.isAudioAt(row),
        highlighted = state.isAudioAt(row),
        onPlay = { vm.onPlayAyah(row) },
        onStar = null,
        onMenu = null,
    )
}

/** The foot of an expanded bookmark: "Mark read" for all its ayahs, or that it is read today. */
@Composable
private fun EndRow(row: BookmarksRow.End, vm: BookmarksViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.done) {
            Text(stringResource(R.string.bm_read_today), style = QintarType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(Spacing.sm))
            ReadStateIndicator(ReadState.Done)
        } else {
            FilledTonalButton(onClick = { vm.onMarkBookmarkRead(row.bookmarkId) }) {
                Text(stringResource(R.string.bm_mark_read), style = QintarType.Label)
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
                            Text(stringResource(R.string.bm_ungrouped), style = QintarType.Body, modifier = Modifier.weight(1f))
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
