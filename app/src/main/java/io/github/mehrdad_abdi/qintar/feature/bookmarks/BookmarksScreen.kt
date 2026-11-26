package io.github.mehrdad_abdi.qintar.feature.bookmarks

import io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.Sort
import io.github.mehrdad_abdi.qintar.ui.common.MessageHost
import io.github.mehrdad_abdi.qintar.ui.common.showUndo
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import io.github.mehrdad_abdi.qintar.ui.designsystem.Radii
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.cardSegment
import io.github.mehrdad_abdi.qintar.ui.designsystem.CardSegment
import io.github.mehrdad_abdi.qintar.ui.designsystem.BasmalaRow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.DoneAll
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
const val BOOKMARKS_ORDER_TAG = "bookmarks-order"
const val BOOKMARKS_READ_ALL_TAG = "bookmarks-read-all"

/**
 * The Bookmarks tab (§5.6): All / Unread and Read all, then one list of groups and bookmarks. It is
 * an index (notes item 47): a bookmark, a group or Read all opens the reader, where reading,
 * counting and playing happen. A group folds its bookmarks.
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
        UndoKind.DELETED to stringResource(R.string.bm_snack_deleted),
    )
    val undoLabel = stringResource(R.string.action_undo)
    val notice = state.notice
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            val result = snackbar.showUndo(messages.getValue(notice.kind), undoLabel)
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
            ReadAllButton(state) { onNavigate(viewModel.readAllRoute()) }
            OrderMenu(state.sort, viewModel::onSort, onNavigate)
            AddMenu(onNavigate)
        }
        FilterRow(state, viewModel::onFilter)
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
                        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.xxl),
                    ) {
                        itemsIndexed(state.rows, key = { _, row -> row.key }) { index, row ->
                            // A group's bookmarks are indented under it, so the bookmarks without a group
                            // that follow are not taken for its own; one of those after a group gets air.
                            val previous = state.rows.getOrNull(index - 1)
                            val loose = row is BookmarksRow.Entry && !row.inGroup
                            val afterGroup = previous != null && (row is BookmarksRow.Section || (loose && (previous is BookmarksRow.Section || (previous as BookmarksRow.Entry).inGroup)))
                            Box(
                                Modifier
                                    .then(if (row is BookmarksRow.Entry && row.inGroup) Modifier.padding(start = Spacing.lg) else Modifier)
                                    .then(if (afterGroup) Modifier.padding(top = Spacing.md) else Modifier),
                            ) {
                                when (row) {
                                    is BookmarksRow.Section -> GroupCard(row, viewModel, onNavigate)
                                    is BookmarksRow.Entry -> BookmarkCard(row, state.glimpses[row.item.state.bookmark.range], viewModel, onNavigate)
                                }
                            }
                        }
                    }
                }
            }
            MessageHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    BookmarksDialogs(state.dialog, viewModel)
}

/** The list's order (Quran order, My order, Newest first) and Arrange (owner, 2026-11-25). */
@Composable
private fun OrderMenu(sort: BookmarkSort, onSort: (BookmarkSort) -> Unit, onNavigate: (Any) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Sizes.touchTarget).testTag(BOOKMARKS_ORDER_TAG)) {
            Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.bm_order), tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((choice, label) in listOf(
                BookmarkSort.MUSHAF to R.string.bm_order_quran,
                BookmarkSort.CUSTOM to R.string.bm_order_custom,
                BookmarkSort.RECENT to R.string.bm_order_recent,
            )) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { if (choice == sort) Icon(Icons.Rounded.Check, null) },
                    onClick = { open = false; onSort(choice) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bm_arrange)) },
                onClick = { open = false; onNavigate(ArrangeRoute()) },
            )
        }
    }
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
private fun FilterRow(state: BookmarksUiState, onFilter: (BookmarksFilter) -> Unit) {
    val unread = state.all.count { !it.state.progress.done }
    val labels = mapOf(
        BookmarksFilter.ALL to stringResource(R.string.bm_filter_count, stringResource(R.string.bm_filter_all), formatNumber(state.all.size)),
        BookmarksFilter.UNREAD to stringResource(R.string.bm_filter_count, stringResource(R.string.bm_filter_unread), formatNumber(unread)),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookmarksFilter.entries.forEach { entry ->
            FilterChip(
                selected = entry == state.filter,
                onClick = { onFilter(entry) },
                label = { Text(labels.getValue(entry), style = QintarType.Label) },
            )
        }
    }
}

/**
 * Every bookmark the list shows, in its order, as one reading (owner, 2026-11-26). In the top bar,
 * where it has room: beside the filter chips it broke into three lines on a phone (2026-11-27).
 */
