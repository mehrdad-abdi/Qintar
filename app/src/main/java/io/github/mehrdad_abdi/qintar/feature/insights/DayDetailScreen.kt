package io.github.mehrdad_abdi.qintar.feature.insights

import io.github.mehrdad_abdi.qintar.ui.common.MessageHost
import io.github.mehrdad_abdi.qintar.ui.common.showUndo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Tally
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.ayahName
import io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText
import io.github.mehrdad_abdi.qintar.ui.common.dateText
import io.github.mehrdad_abdi.qintar.ui.common.number
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.CountField
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.RankChip
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.ayahCount
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute
import java.time.format.DateTimeFormatter

/** Test tag of the screen's scrolling content. */
const val DAY_DETAIL_TAG = "day-detail-content"

/** Test tags of a run of ayahs and of the delete button of one ayah in an opened run. */
const val DAY_RUN_TAG = "day-run"
const val DAY_AYAH_DELETE_TAG = "day-ayah-delete"

/** Test tag of the "Add reading" button. */
const val DAY_ADD_READING_TAG = "day-add-reading"

/** What a dialog on this screen is about. */
private sealed interface DayDialog {
    data class EditTally(val tally: Tally) : DayDialog
}

/**
 * One day (R-INS-2, R-TRK-6): total and rank, entries grouped by context with their runs of ayahs, and
 * the tallies. A past day or today can be added to, changed and deleted from, with Undo; a day in the
 * future is only shown.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun DayDetailScreen(
    route: DayDetailRoute,
    onBack: () -> Unit,
    onNavigate: (route: Any) -> Unit,
    viewModel: DayDetailViewModel = hiltViewModel<DayDetailViewModel, DayDetailViewModel.Factory> { it.create(route) },
    logViewModel: LogReadingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DayDialog?>(null) }
    var logging by rememberSaveable { mutableStateOf(false) }
    var logged by remember { mutableStateOf<LogResult?>(null) }

    val undoLabel = stringResource(R.string.action_undo)
    val messages = mapOf(
        UndoKind.DELETED to stringResource(R.string.day_snack_deleted),
        UndoKind.TALLY_CHANGED to stringResource(R.string.day_snack_tally_changed),
        UndoKind.TALLY_DELETED to stringResource(R.string.day_snack_tally_deleted),
    )
    val notice = state.notice
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            val result = snackbar.showUndo(messages.getValue(notice.kind), undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndo() else viewModel.onNoticeShown(notice.id)
        }
    }
    val loggedMessage = logged?.let { stringResource(R.string.log_snack_added_day, ayahCount(it.count), dateText(it.day)) }
    LaunchedEffect(logged) {
        val result = logged ?: return@LaunchedEffect
        val outcome = snackbar.showUndo(loggedMessage.orEmpty(), undoLabel)
        if (outcome == SnackbarResult.ActionPerformed) logViewModel.onUndo(result)
        logged = null
    }
    val message = state.message
    val messageText = message?.let { stringResource(it.text) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(messageText.orEmpty())
            viewModel.onMessageShown()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            val day = state.day
            ScreenTopBar(
                title = day?.let { dayTitle(it) } ?: stringResource(R.string.screen_day_detail),
                onBack = onBack,
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = { logging = true }, modifier = Modifier.size(Sizes.touchTarget).testTag(DAY_ADD_READING_TAG)) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.day_add_reading), tint = colors.onSurface)
                        }
                    }
                },
            )
            when (state.phase) {
                DayPhase.LOADING -> Unit
                DayPhase.INVALID -> EmptyState(
                    title = stringResource(R.string.day_invalid_title),
                    icon = Icons.Outlined.EventBusy,
                    modifier = Modifier.padding(Spacing.screen),
                )
                DayPhase.READY -> DayContent(state, viewModel, onAdd = { logging = true }, onDialog = { dialog = it })
            }
        }
        MessageHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (val open = dialog) {
        is DayDialog.EditTally -> TallyDialog(
            tally = open.tally,
            onConfirm = { count, note ->
                viewModel.onUpdateTally(open.tally.id, count, note)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    if (logging) {
        LogReadingSheet(
            viewModel = logViewModel,
            day = state.day,
            onDismiss = { logging = false },
            onLogged = { logged = it },
        )
    }
}

@Composable
private fun dayTitle(day: java.time.LocalDate): String = io.github.mehrdad_abdi.qintar.ui.common.calendarDate(day, weekday = true)

@Composable
private fun DayContent(
    state: DayDetailUiState,
    viewModel: DayDetailViewModel,
    onAdd: () -> Unit,
    onDialog: (DayDialog) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            .testTag(DAY_DETAIL_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
    ) {
        SummaryCard(state)
        when {
            state.isFuture -> EmptyState(
                title = stringResource(R.string.day_future_title),
                message = stringResource(R.string.day_future_message),
                icon = Icons.Outlined.EventBusy,
            )
            state.isEmpty -> EmptyState(
                title = stringResource(R.string.day_empty_title),
                message = stringResource(R.string.day_empty_message),
                icon = Icons.Outlined.MenuBook,
                actionLabel = stringResource(R.string.day_add_reading),
                onAction = onAdd,
            )
        }
        state.groups.forEach { group -> GroupCard(group, state.canEdit, viewModel, onDialog) }
        if (state.tallies.isNotEmpty()) TalliesCard(state.tallies, state.canEdit, viewModel, onDialog)
        Spacer(Modifier.size(Spacing.xl))
    }
}

@Composable
private fun SummaryCard(state: DayDetailUiState) {
    val colors = MaterialTheme.colorScheme
    QCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatNumber(state.total),
                    style = QintarType.Display.copy(fontFeatureSettings = "tnum"),
                    color = colors.onSurface,
                    modifier = Modifier.testTag(DAY_TOTAL_TAG),
                )
                Text(stringResource(R.string.day_total_label), style = QintarType.Label, color = colors.onSurfaceVariant)
            }
            RankChip(state.rank.number)
        }
        if (state.isFuture) return@QCard
        Spacer(Modifier.size(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Outlined.LocalFireDepartment, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
            val streak = state.streak
            Text(
                when {
                    streak.current == 0 -> stringResource(R.string.today_streak_none)
                    streak.atRisk -> stringResource(R.string.today_streak_at_risk, formatNumber(streak.current))
                    else -> stringResource(R.string.today_streak_days, formatNumber(streak.current))
                },
                style = QintarType.Label,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            if (state.qualifiesForStreak) stringResource(R.string.day_counts_for_streak) else stringResource(R.string.day_not_for_streak),
            style = QintarType.Caption,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Test tag of the day's total. */
