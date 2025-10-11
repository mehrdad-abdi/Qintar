package io.github.mehrdad_abdi.quranbookmarks.data.repository

import android.content.Context
import io.github.mehrdad_abdi.quranbookmarks.data.backup.BackupSerializer
import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportOptions
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportResult
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.ImportStrategy
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class BackupRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var readingActivityRepository: ReadingActivityRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var backupSerializer: BackupSerializer
    private lateinit var backupRepositoryImpl: BackupRepositoryImpl

    private val testBookmarks = listOf(
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

    private val testReadingActivity = listOf(
        ReadingActivity(
            date = LocalDate.of(2025, 10, 11),
            totalAyahsRead = 5,
            trackedAyahIds = setOf("1:1", "1:2", "1:3", "1:4", "1:5"),
            badgeLevel = BadgeLevel.GHAIR_GHAFIL
        )
    )

    private val testSettings = AppSettings()

    @Before
    fun setup() {
        context = mock()
        bookmarkRepository = mock()
        readingActivityRepository = mock()
        settingsRepository = mock()
        backupSerializer = BackupSerializer()

        backupRepositoryImpl = BackupRepositoryImpl(
            context = context,
            bookmarkRepository = bookmarkRepository,
            readingActivityRepository = readingActivityRepository,
            settingsRepository = settingsRepository,
            backupSerializer = backupSerializer
        )
    }

    @Test
    fun `exportAllData should return backup with all data`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        // When
        val result = backupRepositoryImpl.exportAllData()

        // Then
        assertTrue(result.isSuccess)
        val backupData = result.getOrNull()!!
        assertEquals(1, backupData.bookmarks.size)
        assertEquals(1, backupData.readingActivity.size)
        assertEquals(testSettings, backupData.settings)
    }

    @Test
    fun `exportBookmarksOnly should return backup with only bookmarks`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        // When
        val result = backupRepositoryImpl.exportBookmarksOnly()

        // Then
        assertTrue(result.isSuccess)
        val backupData = result.getOrNull()!!
        assertEquals(1, backupData.bookmarks.size)
        assertEquals(0, backupData.readingActivity.size)
    }

    @Test
    fun `exportReadingActivityOnly should return backup with only reading activity`() = runTest {
        // Given
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        // When
        val result = backupRepositoryImpl.exportReadingActivityOnly()

        // Then
        assertTrue(result.isSuccess)
        val backupData = result.getOrNull()!!
        assertEquals(0, backupData.bookmarks.size)
        assertEquals(1, backupData.readingActivity.size)
    }

    @Test
    fun `importData with BOOKMARKS_ONLY should only import bookmarks`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        val backupData = backupRepositoryImpl.exportAllData().getOrThrow()
        val options = ImportOptions(strategy = ImportStrategy.BOOKMARKS_ONLY)

        // When
        val result = backupRepositoryImpl.importData(backupData, options)

        // Then
        assertTrue(result is ImportResult.Success)
        verify(bookmarkRepository).insertBookmark(any())
        verify(readingActivityRepository, never()).saveActivity(any())
    }

    @Test
    fun `importData with MERGE should preserve existing bookmarks`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(readingActivityRepository.getActivityByDate(any())).thenReturn(null)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        val backupData = backupRepositoryImpl.exportAllData().getOrThrow()
        val options = ImportOptions(
            strategy = ImportStrategy.MERGE,
            preserveExistingBookmarks = true,
            mergeReadingActivity = true
        )

        // When
        val result = backupRepositoryImpl.importData(backupData, options)

        // Then
        assertTrue(result is ImportResult.Success)
        verify(bookmarkRepository).insertBookmark(any())
        verify(readingActivityRepository).saveActivity(any())
    }

    @Test
    fun `serializeBackup should return valid JSON string`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        val backupData = backupRepositoryImpl.exportAllData().getOrThrow()

        // When
        val result = backupRepositoryImpl.serializeBackup(backupData)

        // Then
        assertTrue(result.isSuccess)
        val json = result.getOrNull()!!
        assertTrue(json.contains("metadata"))
        assertTrue(json.contains("bookmarks"))
        assertTrue(json.contains("readingActivity"))
        assertTrue(json.contains("settings"))
    }

    @Test
    fun `parseBackupJson should deserialize valid JSON`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        val originalBackupData = backupRepositoryImpl.exportAllData().getOrThrow()
        val json = backupRepositoryImpl.serializeBackup(originalBackupData).getOrThrow()

        // When
        val result = backupRepositoryImpl.parseBackupJson(json)

        // Then
        assertTrue(result.isSuccess)
        val parsedBackupData = result.getOrNull()!!
        assertEquals(originalBackupData.bookmarks.size, parsedBackupData.bookmarks.size)
        assertEquals(originalBackupData.readingActivity.size, parsedBackupData.readingActivity.size)
    }

    @Test
    fun `validateBackup should succeed for valid backup`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        val backupData = backupRepositoryImpl.exportAllData().getOrThrow()

        // When
        val result = backupRepositoryImpl.validateBackup(backupData)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateBackup should fail for invalid backup`() = runTest {
        // Given
        whenever(bookmarkRepository.getAllBookmarks()).thenReturn(flowOf(testBookmarks))
        whenever(readingActivityRepository.getAllActivities()).thenReturn(testReadingActivity)
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(testSettings))

        val backupData = backupRepositoryImpl.exportAllData().getOrThrow()
        val invalidBackup = backupData.copy(
            bookmarks = backupData.bookmarks.map { it.copy(startSurah = 115) }
        )

        // When
        val result = backupRepositoryImpl.validateBackup(invalidBackup)

        // Then
        assertTrue(result.isFailure)
    }
}
