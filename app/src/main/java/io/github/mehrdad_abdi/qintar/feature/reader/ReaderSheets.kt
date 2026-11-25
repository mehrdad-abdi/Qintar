package io.github.mehrdad_abdi.qintar.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.ui.common.GoToField
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
import kotlin.math.roundToInt

/** Test tags of the reader's two sheets. */
const val JUMP_SHEET_TAG = "reader-jump-sheet"
const val TEXT_SHEET_TAG = "reader-text-sheet"

/** The sheets over the reader: the position sheet and the "Aa" panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSheets(state: ReaderUiState, viewModel: ReaderViewModel) {
    val sheet = state.sheet ?: return
    ModalBottomSheet(
        onDismissRequest = { viewModel.onSheet(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        when (sheet) {
            ReaderSheet.JUMP -> JumpSheetContent(state, viewModel)
            ReaderSheet.TEXT -> TextSheetContent(state, viewModel)
        }
    }
}

/**
 * Jump within the reader's scope (R-RDR-7): a go-to field (`2:255`, `p300`, `j12`), the surahs and juz
 * the scope touches, and in a khatm "Resume position" and "First unread".
 */
@Composable
private fun JumpSheetContent(state: ReaderUiState, viewModel: ReaderViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl)
            .navigationBarsPadding()
            .testTag(JUMP_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.reader_jump_title), style = QintarType.Title, color = colors.onSurface)
        GoToField(
            value = state.jumpText,
            onValueChange = viewModel::onJumpText,
            onSubmit = viewModel::onJumpSubmit,
            problem = state.jumpProblem,
            suggestions = state.jumpSuggestions,
        )
        if (state.entry == ReaderEntry.KHATM) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.resume?.let { resume ->
                    OutlinedButton(onClick = { viewModel.onJumpToAyah(resume) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Text(stringResource(R.string.reader_jump_resume), style = QintarType.Label)
                    }
                }
                state.firstUnread?.let { unread ->
                    OutlinedButton(onClick = { viewModel.onJumpToAyah(unread) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Text(stringResource(R.string.reader_first_unread_chip), style = QintarType.Label)
                    }
                }
            }
        }
        if (state.scopeSurahs.size > 1) {
            Text(stringResource(R.string.reader_jump_surah), style = QintarType.Label, color = colors.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                items(state.scopeSurahs, key = { it.number }) { surah ->
                    AssistChip(
                        onClick = { viewModel.onJumpToSurah(surah.number) },
                        label = { Text("${formatNumber(surah.number)}. ${surahName(surah)}", style = QintarType.Label) },
                    )
                }
            }
        }
        if (state.scopeJuz.size > 1) {
            Text(stringResource(R.string.reader_jump_juz), style = QintarType.Label, color = colors.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                items(state.scopeJuz, key = { it }) { juz ->
                    AssistChip(
                        onClick = { viewModel.onJumpToJuz(juz) },
                        label = { Text(stringResource(R.string.juz_n, formatNumber(juz)), style = QintarType.Label) },
                    )
                }
            }
        }
    }
}

/** The "Aa" panel (R-RDR-3): Quran font size, line breaks at pause marks, keep screen on. */
@Composable
private fun TextSheetContent(state: ReaderUiState, viewModel: ReaderViewModel) {
    val colors = MaterialTheme.colorScheme
    val sizeLabel = stringResource(R.string.reader_font_size)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl)
            .navigationBarsPadding()
            .testTag(TEXT_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.reader_text_settings), style = QintarType.Title, color = colors.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sizeLabel, style = QintarType.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(formatNumber(state.fontSp), style = QintarType.Numeric, color = colors.onSurfaceVariant)
        }
        Slider(
            value = state.fontSp.toFloat(),
            onValueChange = { viewModel.onFontSize(it.roundToInt()) },
            valueRange = UserPrefs.MIN_FONT_SP.toFloat()..UserPrefs.MAX_FONT_SP.toFloat(),
            steps = UserPrefs.MAX_FONT_SP - UserPrefs.MIN_FONT_SP - 1,
            modifier = Modifier.semantics { contentDescription = sizeLabel },
        )
        SwitchRow(stringResource(R.string.reader_break_lines), state.breakAtPauseMarks, viewModel::onBreakAtPauseMarks)
        SwitchRow(stringResource(R.string.reader_keep_screen_on), state.keepScreenOn, viewModel::onKeepScreenOn)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}


