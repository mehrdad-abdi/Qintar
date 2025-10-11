package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup

import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportOptions
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportResult
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportStrategy
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class ImportDataUseCaseTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var importDataUseCase: ImportDataUseCase

    private fun createTestBackupData(): BackupData {
        val metadata = BackupMetadata(
            version = BackupData.CURRENT_VERSION,
            timestamp = "2025-10-11T10:00:00",
            appVersion = "1.0"
        )

        val bookmarks = listOf(
            Bookmark(
                id = 1,
                type = BookmarkType.AYAH,
                startSurah = 1,
                startAyah = 1,
                endSurah = 1,
                endAyah = 1,
                description = "Test"
            )
        )

        val readingActivity = listOf(
            ReadingActivity(
                date = LocalDate.of(2025, 10, 11),
                totalAyahsRead = 5,
                trackedAyahIds = setOf("1:1", "1:2", "1:3", "1:4", "1:5"),
                badgeLevel = BadgeLevel.GHAIR_GHAFIL
            )
        )

        return BackupData(
            metadata = metadata,
            bookmarks = bookmarks,
            readingActivity = readingActivity,
            settings = AppSettings()
        )
    }

    @Before
    fun setup() {
        backupRepository = mock()
        importDataUseCase = ImportDataUseCase(backupRepository)
    }

    @Test
    fun `parseAndValidate should return summary for valid backup`() = runTest {
        val backupData = createTestBackupData()
        val json = "{\"test\": \"data\"}"

        whenever(backupRepository.parseBackupJson(json)).thenReturn(Result.success(backupData))
        whenever(backupRepository.validateBackup(backupData)).thenReturn(Result.success(Unit))

        val result = importDataUseCase.parseAndValidate(json)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()!!
        assertEquals(1, summary.bookmarkCount)
        assertEquals(1, summary.readingActivityCount)
        assertEquals(5, summary.totalAyahsRead)
    }

    @Test
    fun `parseAndValidate should fail for invalid JSON`() = runTest {
        val json = "invalid json"
        val exception = Exception("Parse error")

        whenever(backupRepository.parseBackupJson(json)).thenReturn(Result.failure(exception))

        val result = importDataUseCase.parseAndValidate(json)

        assertTrue(result.isFailure)
    }

    @Test
    fun `parseAndValidate should fail for invalid backup data`() = runTest {
        val backupData = createTestBackupData()
        val json = "{\"test\": \"data\"}"

        whenever(backupRepository.parseBackupJson(json)).thenReturn(Result.success(backupData))
        whenever(backupRepository.validateBackup(backupData)).thenReturn(
            Result.failure(IllegalArgumentException("Validation failed"))
        )

        val result = importDataUseCase.parseAndValidate(json)

        assertTrue(result.isFailure)
    }

    @Test
    fun `import should succeed with valid data`() = runTest {
        val backupData = createTestBackupData()
        val json = "{\"test\": \"data\"}"
        val options = ImportOptions(strategy = ImportStrategy.MERGE)

        whenever(backupRepository.parseBackupJson(json)).thenReturn(Result.success(backupData))
        whenever(backupRepository.importData(eq(backupData), eq(options))).thenReturn(
            ImportResult.Success(
                bookmarksImported = 1,
                readingActivityImported = 1,
                settingsImported = false
            )
        )

        val result = importDataUseCase.import(json, options)

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(1, success.bookmarksImported)
        assertEquals(1, success.readingActivityImported)
    }

    @Test
    fun `import should handle parse failure`() = runTest {
        val json = "invalid json"
        val options = ImportOptions(strategy = ImportStrategy.MERGE)
        val exception = Exception("Parse error")

        whenever(backupRepository.parseBackupJson(json)).thenReturn(Result.failure(exception))

        val result = importDataUseCase.import(json, options)

        assertTrue(result is ImportResult.Error)
    }

    @Test
    fun `getBackupSummary should return summary`() = runTest {
        val backupData = createTestBackupData()
        val json = "{\"test\": \"data\"}"

        whenever(backupRepository.parseBackupJson(json)).thenReturn(Result.success(backupData))

        val result = importDataUseCase.getBackupSummary(json)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()!!
        assertEquals(1, summary.bookmarkCount)
    }

    @Test
    fun `parseBackup should return backup data without validation`() = runTest {
        val backupData = createTestBackupData()
        val json = "{\"test\": \"data\"}"

        whenever(backupRepository.parseBackupJson(json)).thenReturn(Result.success(backupData))

        val result = importDataUseCase.parseBackup(json)

        assertTrue(result.isSuccess)
        assertEquals(backupData, result.getOrNull())
    }
}
