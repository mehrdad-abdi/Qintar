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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.ui.common.ScheduleEditor
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.navigation.GroupEditorRoute

/** Test tag of the group editor's Save action. */
const val GROUP_SAVE_TAG = "group-save"

/**
 * Add or edit a bookmark group: name, description, schedule and, when editing, its bookmarks.
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun GroupEditorScreen(
    route: GroupEditorRoute,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigate: (route: Any) -> Unit,
    viewModel: GroupEditorViewModel = hiltViewModel<GroupEditorViewModel, GroupEditorViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { if (state.savedId != null) onBack() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(
            stringResource(if (state.isNew) R.string.grp_editor_new else R.string.grp_editor_edit),
            onBack,
            actions = {
                if (state.phase == EditorPhase.READY) {
                    TextButton(onClick = viewModel::onSave, modifier = Modifier.testTag(GROUP_SAVE_TAG)) {
                        Text(stringResource(R.string.action_save), style = QintarType.Label)
                    }
                }
            },
        )
        when (state.phase) {
            EditorPhase.LOADING -> Unit
            EditorPhase.NOT_FOUND -> EmptyState(stringResource(R.string.grp_not_found))
            EditorPhase.READY -> GroupForm(state, viewModel)
        }
    }
}

@Composable
private fun GroupForm(state: GroupEditorUiState, vm: GroupEditorViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val nameProblem = state.problem(GroupEditorUiState.NAME)
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::onName,
            label = { Text(stringResource(R.string.grp_name)) },
            placeholder = { Text(stringResource(R.string.grp_name_hint)) },
            singleLine = true,
            isError = nameProblem != null,
            supportingText = nameProblem?.let { { Text(stringResource(it.message)) } },
            shape = QintarShapes.Inner,
            modifier = Modifier.fillMaxWidth(),
        )
        val descriptionProblem = state.problem(GroupEditorUiState.DESCRIPTION)
        OutlinedTextField(
            value = state.description,
            onValueChange = vm::onDescription,
            label = { Text(stringResource(R.string.grp_description)) },
            minLines = 2,
            isError = descriptionProblem != null,
            supportingText = descriptionProblem?.let { { Text(stringResource(it.message, formatNumber(Bookmark.MAX_DESCRIPTION))) } },
            shape = QintarShapes.Inner,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionHeader(stringResource(R.string.sched_title))
        ScheduleEditor(state.schedule, vm::onSchedule, state.weekDays, stringResource(R.string.sched_none), allowOnce = false)

        if (!state.isNew) {
            SectionHeader(stringResource(R.string.grp_members))
            if (state.members.isEmpty()) {
                Text(stringResource(R.string.grp_members_empty), style = QintarType.Body, color = colors.onSurfaceVariant)
            } else {
                QCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xs)) {
                    state.members.forEachIndexed { position, member ->
                        if (position > 0) Hairline(Modifier.padding(horizontal = Spacing.card))
                        Row(Modifier.fillMaxWidth().padding(start = Spacing.card), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                bookmarkTitleText(member.title),
                                style = QintarType.Body,
                                color = colors.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.onMoveUp(position) }, enabled = position > 0, modifier = Modifier.size(Sizes.touchTarget)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.grp_move_up))
                            }
                            IconButton(
                                onClick = { vm.onMoveDown(position) },
                                enabled = position < state.members.lastIndex,
                                modifier = Modifier.size(Sizes.touchTarget),
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.grp_move_down)) }
                            IconButton(onClick = { vm.onRemove(member.id) }, modifier = Modifier.size(Sizes.touchTarget)) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.grp_remove))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}
