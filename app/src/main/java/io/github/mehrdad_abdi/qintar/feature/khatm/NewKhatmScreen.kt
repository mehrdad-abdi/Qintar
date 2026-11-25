package io.github.mehrdad_abdi.qintar.feature.khatm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.common.GoToField
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.dateText
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute

/** Test tag of the new-khatm form's scrolling column. */
const val NEW_KHATM_TAG = "new-khatm"

/**
 * The form that starts a khatm (§5.5): a name (default "Khatm 2"), an optional start position
 * (`2:255`, `p300`, `j12`; empty = the beginning) and an optional pages-per-day guide with the
 * projected finish shown live. Opens the new khatm's detail when it is created.
 */
@Composable
fun NewKhatmScreen(onBack: () -> Unit, onNavigate: (route: Any) -> Unit, viewModel: NewKhatmViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val defaultName = stringResource(R.string.khatm_default_name_n, formatNumber(state.ordinal))
    LaunchedEffect(defaultName, state.loading) { if (!state.loading) viewModel.onDefaultName(defaultName) }
    LaunchedEffect(state.createdId) {
        state.createdId?.let { id ->
            onBack()
            onNavigate(KhatmDetailRoute(id))
        }
    }
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(stringResource(R.string.khatm_new_title), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
                .testTag(NEW_KHATM_TAG),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.khatm_name_label)) },
                singleLine = true,
                isError = state.nameMissing,
                supportingText = if (state.nameMissing) ({ Text(stringResource(R.string.khatm_name_required)) }) else null,
                shape = QintarShapes.Inner,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                GoToField(
                    value = state.startText,
                    onValueChange = viewModel::onStartText,
                    onSubmit = {},
                    problem = state.startProblem,
                    label = stringResource(R.string.khatm_start_from_label),
                    placeholder = stringResource(R.string.khatm_start_from_hint),
                    actionLabel = null,
                    suggestions = state.startSuggestions,
                )
                state.startRef?.let { ref ->
                    // The reference is resolved by the parser; the surah's name comes from the index in the screen.
                    ResolvedStart(ref.surah, ref.ayah)
                }
            }
            OutlinedTextField(
                value = state.dailyText,
                onValueChange = viewModel::onDailyText,
                label = { Text(stringResource(R.string.khatm_daily_label)) },
                singleLine = true,
                isError = state.dailyInvalid,
                supportingText = if (state.dailyInvalid) ({ Text(stringResource(R.string.khatm_daily_invalid)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = QintarShapes.Inner,
                modifier = Modifier.fillMaxWidth(),
            )
            state.projectedFinish?.let { day ->
                Text(
                    stringResource(R.string.khatm_projected_finish, dateText(day)),
                    style = QintarType.Label,
                    color = colors.onSurfaceVariant,
                )
            }
            Button(
                onClick = viewModel::onStart,
                enabled = !state.loading && !state.dailyInvalid && state.startProblem == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
            ) { Text(stringResource(R.string.khatm_start_action), style = QintarType.Label) }
        }
    }
}

@Composable
private fun ResolvedStart(surah: Int, ayah: Int) {
    // Surah names need the index; the form shows the reference itself, which is always accurate.
    Text(
        stringResource(R.string.khatm_start_from_resolved, io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits("$surah:$ayah")),
        style = QintarType.Label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
