package io.github.mehrdad_abdi.quranbookmarks.domain.model.backup

import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupDataTest {

    private fun createValidBackupData(): BackupData {
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
                description = "Test bookmark"
            ),
            Bookmark(
                id = 2,
                type = BookmarkType.SURAH,
                startSurah = 2,
                startAyah = 1,
                endSurah = 2,
                endAyah = 286,
                description = "Al-Baqarah"
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

        val settings = AppSettings()

        return BackupData(
            metadata = metadata,
            bookmarks = bookmarks,
            readingActivity = readingActivity,
            settings = settings
        )
    }

    @Test
    fun `validate should succeed for valid backup data`() {
        val backupData = createValidBackupData()
        val result = backupData.validate()
        assertTrue(result is BackupValidationResult.Success)
    }

    @Test
    fun `validate should fail for invalid surah number`() {
        val backupData = createValidBackupData()
        val invalidBookmark = backupData.bookmarks.first().copy(startSurah = 115)
        val updatedBackupData = backupData.copy(
            bookmarks = listOf(invalidBookmark)
        )

        val result = updatedBackupData.validate()
        assertTrue(result is BackupValidationResult.Error)
        if (result is BackupValidationResult.Error) {
            assertTrue(result.errors.any { it.contains("Invalid surah number") })
        }
    }

    @Test
    fun `validate should fail for invalid ayah number`() {
        val backupData = createValidBackupData()
        val invalidBookmark = backupData.bookmarks.first().copy(startAyah = -1)
        val updatedBackupData = backupData.copy(
            bookmarks = listOf(invalidBookmark)
        )

        val result = updatedBackupData.validate()
        assertTrue(result is BackupValidationResult.Error)
        if (result is BackupValidationResult.Error) {
            assertTrue(result.errors.any { it.contains("Invalid ayah number") })
        }
    }

    @Test
    fun `validate should fail for invalid version`() {
        val backupData = createValidBackupData()
        val updatedBackupData = backupData.copy(
            metadata = backupData.metadata.copy(version = "0.9")
        )

        val result = updatedBackupData.validate()
        assertTrue(result is BackupValidationResult.Error)
        if (result is BackupValidationResult.Error) {
            assertTrue(result.errors.any { it.contains("Unsupported backup version") })
        }
    }

    @Test
    fun `validate should fail for negative ayah count`() {
        val backupData = createValidBackupData()
        val invalidActivity = backupData.readingActivity.first().copy(totalAyahsRead = -1)
        val updatedBackupData = backupData.copy(
            readingActivity = listOf(invalidActivity)
        )

        val result = updatedBackupData.validate()
        assertTrue(result is BackupValidationResult.Error)
        if (result is BackupValidationResult.Error) {
            assertTrue(result.errors.any { it.contains("Invalid ayah count") })
        }
    }

    @Test
    fun `validate should fail for mismatched ayah count and tracked IDs`() {
        val backupData = createValidBackupData()
        val invalidActivity = backupData.readingActivity.first().copy(
            totalAyahsRead = 10,
            trackedAyahIds = setOf("1:1", "1:2") // Only 2 IDs
        )
        val updatedBackupData = backupData.copy(
            readingActivity = listOf(invalidActivity)
        )

        val result = updatedBackupData.validate()
        assertTrue(result is BackupValidationResult.Error)
        if (result is BackupValidationResult.Error) {
            assertTrue(result.errors.any { it.contains("Mismatch between total count and tracked IDs") })
        }
    }

    @Test
    fun `getSummary should return correct statistics`() {
        val backupData = createValidBackupData()
        val summary = backupData.getSummary()

        assertEquals(2, summary.bookmarkCount)
        assertEquals(1, summary.readingActivityCount)
        assertEquals(5, summary.totalAyahsRead)
        assertEquals("2025-10-11T10:00:00", summary.timestamp)
        assertEquals("1.0", summary.appVersion)
    }

    @Test
    fun `getSummary should calculate total ayahs from multiple activities`() {
        val backupData = createValidBackupData()
        val additionalActivity = ReadingActivity(
            date = LocalDate.of(2025, 10, 10),
            totalAyahsRead = 10,
            trackedAyahIds = setOf("2:1", "2:2", "2:3", "2:4", "2:5", "2:6", "2:7", "2:8", "2:9", "2:10"),
            badgeLevel = BadgeLevel.DHAKIR
        )
        val updatedBackupData = backupData.copy(
            readingActivity = backupData.readingActivity + additionalActivity
        )

        val summary = updatedBackupData.getSummary()
        assertEquals(2, summary.readingActivityCount)
        assertEquals(15, summary.totalAyahsRead) // 5 + 10
    }
}