const val DAY_TOTAL_TAG = "day-total"

@Composable
private fun entryLabelText(label: EntryLabel): String = when (label) {
    is EntryLabel.Khatm -> label.name ?: stringResource(R.string.day_deleted_khatm)
    is EntryLabel.Bookmark -> label.title?.let { bookmarkTitleText(it) } ?: stringResource(R.string.day_deleted_bookmark)
    EntryLabel.Browse -> stringResource(R.string.source_browse)
    EntryLabel.Random -> stringResource(R.string.source_random)
    EntryLabel.Manual -> stringResource(R.string.source_manual)
}

@Composable
private fun methodText(method: ReadMethod): String = stringResource(
    when (method) {
        ReadMethod.TAP -> R.string.method_tapped
        ReadMethod.BULK -> R.string.method_bulk
        ReadMethod.AUDIO -> R.string.method_listened
        ReadMethod.MANUAL -> R.string.method_manual
        ReadMethod.IMPORT -> R.string.method_imported
    },
)

/** "Al-Baqarah 1–7", "Al-Fatiha 7 – Al-Baqarah 5", "Ayat 255". */
@Composable
internal fun runText(run: EntryRun): String = when {
    run.range.size == 1 -> ayahName(run.firstSurah, run.firstAyah)
    run.firstSurah.number == run.lastSurah.number ->
        stringResource(R.string.bookmark_title_range, surahName(run.firstSurah), formatNumber(run.firstAyah), formatNumber(run.lastAyah))
    else -> stringResource(R.string.bookmark_title_across, ayahName(run.firstSurah, run.firstAyah), ayahName(run.lastSurah, run.lastAyah))
}

