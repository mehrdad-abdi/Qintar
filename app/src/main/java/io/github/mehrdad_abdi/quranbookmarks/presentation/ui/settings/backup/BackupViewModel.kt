package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupSummary
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportOptions
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportResult
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportStrategy
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup.ExportDataUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup.FileOperationsUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup.ImportDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val exportSuccess: Boolean = false,
    val importSuccess: Boolean = false,
    val errorMessage: String? = null,
    val backupSummary: BackupSummary? = null,
    val showImportDialog: Boolean = false,
    val importOptions: ImportOptions = ImportOptions()
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase,
    private val fileOperationsUseCase: FileOperationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /**
     * Get default backup filename.
     */
    fun getBackupFilename(): String {
        return fileOperationsUseCase.generateBackupFilename()
    }

    /**
     * Export all data to the specified URI.
     */
    fun exportAllData(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, exportSuccess = false) }

            val result = exportDataUseCase.exportAll()
            result.fold(
                onSuccess = { json ->
                    val writeResult = fileOperationsUseCase.writeBackupToUri(uri, json)
                    writeResult.fold(
                        onSuccess = {
                            _uiState.update { it.copy(isLoading = false, exportSuccess = true) }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to write backup: ${error.message}"
                                )
                            }
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to export data: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Export only bookmarks to the specified URI.
     */
    fun exportBookmarks(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, exportSuccess = false) }

            val result = exportDataUseCase.exportBookmarks()
            result.fold(
                onSuccess = { json ->
                    val writeResult = fileOperationsUseCase.writeBackupToUri(uri, json)
                    writeResult.fold(
                        onSuccess = {
                            _uiState.update { it.copy(isLoading = false, exportSuccess = true) }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to write backup: ${error.message}"
                                )
                            }
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to export bookmarks: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Read and validate backup file from URI.
     */
    fun readBackupFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val readResult = fileOperationsUseCase.readBackupFromUri(uri)
            readResult.fold(
                onSuccess = { json ->
                    val summaryResult = importDataUseCase.getBackupSummary(json)
                    summaryResult.fold(
                        onSuccess = { summary ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    backupSummary = summary,
                                    showImportDialog = true
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Invalid backup file: ${error.message}"
                                )
                            }
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to read backup file: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Import backup data with current options.
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, importSuccess = false) }

            val readResult = fileOperationsUseCase.readBackupFromUri(uri)
            readResult.fold(
                onSuccess = { json ->
                    val importResult = importDataUseCase.import(json, _uiState.value.importOptions)
                    when (importResult) {
                        is ImportResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    importSuccess = true,
                                    showImportDialog = false
                                )
                            }
                        }
                        is ImportResult.PartialSuccess -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    importSuccess = true,
                                    showImportDialog = false,
                                    errorMessage = "Imported with warnings: ${importResult.warnings.joinToString()}"
                                )
                            }
                        }
                        is ImportResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = importResult.message
                                )
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to read backup file: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Update import options.
     */
    fun updateImportOptions(options: ImportOptions) {
        _uiState.update { it.copy(importOptions = options) }
    }

    /**
     * Update import strategy.
     */
    fun updateImportStrategy(strategy: ImportStrategy) {
        _uiState.update {
            it.copy(importOptions = it.importOptions.copy(strategy = strategy))
        }
    }

    /**
     * Dismiss import dialog.
     */
    fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false, backupSummary = null) }
    }

    /**
     * Clear success/error messages.
     */
    fun clearMessages() {
        _uiState.update {
            it.copy(
                exportSuccess = false,
                importSuccess = false,
                errorMessage = null
            )
        }
    }
}
