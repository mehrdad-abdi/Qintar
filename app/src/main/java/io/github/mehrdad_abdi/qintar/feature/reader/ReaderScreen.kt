package io.github.mehrdad_abdi.qintar.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import io.github.mehrdad_abdi.qintar.ui.common.ayahName
import io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText
import io.github.mehrdad_abdi.qintar.ui.common.number
import io.github.mehrdad_abdi.qintar.ui.common.rankCaption
import io.github.mehrdad_abdi.qintar.ui.common.rankReachedText
import io.github.mehrdad_abdi.qintar.ui.common.surahFacts
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.AyahRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.BasmalaRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.PageDivider
import io.github.mehrdad_abdi.qintar.ui.designsystem.PositionBar
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.SetCountDialog
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.SurahHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.TodayPill
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute

/** Test tag of the reader's list. */
const val READER_LIST_TAG = "reader-list"

/**
 * The reader (§5.4): one vertical list for the whole scope, the today pill always in view.
 *
 * Other features open it by navigating to a [ReaderRoute]: `ReaderRoute.khatm(id)`,
 * `.bookmark(id)`, `.group(id)`, `.browse(ayahId)`, `.random(first, last)`.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun ReaderScreen(
    route: ReaderRoute,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigate: (route: Any) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    // Keep the screen on while the reader is in front, if the setting says so (R-RDR-6).
    val view = LocalView.current
    DisposableEffect(view, state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.reachedRank) {
        if (state.reachedRank != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val snackbar = remember { SnackbarHostState() }
    val bulk = state.bulk
    val bulkMessage = bulk?.let { stringResource(R.string.bulk_marked, formatNumber(it.added)) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(bulk?.id) {
        if (bulk != null && bulkMessage != null) {
            val result = snackbar.showSnackbar(bulkMessage, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoBulk() else viewModel.onBulkNoticeShown(bulk)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val model = state.model
        when {
            state.phase == ReaderPhase.NOT_FOUND -> Column(Modifier.fillMaxSize()) {
                TopBar(stringResource(R.string.screen_reader), onBack)
                EmptyState(
                    title = stringResource(R.string.reader_not_found_title),
                    message = stringResource(R.string.reader_not_found_message),
                    icon = Icons.Outlined.SearchOff,
                    modifier = Modifier.padding(Spacing.screen),
                )
            }
            model == null -> TopBar("", onBack)
            else -> {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.initialRowIndex)
                LaunchedEffect(listState, model) {
                    snapshotFlow {
                        val visible = listState.layoutInfo.visibleItemsInfo
                        (visible.firstOrNull()?.index ?: listState.firstVisibleItemIndex) to (visible.lastOrNull()?.index ?: listState.firstVisibleItemIndex)
                    }.collect { (first, last) -> viewModel.onVisibleRows(first, last) }
                }
                val current by remember(listState, model) {
                    derivedStateOf { model.ayahAtOrAfter(listState.firstVisibleItemIndex) }
                }
                Column(Modifier.fillMaxSize()) {
                    TopBar(readerTitle(state.title, current, viewModelIndex = model), onBack)
                    current?.let { at ->
                        PositionBar(
                            primary = stringResource(
                                R.string.reader_position,
                                formatNumber(at.page),
                                formatNumber(PAGE_COUNT),
                                formatNumber(at.page * 100 / PAGE_COUNT),
                            ),
                            secondary = stringResource(R.string.juz_n, formatNumber(at.juz)),
                            onClick = {},
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                        )
                    }
                    ReaderList(state, model, listState, viewModel, Modifier.weight(1f))
                }
            }
        }

        // Floating above the text; a fade keeps the last line from colliding with the pill.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to colors.background))
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SnackbarHost(snackbar)
            val reached = state.reachedRank
            TodayPill(
                rank = (reached ?: state.today.rank).number,
                count = state.today.count,
                caption = rankCaption(state.today),
                progress = state.today.ringFraction,
                rankReached = reached != null,
                reachedText = reached?.let { rankReachedText(it) },
            )
        }
    }

    ReaderDialogs(state, viewModel)
}

private const val PAGE_COUNT = 604

@Composable
private fun readerTitle(title: ReaderTitle, current: ReaderRow.Ayah?, viewModelIndex: ReaderRowModel): String = when (title) {
    is ReaderTitle.Name -> title.text
    is ReaderTitle.OfBookmark -> bookmarkTitleText(title.title)
    ReaderTitle.CurrentSurah -> current?.let { surahName(viewModelIndex.surahOf(it)) }.orEmpty()
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = colors.onSurface)
        }
        Text(
            title,
            style = QintarType.Title,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
        )
    }
}

@Composable
private fun ReaderList(
    state: ReaderUiState,
    model: ReaderRowModel,
    listState: LazyListState,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag(READER_LIST_TAG),
        // Room for the pill, so that the last ayah can be scrolled clear of it.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        items(model.rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                is ReaderRow.SegmentHeader -> SegmentHeaderRow(
                    state.segmentTitles.getOrNull(row.segment)?.let { bookmarkTitleText(it) }.orEmpty(),
                    first = row.segment == 0,
                )
                is ReaderRow.SurahHeader -> SurahHeader(surahName(row.surah), subtitle = surahFacts(row.surah))
                is ReaderRow.Basmala -> BasmalaRow(fontSizeSp = state.fontSp)
                is ReaderRow.PageDivider -> PageDivider(
                    text = if (row.hizb != null) {
                        stringResource(R.string.page_divider_hizb, formatNumber(row.page), formatNumber(row.juz), formatNumber(row.hizb))
                    } else {
                        stringResource(R.string.page_divider, formatNumber(row.page), formatNumber(row.juz))
                    },
                    juzLabel = if (row.startsJuz) stringResource(R.string.juz_n, formatNumber(row.juz)) else null,
                )
                is ReaderRow.Ayah -> AyahItem(
                    row = row,
                    stored = state.texts[row.id],
                    state = state,
                    surahLabel = surahName(model.surahOf(row)),
                    onCheck = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onCheck(row)
                    },
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun SegmentHeaderRow(title: String, first: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        if (!first) Hairline(Modifier.padding(top = Spacing.xl))
        Text(
            title,
            style = QintarType.Headline,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        )
    }
}

@Composable
private fun AyahItem(
    row: ReaderRow.Ayah,
    stored: String?,
    state: ReaderUiState,
    surahLabel: String,
    onCheck: () -> Unit,
    viewModel: ReaderViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // A row whose text has not arrived keeps its place and its controls.
    val shown = remember(stored, row.number, state.breakAtPauseMarks) {
        stored?.let { AyahTextFormatter.format(it, row.number, state.breakAtPauseMarks) } ?: " "
    }
    Column {
        AyahRow(
            text = shown,
            reference = localizeDigits("${row.surah}:${row.number}"),
            checkState = state.checkState(row),
            readCount = state.count(row),
            starred = row.id in state.starred,
            sajdaLabel = if (row.sajda != SajdaType.NONE) stringResource(R.string.sajda_label) else null,
            fontSizeSp = state.fontSp,
            accessibilityLabel = stringResource(R.string.ayah_label, surahLabel, formatNumber(row.number)),
            onCheck = onCheck,
            onPlay = null,
            onStar = { viewModel.onStar(row) },
            onMenu = { menuOpen = true },
        )
        Box(Modifier.align(Alignment.End)) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ayah_menu_set_count)) },
                    onClick = { menuOpen = false; viewModel.onSetCountRequested(row) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ayah_menu_mark_up_to_here)) },
                    onClick = { menuOpen = false; viewModel.onMarkUpToHere(row) },
                )
            }
        }
        Hairline(Modifier.padding(horizontal = Spacing.screen))
    }
}

@Composable
private fun ReaderDialogs(state: ReaderUiState, viewModel: ReaderViewModel) {
    val model = state.model ?: return
    when (val dialog = state.dialog) {
        null -> Unit
        is ReaderDialog.SetCount -> SetCountDialog(
            initialCount = dialog.current,
            onConfirm = viewModel::onSetCount,
            onDismiss = viewModel::onDialogDismissed,
            supportingText = ayahName(model.surahOf(dialog.row), dialog.row.number),
        )
        is ReaderDialog.KhatmSkip -> {
            val from = model.find(dialog.gap.start)?.let { model.rows[it] as? ReaderRow.Ayah } ?: dialog.row
            val n = formatNumber(dialog.gap.size)
            AlertDialog(
                onDismissRequest = viewModel::onDialogDismissed,
                text = { Text(stringResource(R.string.khatm_skip_message, n, ayahName(model.surahOf(from), from.number))) },
                confirmButton = {
                    Button(onClick = viewModel::onSkipMarkAll) { Text(stringResource(R.string.khatm_skip_mark_n, n)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = viewModel::onDialogDismissed) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(onClick = viewModel::onSkipOnlyThis) { Text(stringResource(R.string.khatm_skip_only_this)) }
                    }
                },
            )
        }
        is ReaderDialog.ConfirmUnstar -> AlertDialog(
            onDismissRequest = viewModel::onDialogDismissed,
            title = { Text(stringResource(R.string.unstar_confirm_title)) },
            text = { Text(stringResource(R.string.unstar_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onUnstarConfirmed) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDialogDismissed) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
