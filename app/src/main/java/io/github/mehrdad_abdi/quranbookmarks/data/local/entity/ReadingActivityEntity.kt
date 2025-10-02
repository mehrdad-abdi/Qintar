package io.github.mehrdad_abdi.quranbookmarks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing daily reading activity.
 * Stores the count of ayahs read per day and the IDs of tracked ayahs.
 */
@Entity(tableName = "reading_activity")
data class ReadingActivityEntity(
    @PrimaryKey
    val date: String, // Format: "YYYY-MM-DD" (e.g., "2025-09-30")

    val totalAyahsRead: Int, // Total count of ayahs read on this date

    val trackedAyahIds: String, // JSON array of tracked ayah IDs: ["15:2:255", "15:2:256"]

    val badgeLevel: String // Badge level achieved: "NONE", "GHAIR_GHAFIL", "DHAKIR", etc.
)
