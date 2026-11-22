package io.github.mehrdad_abdi.qintar.feature.khatm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.service.KhatmDay
import io.github.mehrdad_abdi.qintar.domain.service.KhatmSnapshot
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.dateText
import io.github.mehrdad_abdi.qintar.ui.common.pagesText
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.StackedProgressRow
import io.github.mehrdad_abdi.qintar.ui.designsystem.StatTile
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.NewKhatmRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute

/** Test tag of the detail's scrolling column. */
const val KHATM_DETAIL_TAG = "khatm-detail"

/**
 * One khatm in detail (§5.5): progress bars, the 30-juz strip, recent days, pace; Continue and First
 * unread; Rename, Change daily goal and Delete. A completed khatm shows its summary instead.
 */
@Composable
fun KhatmDetailScreen(
    route: KhatmDetailRoute,
    onBack: () -> Unit,
    onNavigate: (route: Any) -> Unit,
    viewModel: KhatmDetailViewModel = hiltViewModel<KhatmDetailViewModel, KhatmDetailViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    // A khatm that disappears while the screen is open (deleted here) takes the screen with it.
    var wasReady by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase == KhatmDetailPhase.READY) wasReady = true
        if (state.phase == KhatmDetailPhase.NOT_FOUND && wasReady) onBack()
    }

    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        var menuOpen by remember { mutableStateOf(false) }
        ScreenTopBar(
            title = state.name.ifEmpty { stringResource(R.string.screen_khatm_detail) },
            onBack = onBack,
            actions = {
                if (state.phase == KhatmDetailPhase.READY) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(Sizes.touchTarget)) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.khatm_action_more), tint = colors.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.khatm_action_rename)) },
                                onClick = { menuOpen = false; viewModel.onDialog(KhatmDetailDialog.RENAME) },
                            )
                            if (!state.isComplete) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.khatm_action_change_goal)) },
                                    onClick = { menuOpen = false; viewModel.onDialog(KhatmDetailDialog.DAILY_GOAL) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.khatm_action_delete)) },
                                onClick = { menuOpen = false; viewModel.onDialog(KhatmDetailDialog.DELETE) },
                            )
                        }
                    }
                }
            },
        )
        val snapshot = state.snapshot
        when {
            state.phase == KhatmDetailPhase.NOT_FOUND -> EmptyState(
                title = stringResource(R.string.khatm_not_found_title),
                icon = Icons.Outlined.AutoStories,
                modifier = Modifier.padding(Spacing.screen),
            )
            snapshot == null -> Unit
            else -> DetailContent(state, snapshot, viewModel, onNavigate, route.khatmId)
        }
    }
    DetailDialogs(state, viewModel)
}

@Composable
private fun DetailContent(
    state: KhatmDetailUiState,
    snapshot: KhatmSnapshot,
    viewModel: KhatmDetailViewModel,
    onNavigate: (Any) -> Unit,
    khatmId: Long,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
            .testTag(KHATM_DETAIL_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
    ) {
        val completion = state.completion
        if (completion != null) {
            QCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.khatm_complete_title), style = QintarType.Title, color = colors.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.khatm_complete_dates, dateText(completion.startedDay), dateText(completion.completedDay)),
                    style = QintarType.Body,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.khatm_complete_days, formatNumber(completion.daysTaken)),
                    style = QintarType.BodyStrong,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.khatm_complete_average, pagesText(completion.averageAyahsPerDay)),
                    style = QintarType.Body,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))
                Button(
                    onClick = { onNavigate(NewKhatmRoute) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
                ) { Text(stringResource(R.string.khatm_start_new), style = QintarType.Label) }
            }
        } else {
            QCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.khatm_position_page, formatNumber(state.page)), style = QintarType.BodyStrong, color = colors.onSurface)
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onNavigate(ReaderRoute.khatm(khatmId)) },
                        modifier = Modifier.heightIn(min = Sizes.touchTarget),
                        contentPadding = PaddingValues(horizontal = Spacing.xxl),
                    ) { Text(stringResource(R.string.action_continue), style = QintarType.Label) }
                    state.firstUnread?.let { unread ->
                        OutlinedButton(
                            onClick = { onNavigate(ReaderRoute.khatm(khatmId, startAyahId = unread.value)) },
                            modifier = Modifier.heightIn(min = Sizes.touchTarget),
                        ) { Text(stringResource(R.string.khatm_action_first_unread), style = QintarType.Label) }
                    }
                }
            }
        }

        QCard(Modifier.fillMaxWidth()) {
            if (state.showTodayBar) {
                val goal = checkNotNull(state.dailyPages)
                StackedProgressRow(
                    label = stringResource(R.string.today_label),
                    valueText = stringResource(R.string.khatm_today_pages, pagesText(snapshot.todayPages), pagesText(goal.toDouble())),
                    progress = (snapshot.todayGoalFraction ?: 0f).coerceIn(0f, 1f),
                )
                Spacer(Modifier.height(Spacing.md))
            }
            if (!state.isComplete) {
                StackedProgressRow(
                    label = surahName(checkNotNull(state.currentSurah)),
                    valueText = percent(snapshot.surahBar.fraction),
                    progress = snapshot.surahBar.fraction,
                )
                Spacer(Modifier.height(Spacing.md))
                StackedProgressRow(
                    label = stringResource(R.string.juz_n, formatNumber(snapshot.currentJuz)),
                    valueText = percent(snapshot.juzBar.fraction),
                    progress = snapshot.juzBar.fraction,
                )
                Spacer(Modifier.height(Spacing.md))
            }
            StackedProgressRow(
                label = stringResource(R.string.khatm_whole_quran),
                valueText = percent(snapshot.wholeBar.fraction),
                progress = snapshot.wholeBar.fraction,
            )
        }

        Column {
            SectionHeader(stringResource(R.string.khatm_juz_coverage))
            Spacer(Modifier.height(Spacing.sm))
            JuzStrip(snapshot.juzCoverage)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
            StatTile(
                value = if (state.isComplete) formatNumber(0) else pagesText(snapshot.remainingPages),
                label = stringResource(R.string.khatm_pages_left),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = snapshot.projectedFinish?.let { dateText(it) } ?: "—",
                label = stringResource(R.string.khatm_projected_finish_label),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenCards)) {
            StatTile(
                value = state.dailyPages?.let { pagesText(it.toDouble()) } ?: "—",
                label = stringResource(R.string.khatm_daily_goal_label),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = dateText(snapshot.khatm.startedDay),
                label = stringResource(R.string.khatm_started_label),
                modifier = Modifier.weight(1f),
            )
        }

        Column {
            SectionHeader(stringResource(R.string.khatm_recent_days))
            Spacer(Modifier.height(Spacing.sm))
            QCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                if (state.recentDays.isEmpty()) {
                    Text(
                        stringResource(R.string.khatm_no_days),
                        style = QintarType.Body,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.card),
                    )
                }
                state.recentDays.forEachIndexed { i, day ->
                    if (i > 0) Hairline(Modifier.padding(horizontal = Spacing.card))
                    DayRow(day)
                }
            }
        }
    }
}

