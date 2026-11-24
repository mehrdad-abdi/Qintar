package io.github.mehrdad_abdi.qintar.feature.reader

import io.github.mehrdad_abdi.qintar.ui.common.MessageHost
import io.github.mehrdad_abdi.qintar.ui.common.showUndo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Headphones
import io.github.mehrdad_abdi.qintar.feature.player.PlayerBarViewModel
import io.github.mehrdad_abdi.qintar.feature.player.ReaderPlayerBar
import io.github.mehrdad_abdi.qintar.playback.DownloadState
import io.github.mehrdad_abdi.qintar.playback.origin
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
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.StackedProgressRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.SurahHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.TodayPill
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
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
 * @param player the mini player's view model; null leaves the player out (previews, tests of the list).
 */
@Composable
fun ReaderScreen(
    route: ReaderRoute,
    onBack: () -> Unit,
    onNavigate: (route: Any) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory> { it.create(route) },
    player: (@Composable () -> PlayerBarViewModel)? = null,
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
            val result = snackbar.showUndo(bulkMessage, undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoBulk() else viewModel.onBulkNoticeShown(bulk)
        }
    }

    // The outcome of "Download for offline" is said once.
    val download = state.download
    val downloadMessage = when (download) {
        is DownloadState.Finished -> stringResource(R.string.audio_download_done)
        is DownloadState.Failed -> stringResource(R.string.audio_download_failed, formatNumber(download.done), formatNumber(download.total))
        is DownloadState.Blocked -> stringResource(
            if (download.reason == DownloadState.Blocked.Reason.WIFI_ONLY) R.string.audio_download_wifi_only else R.string.audio_download_offline,
        )
        else -> null
    }
    LaunchedEffect(downloadMessage) {
        if (downloadMessage != null) {
            // Acknowledged only afterwards: doing it first would change the key and cancel the message.
            try {
                snackbar.showSnackbar(downloadMessage)
            } finally {
                viewModel.onDownloadNoticeShown()
            }
        }
    }
    val playerViewModel = player?.invoke()
    val playerVisible = playerViewModel?.state?.collectAsStateWithLifecycle()?.value?.visible == true

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
                state.scrollRequest?.let { request ->
                    LaunchedEffect(request) {
                        listState.scrollToItem(request.row)
                        viewModel.onScrollHandled(request)
                    }
                }
                val current by remember(listState, model) {
                    derivedStateOf { model.ayahAtOrAfter(listState.firstVisibleItemIndex) }
                }
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        readerTitle(state.title, current, viewModelIndex = model),
                        onBack,
                        onTextSettings = { viewModel.onSheet(ReaderSheet.TEXT) },
                        audioMenu = { AudioMenu(state.canDownloadPassage, viewModel) },
                    )
                    current?.let { at ->
                        PositionBar(
                            primary = stringResource(
                                R.string.reader_position,
                                formatNumber(at.page),
                                formatNumber(PAGE_COUNT),
                                formatNumber(at.page * 100 / PAGE_COUNT),
                            ),
                            secondary = stringResource(R.string.juz_n, formatNumber(at.juz)),
                            onClick = { viewModel.onSheet(ReaderSheet.JUMP) },
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                        )
                    }
                    ReaderList(state, model, listState, viewModel, onNavigate, Modifier.weight(1f), bottomRoom = if (playerVisible) 224.dp else 112.dp)
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
            MessageHost(snackbar)
            if (download is DownloadState.Running) {
                DownloadProgress(download, onCancel = viewModel::onDownloadCancel)
            }
            if (playerViewModel != null) {
                ReaderPlayerBar(
                    playerViewModel,
                    // This reader's own audio: back to the ayah; another reader's: over to that reader.
                    onOpen = { target -> if (target.origin() == route.origin()) viewModel.onShowPlaying() else onNavigate(target) },
                )
            }
            state.firstUnread?.let { unread ->
                AssistChip(
                    onClick = { viewModel.onJumpToAyah(unread) },
                    label = { Text(stringResource(R.string.reader_first_unread_chip), style = QintarType.Label) },
                    leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, modifier = Modifier.size(Sizes.iconSmall)) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = Sizes.touchTarget),
                )
            }
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
    ReaderSheets(state, viewModel)
}

private const val PAGE_COUNT = 604

