package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Track the current URI for import
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for creating a backup file
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportAllData(it) }
    }

    // Launcher for creating a bookmarks-only backup
    val createBookmarksDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBookmarks(it) }
    }

    // Launcher for opening a backup file
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            pendingImportUri = it
            viewModel.readBackupFile(it)
        }
    }

    // Show success/error messages
    LaunchedEffect(uiState.exportSuccess, uiState.importSuccess, uiState.errorMessage) {
        when {
            uiState.exportSuccess -> {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_exported))
                viewModel.clearMessages()
            }
            uiState.importSuccess -> {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_imported))
                viewModel.clearMessages()
                pendingImportUri = null
            }
            uiState.errorMessage != null -> {
                snackbarHostState.showSnackbar(uiState.errorMessage!!)
                viewModel.clearMessages()
            }
        }
    }

    // Import confirmation dialog
    if (uiState.showImportDialog && uiState.backupSummary != null) {
        ImportConfirmationDialog(
            summary = uiState.backupSummary!!,
            currentStrategy = uiState.importOptions.strategy,
            onStrategyChange = { viewModel.updateImportStrategy(it) },
            onConfirm = {
                pendingImportUri?.let { viewModel.importBackup(it) }
            },
            onDismiss = {
                viewModel.dismissImportDialog()
                pendingImportUri = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(RtlIcons.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.about_backup),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.backup_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Export section
            Text(
                text = stringResource(R.string.export_data),
                style = MaterialTheme.typography.titleLarge
            )

            Button(
                onClick = {
                    createDocumentLauncher.launch(viewModel.getBackupFilename())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Icon(Icons.Default.CloudUpload, stringResource(R.string.export_data))
                Spacer(modifier = Modifier.padding(4.dp))
                Text(stringResource(R.string.export_all_data))
            }

            OutlinedButton(
                onClick = {
                    val filename = viewModel.getBackupFilename().replace(".json", "_bookmarks.json")
                    createBookmarksDocumentLauncher.launch(filename)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Icon(Icons.Default.CloudUpload, stringResource(R.string.export_data))
                Spacer(modifier = Modifier.padding(4.dp))
                Text(stringResource(R.string.export_bookmarks_only))
            }

            // Import section
            Text(
                text = stringResource(R.string.import_data),
                style = MaterialTheme.typography.titleLarge
            )

            Button(
                onClick = {
                    openDocumentLauncher.launch(arrayOf("application/json"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Icon(Icons.Default.CloudDownload, stringResource(R.string.import_data))
                Spacer(modifier = Modifier.padding(4.dp))
                Text(stringResource(R.string.import_from_file))
            }

            // Warning text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.import_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun ImportConfirmationDialog(
    summary: io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupSummary,
    currentStrategy: ImportStrategy,
    onStrategyChange: (ImportStrategy) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_backup_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.backup_summary_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.backup_summary_bookmarks, summary.bookmarkCount))
                Text(stringResource(R.string.backup_summary_activity, summary.readingActivityCount))
                Text(stringResource(R.string.backup_summary_ayahs, summary.totalAyahsRead))
                Text(stringResource(R.string.backup_summary_created, summary.timestamp.take(10)))
                Text(stringResource(R.string.backup_summary_version, summary.appVersion))

                Spacer(modifier = Modifier.height(8.dp))

                Text(stringResource(R.string.import_strategy_title), style = MaterialTheme.typography.titleSmall)

                ImportStrategyOption(
                    label = stringResource(R.string.import_merge),
                    description = stringResource(R.string.import_merge_desc),
                    selected = currentStrategy == ImportStrategy.MERGE,
                    onClick = { onStrategyChange(ImportStrategy.MERGE) }
                )

                ImportStrategyOption(
                    label = stringResource(R.string.import_replace_all),
                    description = stringResource(R.string.import_replace_all_desc),
                    selected = currentStrategy == ImportStrategy.REPLACE_ALL,
                    onClick = { onStrategyChange(ImportStrategy.REPLACE_ALL) }
                )

                ImportStrategyOption(
                    label = stringResource(R.string.import_bookmarks_only),
                    description = stringResource(R.string.import_bookmarks_only_desc),
                    selected = currentStrategy == ImportStrategy.BOOKMARKS_ONLY,
                    onClick = { onStrategyChange(ImportStrategy.BOOKMARKS_ONLY) }
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.button_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ImportStrategyOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
