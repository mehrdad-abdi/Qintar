package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup

import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class ExportDataUseCaseTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var exportDataUseCase: ExportDataUseCase

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
        exportDataUseCase = ExportDataUseCase(backupRepository)
    }

    @Test
    fun `exportAll should return serialized JSON`() = runTest {
        val backupData = createTestBackupData()
        val expectedJson = "{\"test\": \"data\"}"

        whenever(backupRepository.exportAllData()).thenReturn(Result.success(backupData))
        whenever(backupRepository.serializeBackup(backupData)).thenReturn(Result.success(expectedJson))

        val result = exportDataUseCase.exportAll()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == expectedJson)
    }

    @Test
    fun `exportAll should handle repository failure`() = runTest {
        val exception = Exception("Repository error")
        whenever(backupRepository.exportAllData()).thenReturn(Result.failure(exception))

        val result = exportDataUseCase.exportAll()

        assertTrue(result.isFailure)
    }

    @Test
    fun `exportBookmarks should return serialized JSON`() = runTest {
        val backupData = createTestBackupData()
        val expectedJson = "{\"test\": \"data\"}"

        whenever(backupRepository.exportBookmarksOnly()).thenReturn(Result.success(backupData))
        whenever(backupRepository.serializeBackup(backupData)).thenReturn(Result.success(expectedJson))

        val result = exportDataUseCase.exportBookmarks()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == expectedJson)
    }

    @Test
    fun `exportReadingActivity should return serialized JSON`() = runTest {
        val backupData = createTestBackupData()
        val expectedJson = "{\"test\": \"data\"}"

        whenever(backupRepository.exportReadingActivityOnly()).thenReturn(Result.success(backupData))
        whenever(backupRepository.serializeBackup(backupData)).thenReturn(Result.success(expectedJson))

        val result = exportDataUseCase.exportReadingActivity()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == expectedJson)
    }

    @Test
    fun `getBackupData should return backup without serialization`() = runTest {
        val backupData = createTestBackupData()
        whenever(backupRepository.exportAllData()).thenReturn(Result.success(backupData))

        val result = exportDataUseCase.getBackupData()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == backupData)
    }
}
