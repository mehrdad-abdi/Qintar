package io.github.mehrdad_abdi.qintar.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigration
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigrationOutcome
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyReport
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The numbers of the last v1 migration that ran in this process. They are not stored, so a card shown
 * after a restart has no numbers and says only that the data was carried over.
 */
@Singleton
class MigrationReports @Inject constructor() {
    private val _last = MutableStateFlow<LegacyReport?>(null)
    val last: StateFlow<LegacyReport?> = _last.asStateFlow()

    fun record(outcome: LegacyMigrationOutcome) {
        if (outcome is LegacyMigrationOutcome.Migrated) _last.value = outcome.report
    }
}

/** What Today shows about carrying v1 data over (§5.10). */
sealed interface MigrationNotice {
    data object Hidden : MigrationNotice

    /** Shown once; [report] is null when the migration ran in an earlier process. */
    data class Carried(val report: LegacyReport?) : MigrationNotice

    /** The old data is untouched; [retrying] while "Try again" runs. */
    data class Failed(val retrying: Boolean) : MigrationNotice
}

@HiltViewModel
class MigrationNoticeViewModel @Inject constructor(
    private val prefs: PrefsStore,
    private val migration: LegacyMigration,
    private val reports: MigrationReports,
) : ViewModel() {
    private val retrying = MutableStateFlow(false)

    val notice: StateFlow<MigrationNotice> = MutableStateFlow<MigrationNotice>(MigrationNotice.Hidden).also { out ->
        viewModelScope.launch {
            combine(prefs.prefs, reports.last, retrying) { p, report, busy ->
                when (p.legacyMigrationState) {
                    LegacyMigrationState.MIGRATED -> MigrationNotice.Carried(report)
                    LegacyMigrationState.FAILED -> MigrationNotice.Failed(busy)
                    else -> MigrationNotice.Hidden
                }
            }.collect { out.value = it }
        }
    }.asStateFlow()

    /** The card was read: it never comes back. */
    fun dismiss() {
        viewModelScope.launch {
            prefs.update { if (it.legacyMigrationState == LegacyMigrationState.MIGRATED) it.copy(legacyMigrationState = LegacyMigrationState.ACKNOWLEDGED) else it }
        }
    }

    fun retry(khatmName: String) {
        if (retrying.value) return
        viewModelScope.launch {
            retrying.update { true }
            runCatching { migration.migrate(khatmName, retryFailed = true) }.onSuccess(reports::record)
            retrying.update { false }
        }
    }
}

const val MIGRATION_CARD_TAG = "migration-card"
const val MIGRATION_DISMISS_TAG = "migration-dismiss"
const val MIGRATION_RETRY_TAG = "migration-retry"

/** The one-time card at the top of Today. Nothing is drawn while there is nothing to say. */
@Composable
fun MigrationNoticeCard(viewModel: MigrationNoticeViewModel, modifier: Modifier = Modifier) {
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    MigrationNoticeCard(
        notice = notice,
        onDismiss = viewModel::dismiss,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun MigrationNoticeCard(
    notice: MigrationNotice,
    onDismiss: () -> Unit,
    onRetry: (khatmName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    when (notice) {
        MigrationNotice.Hidden -> Unit
        is MigrationNotice.Carried -> QCard(modifier.fillMaxWidth().testTag(MIGRATION_CARD_TAG), containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
            Text(stringResource(R.string.notice_carried_title), style = QintarType.BodyStrong)
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(R.string.notice_carried_body), style = QintarType.Body)
            notice.report?.let { report ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(
                        R.string.notice_carried_numbers,
                        formatNumber(report.bookmarks),
                        formatNumber(report.days),
                        formatNumber(report.events + report.talliedAyahs),
                    ),
                    style = QintarType.Label,
                )
                val skipped = report.skippedAyahIds + report.skippedBookmarks + report.skippedDays
                if (skipped > 0) Text(stringResource(R.string.notice_skipped, formatNumber(skipped)), style = QintarType.Caption)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag(MIGRATION_DISMISS_TAG)) {
                    Text(stringResource(R.string.notice_dismiss), style = QintarType.Label)
                }
            }
        }
        is MigrationNotice.Failed -> {
            val khatmName = stringResource(R.string.khatm_migrated_name)
            QCard(modifier.fillMaxWidth().testTag(MIGRATION_CARD_TAG), containerColor = colors.errorContainer, contentColor = colors.onErrorContainer) {
                Text(stringResource(R.string.notice_failed_title), style = QintarType.BodyStrong)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.notice_failed_body), style = QintarType.Body)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { onRetry(khatmName) },
                        enabled = !notice.retrying,
                        modifier = Modifier.testTag(MIGRATION_RETRY_TAG),
                    ) { Text(stringResource(R.string.notice_retry), style = QintarType.Label) }
                }
            }
        }
    }
}
