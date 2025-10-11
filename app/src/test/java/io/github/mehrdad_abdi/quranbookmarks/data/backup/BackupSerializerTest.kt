package io.github.mehrdad_abdi.quranbookmarks.data.backup

import io.github.mehrdad_abdi.quranbookmarks.domain.model.AppSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.backup.BackupMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class BackupSerializerTest {

    private lateinit var backupSerializer: BackupSerializer

    @Before
    fun setup() {
        backupSerializer = BackupSerializer()
    }

    private fun createTestBackupData(): BackupData {
        val metadata = BackupMetadata(
            version = BackupData.CURRENT_VERSION,
            timestamp = "2025-10-11T10:00:00",
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        val bookmarks = listOf(
            Bookmark(
                id = 1,
                type = BookmarkType.AYAH,
                startSurah = 1,
                startAyah = 1,
                endSurah = 1,
                endAyah = 1,
                description = "Test bookmark",
                tags = listOf("test", "important")
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
    fun `serialize should produce valid JSON`() {
        val backupData = createTestBackupData()
        val json = backupSerializer.serialize(backupData)

        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("metadata"))
        assertTrue(json.contains("bookmarks"))
        assertTrue(json.contains("readingActivity"))
        assertTrue(json.contains("settings"))
    }

    @Test
    fun `deserialize should restore original data`() {
        val originalData = createTestBackupData()
        val json = backupSerializer.serialize(originalData)
        val deserializedData = backupSerializer.deserialize(json)

        assertEquals(originalData.metadata.version, deserializedData.metadata.version)
        assertEquals(originalData.metadata.timestamp, deserializedData.metadata.timestamp)
        assertEquals(originalData.metadata.appVersion, deserializedData.metadata.appVersion)
        assertEquals(originalData.metadata.deviceInfo, deserializedData.metadata.deviceInfo)

        assertEquals(originalData.bookmarks.size, deserializedData.bookmarks.size)
        assertEquals(originalData.bookmarks[0].id, deserializedData.bookmarks[0].id)
        assertEquals(originalData.bookmarks[0].type, deserializedData.bookmarks[0].type)
        assertEquals(originalData.bookmarks[0].description, deserializedData.bookmarks[0].description)
        assertEquals(originalData.bookmarks[0].tags, deserializedData.bookmarks[0].tags)

        assertEquals(originalData.readingActivity.size, deserializedData.readingActivity.size)
        assertEquals(originalData.readingActivity[0].date, deserializedData.readingActivity[0].date)
        assertEquals(originalData.readingActivity[0].totalAyahsRead, deserializedData.readingActivity[0].totalAyahsRead)
        assertEquals(originalData.readingActivity[0].trackedAyahIds, deserializedData.readingActivity[0].trackedAyahIds)
        assertEquals(originalData.readingActivity[0].badgeLevel, deserializedData.readingActivity[0].badgeLevel)
    }

    @Test
    fun `serialize should handle LocalDate correctly`() {
        val testDate = LocalDate.of(2025, 10, 11)
        val backupData = createTestBackupData()
        val json = backupSerializer.serialize(backupData)

        assertTrue(json.contains("2025-10-11"))
    }

    @Test
    fun `deserialize should handle LocalDate correctly`() {
        val originalData = createTestBackupData()
        val json = backupSerializer.serialize(originalData)
        val deserializedData = backupSerializer.deserialize(json)

        assertEquals(LocalDate.of(2025, 10, 11), deserializedData.readingActivity[0].date)
    }

    @Test
    fun `serialize should handle Set of tracked ayah IDs correctly`() {
        val backupData = createTestBackupData()
        val json = backupSerializer.serialize(backupData)

        assertTrue(json.contains("1:1"))
        assertTrue(json.contains("1:2"))
        assertTrue(json.contains("1:3"))
        assertTrue(json.contains("1:4"))
        assertTrue(json.contains("1:5"))
    }

    @Test
    fun `deserialize should restore Set of tracked ayah IDs correctly`() {
        val originalData = createTestBackupData()
        val originalIds = originalData.readingActivity[0].trackedAyahIds
        val json = backupSerializer.serialize(originalData)
        val deserializedData = backupSerializer.deserialize(json)
        val deserializedIds = deserializedData.readingActivity[0].trackedAyahIds

        assertEquals(originalIds.size, deserializedIds.size)
        assertEquals(originalIds, deserializedIds)
    }

    @Test
    fun `serialize should handle empty collections`() {
        val metadata = BackupMetadata(
            version = BackupData.CURRENT_VERSION,
            timestamp = "2025-10-11T10:00:00",
            appVersion = "1.0"
        )

        val backupData = BackupData(
            metadata = metadata,
            bookmarks = emptyList(),
            readingActivity = emptyList(),
            settings = AppSettings()
        )

        val json = backupSerializer.serialize(backupData)
        val deserializedData = backupSerializer.deserialize(json)

        assertTrue(deserializedData.bookmarks.isEmpty())
        assertTrue(deserializedData.readingActivity.isEmpty())
    }

    @Test
    fun `serialize should produce pretty-printed JSON`() {
        val backupData = createTestBackupData()
        val json = backupSerializer.serialize(backupData)

        // Pretty-printed JSON should have newlines and indentation
        assertTrue(json.contains("\n"))
        assertTrue(json.contains("  "))
    }
}