@Composable
private fun readerTitle(title: ReaderTitle, current: ReaderRow.Ayah?, viewModelIndex: ReaderRowModel): String = when (title) {
    is ReaderTitle.Name -> title.text
    is ReaderTitle.OfBookmark -> bookmarkTitleText(title.title)
    ReaderTitle.CurrentSurah -> current?.let { surahName(viewModelIndex.surahOf(it)) }.orEmpty()
    ReaderTitle.AllBookmarks -> stringResource(R.string.tab_bookmarks)
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onTextSettings: (() -> Unit)? = null,
    audioMenu: (@Composable () -> Unit)? = null,
) {
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
        audioMenu?.invoke()
        if (onTextSettings != null) {
            val label = stringResource(R.string.reader_text_settings)
            TextButton(
                onClick = onTextSettings,
                modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics { contentDescription = label },
            ) { Text("Aa", style = QintarType.Title, color = colors.onSurfaceVariant) }
        }
    }
}

/** The reader's audio menu (§5.4): play from the ayah on screen, and "download for offline". */
@Composable
private fun AudioMenu(canDownloadPassage: Boolean, viewModel: ReaderViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(Icons.Rounded.Headphones, stringResource(R.string.reader_audio_menu), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_audio_play_from_here)) },
                onClick = { open = false; viewModel.onPlayFromHere() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_audio_download_surah)) },
                onClick = { open = false; viewModel.onDownloadSurah() },
            )
            if (canDownloadPassage) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_audio_download_passage)) },
                    onClick = { open = false; viewModel.onDownloadPassage() },
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(download: DownloadState.Running, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(QintarShapes.Inner).background(colors.surfaceContainer).padding(start = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.audio_download_progress, formatNumber(download.done), formatNumber(download.total)),
            style = QintarType.Label,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun ReaderList(
    state: ReaderUiState,
    model: ReaderRowModel,
    listState: LazyListState,
    viewModel: ReaderViewModel,
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier,
    bottomRoom: Dp = 112.dp,
) {
    val haptics = LocalHapticFeedback.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag(READER_LIST_TAG),
        // Room for the pill (and the player), so that the last ayah can be scrolled clear of them.
        contentPadding = PaddingValues(bottom = bottomRoom),
    ) {
        itemsIndexed(model.rows, key = { _, row -> row.key }, contentType = { _, row -> row::class }) { rowIndex, row ->
            when (row) {
                is ReaderRow.SegmentHeader -> SegmentHeaderRow(
                    title = state.segmentTitles.getOrNull(row.segment)?.let { bookmarkTitleText(it) }.orEmpty(),
                    info = state.headers.getOrNull(row.segment),
                    first = row.segment == 0,
                    groupAction = state.entry == ReaderEntry.GROUP && row.segment == 0,
                    onLog = { viewModel.onLogFullReading(row.segment) },
                    onLogGroup = viewModel::onLogGroupReading,
                )
                is ReaderRow.SurahHeader -> SurahHeader(surahName(row.surah), subtitle = surahFacts(row.surah))
                is ReaderRow.Basmala -> BasmalaRow(fontSizeSp = state.fontSp, highlighted = state.isAudioAt(row))
                is ReaderRow.PageDivider -> PageDivider(
                    text = if (row.hizb != null) {
                        stringResource(R.string.page_divider_hizb, formatNumber(row.page), formatNumber(row.juz), formatNumber(row.hizb))
                    } else {
                        stringResource(R.string.page_divider, formatNumber(row.page), formatNumber(row.juz))
                    },
                    juzLabel = if (row.startsJuz) stringResource(R.string.juz_n, formatNumber(row.juz)) else null,
                    // The page above this divider: marked read in one tap while it has unread ayahs.
                    actionLabel = if (state.pageMarkOffered(rowIndex)) {
                        stringResource(R.string.reader_mark_page, formatNumber(row.page - 1))
                    } else {
                        null
                    },
                    onAction = { viewModel.onMarkPage(row.page - 1, row.segment) },
                )
                is ReaderRow.SegmentEnd -> EndAction(
                    label = stringResource(R.string.reader_mark_bookmark),
                    done = !state.segmentHasUnread(row.segment),
                    onClick = { viewModel.onMarkSegment(row.segment) },
                )
                is ReaderRow.SurahEnd -> EndAction(
                    label = stringResource(R.string.reader_mark_surah, surahName(model.surahInfo(row.surah))),
                    done = !state.surahHasUnread(rowIndex),
                    onClick = { viewModel.onMarkSurah(row.surah, row.segment) },
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
                    onStar = { onNavigate(viewModel.starTarget(row)) },
                    onTodayReadings = { onNavigate(viewModel.todayReadingsRoute()) },
                )
            }
        }
        // The last page of the scope has no divider below it: its "Mark page" stands at the end
        // (not in a bookmark, which ends with its own Mark read).
        val last = model.rows.lastOrNull { it is ReaderRow.Ayah } as? ReaderRow.Ayah
        if (last != null && last.context !is ReadContext.Bookmark && state.pageAboveHasUnread(model.rows.size)) {
            item(key = "footer") {
                PageDivider(
                    text = stringResource(R.string.page_divider, formatNumber(last.page), formatNumber(last.juz)),
                    actionLabel = stringResource(R.string.reader_mark_page, formatNumber(last.page)),
                    onAction = { viewModel.onMarkPage(last.page, last.segment) },
                )
            }
        }
    }
}

