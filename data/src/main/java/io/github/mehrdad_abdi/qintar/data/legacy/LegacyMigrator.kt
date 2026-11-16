package io.github.mehrdad_abdi.qintar.data.legacy

import io.github.mehrdad_abdi.qintar.domain.backup.BackupManager
import io.github.mehrdad_abdi.qintar.domain.backup.ImportMode
import io.github.mehrdad_abdi.qintar.domain.backup.ImportResult
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyReport
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyV1Converter
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

sealed interface LegacyMigrationOutcome {
    /** Already decided on an earlier launch; nothing was read or written. */
    data class AlreadyHandled(val state: LegacyMigrationState) : LegacyMigrationOutcome

    /** No v1 data on this device. */
    data object NotNeeded : LegacyMigrationOutcome

    data class Migrated(val report: LegacyReport, val result: ImportResult) : LegacyMigrationOutcome

    /** Nothing was imported (the import is one transaction); v1's files are untouched. */
    data class Failed(val cause: Exception) : LegacyMigrationOutcome
}

/** What the app needs from the migration; lets screens be tested without a v1 database. */
interface LegacyMigration {
    suspend fun migrate(khatmName: String, retryFailed: Boolean = false): LegacyMigrationOutcome
}

/**
 * The in-place upgrade from v1 (§7.11), run once at first launch of v2.
 *
 * It acts only while `legacyMigrationState` is `PENDING` (or `FAILED`, when asked to retry), so calling
 * it on every launch is safe. The v1 files are never changed here; cleaning them up is a later,
 * separate step once the carried-over data has been confirmed.
 */
class LegacyMigrator @Inject constructor(
    private val prefs: PrefsStore,
    private val reader: LegacyV1Reader,
    // A Provider so that a launch with nothing to migrate does not wait for the index to load.
    private val index: Provider<QuranIndex>,
    private val backup: BackupManager,
    private val clock: DayClock,
) : LegacyMigration {
    /**
     * @param khatmName the localized name for the khatm carried over from v1's "last page read".
     * @param retryFailed also run when an earlier attempt ended in `FAILED`.
     */
    override suspend fun migrate(khatmName: String, retryFailed: Boolean): LegacyMigrationOutcome {
        val before = prefs.prefs.first()
        val state = before.legacyMigrationState
        // IN_PROGRESS: an earlier run died after writing the marker. Its import may have committed.
        val interrupted = state == LegacyMigrationState.IN_PROGRESS
        val due = state == LegacyMigrationState.PENDING || interrupted ||
            (retryFailed && state == LegacyMigrationState.FAILED)
        if (!due) return LegacyMigrationOutcome.AlreadyHandled(state)

        return try {
            when (val read = reader.read()) {
                LegacyReadResult.NotPresent -> {
                    setState(LegacyMigrationState.NOT_NEEDED)
                    LegacyMigrationOutcome.NotNeeded
                }
                is LegacyReadResult.Unreadable -> fail(read.cause, interrupted)
                is LegacyReadResult.Found -> {
                    val conversion = LegacyV1Converter.convert(
                        v1 = read.data,
                        index = index.get(),
                        zone = clock.zone(),
                        today = clock.today(),
                        now = clock.now(),
                        khatmName = khatmName,
                    )
                    // The marker goes first: the import is one transaction but the state is a separate
                    // write, and a crash between them would otherwise import everything twice.
                    setState(LegacyMigrationState.IN_PROGRESS)
                    // After an interruption the earlier import may be on disk. Migration runs at
                    // start-up, before the user can have added anything, so what is there is that
                    // import: replacing it (rather than merging into it) cannot duplicate anything.
                    val result = backup.import(conversion.data, if (interrupted) ImportMode.REPLACE else ImportMode.MERGE)
                    // The import leaves preferences alone (a replace writes the file's, which are
                    // not wanted here), so v1's settings are applied to what was there before,
                    // together with the state: one atomic preferences write.
                    val carried = conversion.data.prefs
                    prefs.update { current ->
                        val base = if (interrupted) before else current
                        (if (carried != null) base.withV1Settings(carried) else base)
                            .copy(legacyMigrationState = LegacyMigrationState.MIGRATED)
                    }
                    LegacyMigrationOutcome.Migrated(conversion.report, result)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure, interrupted)
        }
    }

    /**
     * After an interrupted run the marker stays: the earlier import may be on disk, and only the
     * replace done under the marker is safe to retry (the next start tries again).
     */
    private suspend fun fail(cause: Exception, keepMarker: Boolean): LegacyMigrationOutcome {
        if (!keepMarker) setState(LegacyMigrationState.FAILED)
        return LegacyMigrationOutcome.Failed(cause)
    }

    private suspend fun setState(state: LegacyMigrationState) {
        prefs.update { it.copy(legacyMigrationState = state) }
    }

    /** Exactly the settings v1 had (§7.11); everything else keeps its current value. */
    private fun UserPrefs.withV1Settings(v1: UserPrefs): UserPrefs = copy(
        reciterId = v1.reciterId,
        audioQuality = v1.audioQuality,
        reminderEnabled = v1.reminderEnabled,
        reminderTime = v1.reminderTime,
        theme = v1.theme,
        playbackSpeed = v1.playbackSpeed,
        language = v1.language,
    )
}
