package io.github.mehrdad_abdi.qintar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.BuildConfig
import io.github.mehrdad_abdi.qintar.data.backup.BackupCodec
import io.github.mehrdad_abdi.qintar.data.backup.BackupDecodeResult
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.backup.BackupData
import io.github.mehrdad_abdi.qintar.domain.backup.BackupManager
import io.github.mehrdad_abdi.qintar.domain.backup.BackupPreview
import io.github.mehrdad_abdi.qintar.domain.backup.BackupProblem
import io.github.mehrdad_abdi.qintar.domain.backup.ImportMode
import io.github.mehrdad_abdi.qintar.domain.backup.ImportResult
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyReport
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyV1Converter
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why a backup file or an export could not be used; the screen turns each into a sentence. */
sealed interface BackupError {
    /** The file could not be read from storage. */
    data object Unreadable : BackupError

    /** The file was read but is not a usable backup; [reason] and [detail] come from the decoder. */
    data class Invalid(val reason: BackupDecodeResult.Invalid.Reason, val detail: String) : BackupError

    /** A readable Qintar backup whose content is inconsistent. */
    data class Rejected(val problems: List<BackupProblem>) : BackupError

    data object ExportFailed : BackupError

    /** The import failed and, being one transaction, changed nothing. */
    data object ImportFailed : BackupError
}

sealed interface BackupPhase {
    data object Idle : BackupPhase
    data object Working : BackupPhase

    /** The file was written. */
    data object Exported : BackupPhase

    /**
     * A file was read and understood; nothing has been written yet. [fromV1] files are converted and
     * can only be merged, never replace.
     */
    data class Preview(val preview: BackupPreview, val fromV1: Boolean, val legacy: LegacyReport?) : BackupPhase

    data class Imported(val mode: ImportMode, val result: ImportResult) : BackupPhase

    data class Failed(val error: BackupError) : BackupPhase
}

/**
 * Backup and restore (§7.10). The screen owns the system file picker; this holds the steps in
 * between: encode, decode, preview, and the import the user confirms. A file is parsed and validated
 * first and only [confirm] writes.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
    private val codec: BackupCodec,
    private val index: Provider<QuranIndex>,
    private val clock: DayClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _phase = MutableStateFlow<BackupPhase>(BackupPhase.Idle)
    val phase: StateFlow<BackupPhase> = _phase.asStateFlow()

    /** The decoded (and, for v1, converted) file waiting for the user's choice. */
    private var pending: Pending? = null

    private class Pending(val data: BackupData, val fromV1: Boolean)

    /** The name offered by the "save as" dialog. */
    fun suggestedFileName(): String = "qintar-backup-${clock.today()}.json"

    /** @param write receives the file's text; the screen copies it to the chosen document. */
    fun export(write: suspend (String) -> Unit) {
        if (_phase.value == BackupPhase.Working) return
        _phase.value = BackupPhase.Working
        viewModelScope.launch {
            _phase.value = try {
                val text = withContext(io) { codec.encode(backup.export(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) }
                write(text)
                BackupPhase.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupPhase.Failed(BackupError.ExportFailed)
            }
        }
    }

    /**
     * The user picked a file. [read] returns its text. Nothing is written: the outcome is a preview or
     * an error.
     *
     * @param khatmName the localized name of the khatm a v1 file's "last page read" becomes.
     */
    fun chooseFile(khatmName: String, read: suspend () -> String) {
        if (_phase.value == BackupPhase.Working) return
        _phase.value = BackupPhase.Working
        pending = null
        viewModelScope.launch {
            _phase.value = try {
                val text = try {
                    read()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (text == null) BackupPhase.Failed(BackupError.Unreadable) else examine(text, khatmName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupPhase.Failed(BackupError.Unreadable)
            }
        }
    }

    private suspend fun examine(text: String, khatmName: String): BackupPhase = withContext(io) {
        when (val decoded = codec.decode(text)) {
            is BackupDecodeResult.Invalid -> BackupPhase.Failed(BackupError.Invalid(decoded.reason, decoded.detail))
            is BackupDecodeResult.Backup -> preview(decoded.data, fromV1 = false, legacy = null)
            is BackupDecodeResult.LegacyV1 -> {
                val conversion = LegacyV1Converter.convert(
                    v1 = decoded.data,
                    index = index.get(),
                    zone = clock.zone(),
                    today = clock.today(),
                    now = clock.now(),
                    khatmName = khatmName,
                )
                preview(conversion.data, fromV1 = true, legacy = conversion.report)
            }
        }
    }

    private fun preview(data: BackupData, fromV1: Boolean, legacy: LegacyReport?): BackupPhase {
        val preview = backup.preview(data)
        if (!preview.importable) return BackupPhase.Failed(BackupError.Rejected(preview.problems))
        pending = Pending(data, fromV1)
        return BackupPhase.Preview(preview, fromV1, legacy)
    }

    /** The user chose Replace or Merge. A v1 file is always merged. Does nothing without a preview. */
    fun confirm(mode: ImportMode) {
        val file = pending ?: return
        if (_phase.value !is BackupPhase.Preview) return
        val effective = if (file.fromV1) ImportMode.MERGE else mode
        _phase.value = BackupPhase.Working
        viewModelScope.launch {
            _phase.value = try {
                BackupPhase.Imported(effective, backup.import(file.data, effective))
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainFailure.InvalidBackup) {
                BackupPhase.Failed(BackupError.Rejected(e.problems))
            } catch (e: Exception) {
                BackupPhase.Failed(BackupError.ImportFailed)
            }
            pending = null
        }
    }

    /** Closes the preview, the result or the error without doing anything. */
    fun dismiss() {
        pending = null
        _phase.value = BackupPhase.Idle
    }
}