@Composable
private fun GroupCard(group: EntryGroup, canEdit: Boolean, viewModel: DayDetailViewModel, onDialog: (DayDialog) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = entryLabelText(group.label)
    val methods = group.methods.map { methodText(it) }.joinToString(" · ")
    QCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = QintarType.BodyStrong, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ayahCount(group.total)} · $methods",
                    style = QintarType.Caption,
                    color = colors.onSurfaceVariant,
                )
            }
            if (canEdit) {
                IconButton(onClick = { viewModel.onDeleteGroup(group.context) }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.day_delete_group, name), tint = colors.onSurfaceVariant)
                }
            }
        }
        group.runs.forEach { run ->
            val text = runText(run)
            val times = if (run.readings > 1) stringResource(R.string.day_run_times, formatNumber(run.readings)) else ""
            // A run of several ayahs opens to its single ayahs, each of which can be deleted on its own.
            var open by androidx.compose.runtime.saveable.rememberSaveable(run.range.start.value, run.range.end.value) { androidx.compose.runtime.mutableStateOf(false) }
            val openable = canEdit && run.range.size > 1
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .then(
                        if (openable) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.day_show_ayahs)) { open = !open } else Modifier,
                    )
                    .testTag(DAY_RUN_TAG),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("$text $times".trim(), style = QintarType.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
                Text(formatNumber(run.total), style = QintarType.Numeric, color = colors.onSurfaceVariant)
                if (canEdit) {
                    IconButton(onClick = { viewModel.onDeleteRun(group.context, run.range) }, modifier = Modifier.size(Sizes.touchTarget)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.day_delete_entry, text), tint = colors.onSurfaceVariant)
                    }
                }
            }
            if (open) {
                run.ayahs.forEach { ayah ->
                    val name = ayahName(ayah.surah, ayah.number)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(start = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("$name $times".trim(), style = QintarType.Body, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { viewModel.onDeleteRun(group.context, AyahRange.single(ayah.id)) },
                            modifier = Modifier.size(Sizes.touchTarget).testTag(DAY_AYAH_DELETE_TAG),
                        ) { Icon(Icons.Rounded.Close, stringResource(R.string.day_delete_entry, name), tint = colors.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TalliesCard(tallies: List<Tally>, canEdit: Boolean, viewModel: DayDetailViewModel, onDialog: (DayDialog) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionHeader(stringResource(R.string.day_tallies_title))
    QCard(Modifier.fillMaxWidth()) {
        tallies.forEach { tally ->
            val label = tally.note ?: stringResource(R.string.day_tally_no_note)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .then(
                        if (canEdit) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.day_edit_tally)) {
                            onDialog(DayDialog.EditTally(tally))
                        } else Modifier,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    label,
                    style = QintarType.Body,
                    color = if (tally.note == null) colors.onSurfaceVariant else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(formatNumber(tally.count), style = QintarType.Numeric, color = colors.onSurfaceVariant)
                if (canEdit) {
                    IconButton(onClick = { viewModel.onDeleteTally(tally.id) }, modifier = Modifier.size(Sizes.touchTarget)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.day_delete_tally, label), tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TallyDialog(tally: Tally, onConfirm: (Int, String?) -> Unit, onDismiss: () -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(tally.count) }
    var note by rememberSaveable { mutableStateOf(tally.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.day_edit_tally), style = QintarType.Title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CountField(count, { count = it }, min = 1, max = LogReadingViewModel.MAX_NUMBER)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.log_note)) },
                    singleLine = true,
                    shape = QintarShapes.Inner,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(count, note) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        shape = QintarShapes.Card,
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
