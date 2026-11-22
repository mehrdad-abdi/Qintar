package io.github.mehrdad_abdi.qintar.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.diagnostics.Diagnostics
import io.github.mehrdad_abdi.qintar.diagnostics.LocalDiagnostics
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber

object DiagnosticsTags {
    const val RECORD = "diagnostics-record"
    const val SCREENSHOTS = "diagnostics-screenshots"
    const val SAVE = "diagnostics-save"
    const val ISSUE = "diagnostics-issue"
    const val CLEAR = "diagnostics-clear"
}

/**
 * Settings → Diagnostics (the canary): how to report a bug, the two switches, and saving, sharing
 * and clearing what was recorded. Everything stays on the phone until the user saves a report.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, diagnostics: Diagnostics? = LocalDiagnostics.current) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(stringResource(R.string.diagnostics_title), onBack)
        if (diagnostics == null) return@Column
        val state by diagnostics.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use(diagnostics::export) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.diagnostics_intro), style = QintarType.Body, color = colors.onSurfaceVariant)
            QCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diagnostics_steps_title), style = QintarType.BodyStrong, color = colors.onSurface)
                listOf(
                    R.string.diagnostics_step_1, R.string.diagnostics_step_2, R.string.diagnostics_step_3,
                    R.string.diagnostics_step_4, R.string.diagnostics_step_5,
                ).forEachIndexed { i, step ->
                    Text("${formatNumber(i + 1)}. ${stringResource(step)}", style = QintarType.Body, color = colors.onSurface, modifier = Modifier.padding(top = Spacing.xs))
                }
            }

            SectionHeader(stringResource(R.string.diagnostics_recording))
            Toggle(stringResource(R.string.diagnostics_record), stringResource(R.string.diagnostics_record_summary), state.enabled, DiagnosticsTags.RECORD, enabled = true) {
                diagnostics.setEnabled(it)
            }
            Toggle(
                stringResource(R.string.diagnostics_screenshots),
                stringResource(R.string.diagnostics_screenshots_summary),
                state.screenshotsAllowed,
                DiagnosticsTags.SCREENSHOTS,
                enabled = state.enabled,
            ) { diagnostics.setScreenshotsAllowed(it) }

            SectionHeader(stringResource(R.string.diagnostics_report))
            Text(
                stringResource(R.string.diagnostics_counts, formatNumber(state.events), formatNumber(state.crashes), formatNumber(state.screenshots)),
                style = QintarType.Caption,
                color = colors.onSurfaceVariant,
            )
            Button(
                onClick = { save.launch(diagnostics.reportFileName()) },
                enabled = !state.isEmpty,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).testTag(DiagnosticsTags.SAVE),
            ) { Text(stringResource(R.string.diagnostics_save), style = QintarType.Label) }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Diagnostics.NEW_ISSUE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).testTag(DiagnosticsTags.ISSUE),
            ) { Text(stringResource(R.string.diagnostics_open_issue), style = QintarType.Label) }
            TextButton(
                onClick = diagnostics::clear,
                enabled = !state.isEmpty,
                modifier = Modifier.heightIn(min = Sizes.touchTarget).testTag(DiagnosticsTags.CLEAR),
            ) { Text(stringResource(R.string.diagnostics_clear), style = QintarType.Label, color = colors.error) }
            Text(stringResource(R.string.diagnostics_privacy), style = QintarType.Caption, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(title: String, summary: String, checked: Boolean, tag: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Spacing.sm)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = QintarType.Body, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(summary, style = QintarType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