/**
 * The header of a bookmark in the reader (§5.4): its title, its description (prominent: it may hold a
 * narration), today's progress against the repeat target, and "Log a full reading".
 */
@Composable
private fun SegmentHeaderRow(
    title: String,
    info: SegmentHeaderInfo?,
    first: Boolean,
    groupAction: Boolean,
    onLog: () -> Unit,
    onLogGroup: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag(SEGMENT_HEADER_TAG)) {
        if (!first) Hairline(Modifier.padding(top = Spacing.xl))
        if (groupAction) {
            FilledTonalButton(
                onClick = onLogGroup,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm).heightIn(min = Sizes.touchTarget),
            ) { Text(stringResource(R.string.reader_log_group_reading), style = QintarType.Label) }
        }
        Text(
            title,
            style = QintarType.Headline,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        )
        if (info == null) return@Column
        info.description?.let { description ->
            Text(
                description,
                style = QintarType.Body,
                color = colors.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.screen)
                    .fillMaxWidth()
                    .clip(QintarShapes.Inner)
                    .background(colors.surfaceContainer)
                    .padding(Spacing.lg),
            )
            Spacer(Modifier.height(Spacing.md))
        }
        Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val progress = if (info.required == 0) 0f else info.readings.toFloat() / info.required
            val readings = stringResource(R.string.reader_header_readings, formatNumber(info.fullReadings), formatNumber(info.repeatTarget))
            StackedProgressRow(label = readings, valueText = stringResource(R.string.percent, formatNumber((progress * 100).toInt())), progress = progress)
            FilledTonalButton(
                onClick = onLog,
                modifier = Modifier.heightIn(min = Sizes.touchTarget),
            ) { Text(stringResource(R.string.reader_log_full_reading), style = QintarType.Label) }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

/** Test tag of a bookmark header row. */
const val SEGMENT_HEADER_TAG = "segment-header"

@Composable
private fun AyahItem(
    row: ReaderRow.Ayah,
    stored: String?,
    state: ReaderUiState,
    surahLabel: String,
    onCheck: () -> Unit,
    viewModel: ReaderViewModel,
    onStar: () -> Unit,
    onTodayReadings: () -> Unit,
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
            starred = row.id in state.bookmarkedBy,
            sajdaLabel = if (row.sajda != SajdaType.NONE) stringResource(R.string.sajda_label) else null,
            fontSizeSp = state.fontSp,
            accessibilityLabel = stringResource(R.string.ayah_label, surahLabel, formatNumber(row.number)),
            onCheck = onCheck,
            playing = state.isPlaying(row),
            highlighted = state.isAudioAt(row),
            onPlay = { viewModel.onPlay(row) },
            onStar = onStar,
            onMenu = { menuOpen = true },
        )
        Box(Modifier.align(Alignment.End)) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.todays_readings)) },
                    onClick = { menuOpen = false; onTodayReadings() },
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
    }
}

/** A one-tap "mark read" at the end of a surah or a bookmark; once all is read, a quiet check. */
@Composable
private fun EndAction(label: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            io.github.mehrdad_abdi.qintar.ui.designsystem.ReadStateIndicator(io.github.mehrdad_abdi.qintar.ui.designsystem.ReadState.Done)
        } else {
            androidx.compose.material3.FilledTonalButton(onClick = onClick) { Text(label, style = QintarType.Label) }
        }
    }
}