@Composable
private fun ReadAllButton(state: BookmarksUiState, onReadAll: () -> Unit) {
    if (!state.hasBookmarks) return
    FilledTonalButton(
        onClick = onReadAll,
        enabled = state.listed.isNotEmpty(),
        contentPadding = PaddingValues(horizontal = Spacing.md),
        modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag(BOOKMARKS_READ_ALL_TAG),
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
        Spacer(Modifier.size(Spacing.sm))
        Text(stringResource(R.string.bm_read_all), style = QintarType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun readState(done: Boolean, started: Boolean) = when {
    done -> ReadState.Done
    started -> ReadState.Partial
    else -> ReadState.NotRead
}

/**
 * A description: two lines while its group is closed (and always on a bookmark's card), all of it
 * while the group is open. It has no tap of its own: a tap on the card opens the reader.
 */
@Composable
private fun FoldedDescription(text: String, color: Color, open: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        // The user's own words, in any language: their direction comes from the text itself.
        style = QintarType.Body.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
        color = color,
        maxLines = if (open) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.animateContentSize(),
    )
}

/** The fold arrow, turning with the state. */
@Composable
private fun FoldArrow(expanded: Boolean, tint: Color, description: String?) {
    val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "fold")
    Icon(Icons.Rounded.ExpandMore, contentDescription = description, tint = tint, modifier = Modifier.rotate(turn))
}

// ---- Cards --------------------------------------------------------------------------------------

/** The frame both cards share (notes item 42): a group and a bookmark look alike. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListCard(
    done: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .padding(top = Spacing.sm)
            .fillMaxWidth()
            .cardSegment(
                CardSegment.SINGLE,
                if (done) colors.primaryContainer else colors.surface,
                if (done) colors.primaryContainer else colors.outlineVariant,
            )
            .clip(QintarShapes.Card)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .padding(start = Spacing.card, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
        content = content,
    )
}

/**
 * A group's card, as a bookmark's: ring for "done of N", name, progress, menu, and the arrow that
 * shows or hides its bookmarks. A tap opens the reader on the whole group.
 */
@Composable
private fun GroupCard(row: BookmarksRow.Section, vm: BookmarksViewModel, onNavigate: (Any) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val group = row.group
    val done = row.bookmarkCount > 0 && row.doneCount == row.bookmarkCount
    val state = readState(done, row.doneCount > 0)
    val ink = if (done) colors.onPrimaryContainer else colors.onSurface
    val quiet = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant
    val name = group.state.group.name
    var menu by remember { mutableStateOf(false) }
    ListCard(
        done = done,
        onClick = { if (row.bookmarkCount > 0) onNavigate(ReaderRoute.group(group.id)) else vm.onToggleSection(row.sectionKey) },
        onLongClick = { menu = true },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ReadStateIndicator(state, progress = if (row.bookmarkCount > 0) row.doneCount.toFloat() / row.bookmarkCount else 0f)
            Column(Modifier.weight(1f)) {
                Text(name, style = QintarType.BodyStrong, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                val meta = if (row.bookmarkCount > 0) {
                    stringResource(R.string.bm_group_progress, formatNumber(row.doneCount), formatNumber(row.bookmarkCount))
                } else {
                    stringResource(R.string.bm_group_empty)
                }
                Text(meta, style = QintarType.Caption, color = quiet)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.bm_group_menu_more), tint = quiet)
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
            if (row.bookmarkCount > 0) {
                val fold = stringResource(if (row.expanded) R.string.bm_hide_bookmarks else R.string.bm_show_bookmarks)
                val expandedText = stringResource(if (row.expanded) R.string.state_expanded else R.string.state_collapsed)
                IconButton(
                    onClick = { vm.onToggleSection(row.sectionKey) },
                    modifier = Modifier.size(Sizes.touchTarget).semantics { stateDescription = expandedText },
                ) {
                    FoldArrow(row.expanded, quiet, fold)
                }
            }
        }
        group.state.group.description?.let {
            FoldedDescription(it, quiet, row.expanded, Modifier.fillMaxWidth().padding(top = Spacing.sm, end = Spacing.md))
        }
    }
}

/**
 * A bookmark's card: read-state ring, title, "7 ayahs" or today's progress, menu; its description,
 * then a glimpse of its text. A tap opens it in the reader. Read today, the card turns
 * primaryContainer, as everywhere a bookmark is read (R-BMK-8).
 */
@Composable
private fun BookmarkCard(row: BookmarksRow.Entry, glimpse: Glimpse?, vm: BookmarksViewModel, onNavigate: (Any) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val item = row.item
    val progress = item.state.progress
    val bookmark = item.state.bookmark
    val state = readState(progress.done, progress.started)
    val done = state == ReadState.Done
    val ink = if (done) colors.onPrimaryContainer else colors.onSurface
    val quiet = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant
    val title = bookmarkTitleText(item.title)
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(bookmark.range) { vm.onGlimpseNeeded(item.id) }
    ListCard(done = done, onClick = { onNavigate(ReaderRoute.bookmark(item.id)) }, onLongClick = { menu = true }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ReadStateIndicator(state, progress = progress.fraction)
            Column(Modifier.weight(1f)) {
                Text(title, style = QintarType.BodyStrong, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = when {
                    done -> stringResource(R.string.bm_read_today)
                    progress.started -> stringResource(R.string.progress_of, formatNumber(progress.readings), formatNumber(progress.required))
                    else -> ayahCount(bookmark.range.size)
                }
                Text(meta, style = QintarType.Caption, color = quiet)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.bm_menu_more), tint = quiet)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_edit)) }, onClick = { menu = false; onNavigate(BookmarkEditorRoute(bookmarkId = item.id)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_move)) }, onClick = { menu = false; vm.onMoveRequested(item.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bm_menu_delete)) }, onClick = { menu = false; vm.onDelete(item.id) })
                }
            }
        }
        bookmark.description?.let {
            FoldedDescription(it, quiet, open = false, Modifier.fillMaxWidth().padding(top = Spacing.sm, end = Spacing.md))
        }
        if (glimpse != null) PassageGlimpse(glimpse, ink, Modifier.padding(top = Spacing.sm, end = Spacing.md))
    }
}

// ---- Dialogs -------------------------------------------------------------------------------------

@Composable
private fun BookmarksDialogs(dialog: BookmarksDialog?, viewModel: BookmarksViewModel) {
    when (dialog) {
        null -> Unit
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