@Composable
private fun percent(fraction: Float): String = percentText(fraction)

@Composable
private fun DayRow(day: KhatmDay) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateText(day.day), style = QintarType.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.khatm_day_row, formatNumber(day.ayahs), pagesText(day.pages)),
            style = QintarType.Numeric,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 30 cells, one per juz, tinted by how much of the juz the khatm has covered. */
@Composable
internal fun JuzStrip(coverage: List<Float>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val full = coverage.count { it >= 1f }
    val description = stringResource(R.string.khatm_juz_strip_description, formatNumber(full), formatNumber(coverage.size))
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = description }.testTag(JUZ_STRIP_TAG),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (fraction in coverage) {
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(QintarShapes.Pill)
                    .background(colors.surfaceContainer),
            ) {
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height((28 * fraction.coerceIn(0f, 1f)).dp.coerceAtLeast(3.dp))
                            .background(colors.primary),
                    )
                }
            }
        }
    }
}

const val JUZ_STRIP_TAG = "juz-strip"

@Composable
private fun DetailDialogs(state: KhatmDetailUiState, viewModel: KhatmDetailViewModel) {
    when (state.dialog) {
        null -> Unit
        KhatmDetailDialog.RENAME -> {
            var text by remember { mutableStateOf(state.name) }
            AlertDialog(
                onDismissRequest = { viewModel.onDialog(null) },
                title = { Text(stringResource(R.string.khatm_action_rename), style = QintarType.Title) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.khatm_name_label)) },
                        singleLine = true,
                        isError = state.nameMissing,
                        supportingText = if (state.nameMissing) ({ Text(stringResource(R.string.khatm_name_required)) }) else null,
                        shape = QintarShapes.Inner,
                    )
                },
                confirmButton = { TextButton(onClick = { viewModel.onRename(text) }) { Text(stringResource(R.string.action_save)) } },
                dismissButton = { TextButton(onClick = { viewModel.onDialog(null) }) { Text(stringResource(R.string.action_cancel)) } },
                shape = QintarShapes.Card,
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
        KhatmDetailDialog.DAILY_GOAL -> {
            var text by remember { mutableStateOf(state.dailyPages?.let { goalText(it) }.orEmpty()) }
            AlertDialog(
                onDismissRequest = { viewModel.onDialog(null) },
                title = { Text(stringResource(R.string.khatm_goal_dialog_title), style = QintarType.Title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(stringResource(R.string.khatm_goal_dialog_message), style = QintarType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(stringResource(R.string.khatm_daily_label)) },
                            singleLine = true,
                            isError = state.goalInvalid,
                            supportingText = if (state.goalInvalid) ({ Text(stringResource(R.string.khatm_daily_invalid)) }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = QintarShapes.Inner,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.onSetDailyPages(text) }) { Text(stringResource(R.string.action_save)) } },
                dismissButton = { TextButton(onClick = { viewModel.onDialog(null) }) { Text(stringResource(R.string.action_cancel)) } },
                shape = QintarShapes.Card,
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
        KhatmDetailDialog.DELETE -> AlertDialog(
            onDismissRequest = { viewModel.onDialog(null) },
            title = { Text(stringResource(R.string.khatm_delete_title), style = QintarType.Title) },
            text = { Text(stringResource(R.string.khatm_delete_message), style = QintarType.Body) },
            confirmButton = { TextButton(onClick = viewModel::onDelete) { Text(stringResource(R.string.khatm_action_delete)) } },
            dismissButton = { TextButton(onClick = { viewModel.onDialog(null) }) { Text(stringResource(R.string.action_cancel)) } },
            shape = QintarShapes.Card,
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/** The goal as it is edited: "4" or "2.5", always with Latin digits so that it round-trips. */
private fun goalText(goal: Float): String = java.lang.String.format(java.util.Locale.ROOT, "%.1f", goal).removeSuffix(".0")
