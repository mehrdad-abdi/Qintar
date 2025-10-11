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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                snackbarHostState.showSnackbar("Backup exported successfully")
                viewModel.clearMessages()
            }
            uiState.importSuccess -> {
                snackbarHostState.showSnackbar("Backup imported successfully")
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
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                            text = "About Backup",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Export your bookmarks, reading activity, and settings to a JSON file. You can restore them later or on another device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Export section
            Text(
                text = "Export Data",
                style = MaterialTheme.typography.titleLarge
            )

            Button(
                onClick = {
                    createDocumentLauncher.launch(viewModel.getBackupFilename())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Icon(Icons.Default.CloudUpload, "Export")
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Export All Data")
            }

            OutlinedButton(
                onClick = {
                    val filename = viewModel.getBackupFilename().replace(".json", "_bookmarks.json")
                    createBookmarksDocumentLauncher.launch(filename)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Icon(Icons.Default.CloudUpload, "Export")
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Export Bookmarks Only")
            }

            // Import section
            Text(
                text = "Import Data",
                style = MaterialTheme.typography.titleLarge
            )

            Button(
                onClick = {
                    openDocumentLauncher.launch(arrayOf("application/json"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Icon(Icons.Default.CloudDownload, "Import")
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Import from File")
            }

            // Warning text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Note: Importing with 'Replace All' will delete all existing data. Use 'Merge' to combine with existing data.",
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
        title = { Text("Import Backup") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Backup Summary:", style = MaterialTheme.typography.titleSmall)
                Text("Bookmarks: ${summary.bookmarkCount}")
                Text("Reading Activity: ${summary.readingActivityCount} days")
                Text("Total Ayahs Read: ${summary.totalAyahsRead}")
                Text("Created: ${summary.timestamp.take(10)}")
                Text("App Version: ${summary.appVersion}")

                Spacer(modifier = Modifier.height(8.dp))

                Text("Import Strategy:", style = MaterialTheme.typography.titleSmall)

                ImportStrategyOption(
                    label = "Merge with existing data",
                    description = "Add new items, keep existing",
                    selected = currentStrategy == ImportStrategy.MERGE,
                    onClick = { onStrategyChange(ImportStrategy.MERGE) }
                )

                ImportStrategyOption(
                    label = "Replace all data",
                    description = "Delete existing, import all",
                    selected = currentStrategy == ImportStrategy.REPLACE_ALL,
                    onClick = { onStrategyChange(ImportStrategy.REPLACE_ALL) }
                )

                ImportStrategyOption(
                    label = "Bookmarks only",
                    description = "Import only bookmarks",
                    selected = currentStrategy == ImportStrategy.BOOKMARKS_ONLY,
                    onClick = { onStrategyChange(ImportStrategy.BOOKMARKS_ONLY) }
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
