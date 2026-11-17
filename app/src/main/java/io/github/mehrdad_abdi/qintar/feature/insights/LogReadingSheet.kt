package io.github.mehrdad_abdi.qintar.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.common.AyahNumberField
import io.github.mehrdad_abdi.qintar.ui.common.JuzField
import io.github.mehrdad_abdi.qintar.ui.common.NumberField
import io.github.mehrdad_abdi.qintar.ui.common.PageField
import io.github.mehrdad_abdi.qintar.ui.common.SurahPicker
import io.github.mehrdad_abdi.qintar.ui.common.dateText
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.ayahCount
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Test tag of the sheet's content. */
const val LOG_SHEET_TAG = "log-reading-sheet"

/**
 * The "Log reading" sheet (§5.8). Opens a fresh form for [day] (null = today) and, once something was
 * written, reports it through [onLogged] and closes through [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogReadingSheet(
    viewModel: LogReadingViewModel,
    day: LocalDate?,
    onDismiss: () -> Unit,
    onLogged: (LogResult) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(day) { viewModel.start(day) }
    LaunchedEffect(state.result) {
        state.result?.let { result ->
            onLogged(result)
            viewModel.onResultHandled()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LogReadingContent(state, viewModel)
    }
}

/** The form itself, separate from the sheet so that it can be shown and tested on its own. */
@Composable
fun LogReadingContent(state: LogReadingUiState, viewModel: LogReadingViewModel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl)
            .navigationBarsPadding()
            .testTag(LOG_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.log_title), style = QintarType.Title, color = colors.onSurface)
        if (!state.ready) return@Column

        DayChooser(state, viewModel)

        Text(stringResource(R.string.log_what), style = QintarType.Label, color = colors.onSurfaceVariant)
        ModeChips(state.mode, viewModel::onMode)

        when (state.mode) {
            LogMode.PASSAGE -> PassageFields(state, viewModel)
            LogMode.SURAH -> SurahPicker(state.surahs, state.surah, viewModel::onSurah, Modifier.fillMaxWidth())
            LogMode.PAGES -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PageField(
                    state.fromPageText, viewModel::onFromPageText,
                    Modifier.weight(1f), error = state.errors[LogField.FROM_PAGE]?.message,
                )
                NumberField(
                    state.toPageText, viewModel::onToPageText, stringResource(R.string.log_to_page),
                    Modifier.weight(1f), error = state.errors[LogField.TO_PAGE]?.message,
                )
            }
            LogMode.JUZ -> JuzField(state.juzText, viewModel::onJuzText, Modifier.fillMaxWidth(), error = state.errors[LogField.JUZ]?.message)
            LogMode.NUMBER -> {
                NumberField(
                    state.numberText, viewModel::onNumberText, stringResource(R.string.log_number_label),
                    Modifier.fillMaxWidth(), error = state.errors[LogField.NUMBER]?.message,
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNote,
                    label = { Text(stringResource(R.string.log_note)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = QintarShapes.Inner,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val summary = state.summary
        if (state.mode != LogMode.NUMBER && summary != null) {
            Text(
                if (summary.firstPage == summary.lastPage) {
                    stringResource(R.string.log_summary_page, ayahCount(summary.ayahCount), formatNumber(summary.firstPage))
                } else {
                    stringResource(R.string.log_summary, ayahCount(summary.ayahCount), formatNumber(summary.firstPage), formatNumber(summary.lastPage))
                },
                style = QintarType.Body,
                color = colors.onSurfaceVariant,
            )
        }
        state.refusal?.let { Text(stringResource(it.message), style = QintarType.Body, color = colors.error) }

        Button(
            onClick = viewModel::onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
        ) {
            Text(
                if (state.addCount > 0) stringResource(R.string.log_add_n, ayahCount(state.addCount)) else stringResource(R.string.log_add),
                style = QintarType.Label,
            )
        }
    }
}

@Composable
private fun PassageFields(state: LogReadingUiState, viewModel: LogReadingViewModel) {
    Text(stringResource(R.string.log_from), style = QintarType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SurahPicker(state.surahs, state.fromSurah, viewModel::onFromSurah, Modifier.weight(2f))
        AyahNumberField(state.fromAyahText, viewModel::onFromAyahText, Modifier.weight(1f), error = state.errors[LogField.FROM_AYAH]?.message)
    }
    Text(stringResource(R.string.log_to), style = QintarType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SurahPicker(state.surahs, state.toSurah, viewModel::onToSurah, Modifier.weight(2f))
        AyahNumberField(state.toAyahText, viewModel::onToAyahText, Modifier.weight(1f), error = state.errors[LogField.TO_AYAH]?.message)
    }
}

@Composable
private fun ModeChips(mode: LogMode, onMode: (LogMode) -> Unit) {
    val labels = mapOf(
        LogMode.PASSAGE to stringResource(R.string.log_mode_passage),
        LogMode.SURAH to stringResource(R.string.log_mode_surah),
        LogMode.PAGES to stringResource(R.string.log_mode_pages),
        LogMode.JUZ to stringResource(R.string.log_mode_juz),
        LogMode.NUMBER to stringResource(R.string.log_mode_number),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LogMode.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { entry ->
                    FilterChip(
                        selected = entry == mode,
                        onClick = { onMode(entry) },
                        label = { Text(labels.getValue(entry), style = QintarType.Label) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayChooser(state: LogReadingUiState, viewModel: LogReadingViewModel) {
    var picking by remember { mutableStateOf(false) }
    Text(stringResource(R.string.log_day), style = QintarType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = state.isToday,
            onClick = { viewModel.onDay(state.today) },
            label = { Text(stringResource(R.string.log_today), style = QintarType.Label) },
        )
        FilterChip(
            selected = state.isYesterday,
            onClick = { viewModel.onDay(state.today.minusDays(1)) },
            label = { Text(stringResource(R.string.log_yesterday), style = QintarType.Label) },
        )
        FilterChip(
            selected = !state.isToday && !state.isYesterday,
            onClick = { picking = true },
            label = {
                Text(
                    if (state.isToday || state.isYesterday) stringResource(R.string.log_pick_date) else dateText(state.day),
                    style = QintarType.Label,
                )
            },
        )
    }
    if (picking) {
        val lastSelectable = state.today.toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.day.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= lastSelectable
                override fun isSelectableYear(year: Int) = year <= state.today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.onDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text(stringResource(R.string.log_pick_confirm)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
