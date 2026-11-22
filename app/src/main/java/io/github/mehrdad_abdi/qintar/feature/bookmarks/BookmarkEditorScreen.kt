package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.ui.common.AyahNumberField
import io.github.mehrdad_abdi.qintar.ui.common.JuzField
import io.github.mehrdad_abdi.qintar.ui.common.PageField
import io.github.mehrdad_abdi.qintar.ui.common.ScheduleEditor
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.SurahPicker
import io.github.mehrdad_abdi.qintar.ui.common.scheduleText
import io.github.mehrdad_abdi.qintar.ui.designsystem.CountField
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.ayahCount
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.quranTextStyle
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute

/** Test tag of the passage preview. */
const val EDITOR_PREVIEW_TAG = "editor-preview"

/** Test tag of the Save action. */
const val EDITOR_SAVE_TAG = "editor-save"

/**
 * Add or edit a bookmark (§5.6). The preview under the pickers is the Quran text itself.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun BookmarkEditorScreen(
    route: BookmarkEditorRoute,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigate: (route: Any) -> Unit,
    viewModel: BookmarkEditorViewModel = hiltViewModel<BookmarkEditorViewModel, BookmarkEditorViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { if (state.savedId != null) onBack() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(
            stringResource(if (state.isNew) R.string.bm_editor_new else R.string.bm_editor_edit),
            onBack,
            actions = {
                if (state.phase == EditorPhase.READY) {
                    TextButton(onClick = viewModel::onSave, modifier = Modifier.testTag(EDITOR_SAVE_TAG)) {
                        Text(stringResource(R.string.action_save), style = QintarType.Label)
                    }
                }
            },
        )
        when (state.phase) {
            EditorPhase.LOADING -> Unit
            EditorPhase.NOT_FOUND -> EmptyState(stringResource(R.string.bm_not_found))
            EditorPhase.READY -> EditorForm(state, viewModel)
        }
    }
}

@Composable
private fun EditorForm(state: BookmarkEditorUiState, vm: BookmarkEditorViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Kind
        val kindLabels = mapOf(
            BookmarkKind.AYAH to stringResource(R.string.bm_kind_ayah),
            BookmarkKind.RANGE to stringResource(R.string.bm_kind_range),
            BookmarkKind.SURAH to stringResource(R.string.bm_kind_surah),
            BookmarkKind.PAGE to stringResource(R.string.bm_kind_page),
            BookmarkKind.JUZ to stringResource(R.string.bm_kind_juz),
        )
        SectionHeader(stringResource(R.string.bm_kind_label))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.kinds.forEach { kind ->
                FilterChip(
                    selected = state.kind == kind,
                    onClick = { vm.onKind(kind) },
                    label = { Text(kindLabels.getValue(kind), style = QintarType.Label) },
                )
            }
        }

        // Position
        when (state.kind) {
            BookmarkKind.AYAH -> {
                SurahPicker(state.surahs, state.surah, vm::onSurah, Modifier.fillMaxWidth())
                AyahNumberField(state.ayahText, vm::onAyahText, Modifier.fillMaxWidth(), error = state.errors[EditorField.FROM_AYAH]?.message)
            }
            BookmarkKind.RANGE -> {
                // One surah, two ayahs. A range saved across surahs (older bookmarks) still shows its end surah.
                SurahPicker(state.surahs, state.surah, vm::onSurah, Modifier.fillMaxWidth())
                AyahNumberField(
                    state.ayahText, vm::onAyahText, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.bm_range_from) + " · " + stringResource(R.string.bm_ayah_number),
                    error = state.errors[EditorField.FROM_AYAH]?.message,
                )
                if (state.toSurah != state.surah) {
                    SurahPicker(state.surahs, state.toSurah, vm::onToSurah, Modifier.fillMaxWidth(), label = stringResource(R.string.bm_range_to))
                }
                AyahNumberField(
                    state.toAyahText, vm::onToAyahText, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.bm_range_to) + " · " + stringResource(R.string.bm_ayah_number),
                    error = state.errors[EditorField.TO_AYAH]?.message,
                )
            }
            BookmarkKind.SURAH -> SurahPicker(state.surahs, state.surah, vm::onSurah, Modifier.fillMaxWidth())
            BookmarkKind.PAGE -> PageField(state.pageText, vm::onPageText, Modifier.fillMaxWidth(), error = state.errors[EditorField.PAGE]?.message)
            BookmarkKind.JUZ -> JuzField(state.juzText, vm::onJuzText, Modifier.fillMaxWidth(), error = state.errors[EditorField.JUZ]?.message)
        }

        // Summary and the text itself
        state.summary?.let { summary ->
            val text = if (summary.firstPage == summary.lastPage) {
                stringResource(R.string.bm_summary_page, ayahCount(summary.ayahCount), formatNumber(summary.firstPage))
            } else {
                stringResource(R.string.bm_summary_pages, ayahCount(summary.ayahCount), formatNumber(summary.firstPage), formatNumber(summary.lastPage))
            }
            Text(text, style = QintarType.Label, color = colors.onSurfaceVariant)
        }
        PassagePreviewCard(state.preview, state.fontSp)

        // Label and description
        OutlinedTextField(
            value = state.label,
            onValueChange = vm::onLabel,
            label = { Text(stringResource(R.string.bm_label)) },
            placeholder = { Text(stringResource(R.string.bm_label_hint)) },
            singleLine = true,
            shape = QintarShapes.Inner,
            modifier = Modifier.fillMaxWidth(),
        )
        val descriptionError = state.errors[EditorField.DESCRIPTION]
        OutlinedTextField(
            value = state.description,
            onValueChange = vm::onDescription,
            label = { Text(stringResource(R.string.bm_description)) },
            placeholder = { Text(stringResource(R.string.bm_description_hint)) },
            minLines = 4,
            isError = descriptionError != null,
            supportingText = {
                Text(
                    if (descriptionError != null) {
                        stringResource(descriptionError.message, formatNumber(Bookmark.MAX_DESCRIPTION))
                    } else {
                        stringResource(R.string.bm_char_count, formatNumber(state.description.length), formatNumber(Bookmark.MAX_DESCRIPTION))
                    },
                )
            },
            shape = QintarShapes.Inner,
            modifier = Modifier.fillMaxWidth(),
        )

        // Repeat target: hidden for now (owner, 2026-11-22); the model keeps it, deprecated.
        if (SHOW_REPEAT_TARGET) {
            SectionHeader(stringResource(R.string.bm_repeat_label))
            CountField(state.repeatTarget, vm::onRepeatTarget, min = 1)
            Text(
                state.errors[EditorField.REPEAT]?.let { stringResource(it.message) } ?: stringResource(R.string.bm_repeat_help),
                style = QintarType.Caption,
                color = if (state.errors.containsKey(EditorField.REPEAT)) colors.error else colors.onSurfaceVariant,
            )
        }

        // Group
        if (state.groups.isNotEmpty()) {
            SectionHeader(stringResource(R.string.bm_group_label))
            QCard(Modifier.fillMaxWidth()) {
                GroupChoiceRow(stringResource(R.string.bm_ungrouped), state.groupId == null) { vm.onGroup(null) }
                state.groups.forEach { group -> GroupChoiceRow(group.name, state.groupId == group.id) { vm.onGroup(group.id) } }
            }
        }

        // Schedule: hidden for now (owner, 2026-11-22), see SHOW_SCHEDULES.
        if (SHOW_SCHEDULES) {
            SectionHeader(stringResource(R.string.sched_title))
            val inherited = state.inheritedSchedule
            val noneLabel = when {
                state.groupId == null -> stringResource(R.string.sched_none)
                inherited != null -> stringResource(R.string.sched_inherit_with, scheduleText(inherited))
                else -> stringResource(R.string.sched_inherit)
            }
            ScheduleEditor(state.schedule, vm::onSchedule, state.weekDays, noneLabel)
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun GroupChoiceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = QintarType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = Spacing.md))
    }
}

/** The first ayahs of the passage as Quran text, then "… n more". */
@Composable
fun PassagePreviewCard(preview: PassagePreview, fontSp: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    QCard(modifier.fillMaxWidth().testTag(EDITOR_PREVIEW_TAG)) {
        Text(stringResource(R.string.bm_preview_label), style = QintarType.Caption, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        // One flowing paragraph, as in the reader: ayah text, then its end marker.
        preview.ayahs.forEach { ayah ->
            if (ayah.basmala) io.github.mehrdad_abdi.qintar.ui.designsystem.BasmalaRow(fontSizeSp = fontSp)
            Text(ayah.text, style = quranTextStyle(fontSp), color = colors.onSurface, modifier = Modifier.fillMaxWidth())
        }
        if (preview.more > 0) {
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(R.string.bm_preview_more, formatNumber(preview.more)), style = QintarType.Label, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
        }
        // The end of the passage, after the dots: from where to where.
        preview.last?.let { Text(it.text, style = quranTextStyle(fontSp), color = colors.onSurface, modifier = Modifier.fillMaxWidth()) }
    }
}

/** Off while "readings per day" is hidden; `Bookmark.repeatTarget` stays in the model. */
internal const val SHOW_REPEAT_TARGET = false
