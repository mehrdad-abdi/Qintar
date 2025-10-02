package io.github.mehrdad_abdi.quranbookmarks.domain.model

import java.time.LocalDate

/**
 * Domain model for daily reading activity.
 */
data class ReadingActivity(
    val date: LocalDate,
    val totalAyahsRead: Int,
    val trackedAyahIds: Set<String>,
    val badgeLevel: BadgeLevel
) {
    companion object {
        /**
         * Create a new ReadingActivity with calculated badge level.
         */
        fun create(
            date: LocalDate,
            totalAyahsRead: Int,
            trackedAyahIds: Set<String>
        ): ReadingActivity {
            return ReadingActivity(
                date = date,
                totalAyahsRead = totalAyahsRead,
                trackedAyahIds = trackedAyahIds,
                badgeLevel = BadgeLevel.fromAyahCount(totalAyahsRead)
            )
        }

        /**
         * Create an empty activity for a given date.
         */
        fun empty(date: LocalDate): ReadingActivity {
            return ReadingActivity(
                date = date,
                totalAyahsRead = 0,
                trackedAyahIds = emptySet(),
                badgeLevel = BadgeLevel.NONE
            )
        }
    }

    /**
     * Toggle tracking of an ayah ID.
     * Returns a new ReadingActivity with updated state.
     */
    fun toggleAyahTracking(ayahId: String): ReadingActivity {
        val newTrackedIds = if (ayahId in trackedAyahIds) {
            trackedAyahIds - ayahId
        } else {
            trackedAyahIds + ayahId
        }

        val newCount = newTrackedIds.size
        return copy(
            totalAyahsRead = newCount,
            trackedAyahIds = newTrackedIds,
            badgeLevel = BadgeLevel.fromAyahCount(newCount)
        )
    }

    /**
     * Add an ayah to tracked set.
     * Returns a new ReadingActivity with updated state.
     */
    fun addAyah(ayahId: String): ReadingActivity {
        if (ayahId in trackedAyahIds) return this

        val newTrackedIds = trackedAyahIds + ayahId
        val newCount = newTrackedIds.size
        return copy(
            totalAyahsRead = newCount,
            trackedAyahIds = newTrackedIds,
            badgeLevel = BadgeLevel.fromAyahCount(newCount)
        )
    }

    /**
     * Remove an ayah from tracked set.
     * Returns a new ReadingActivity with updated state.
     */
    fun removeAyah(ayahId: String): ReadingActivity {
        if (ayahId !in trackedAyahIds) return this

        val newTrackedIds = trackedAyahIds - ayahId
        val newCount = newTrackedIds.size
        return copy(
            totalAyahsRead = newCount,
            trackedAyahIds = newTrackedIds,
            badgeLevel = BadgeLevel.fromAyahCount(newCount)
        )
    }

    /**
     * Check if an ayah is tracked.
     */
    fun isAyahTracked(ayahId: String): Boolean {
        return ayahId in trackedAyahIds
    }

    /**
     * Get ayahs needed to reach next badge level.
     * Returns null if already at highest level.
     */
    fun getAyahsToNextLevel(): Int? {
        return badgeLevel.getAyahsToNextLevel(totalAyahsRead)
    }

    /**
     * Get the next badge level.
     * Returns null if already at highest level.
     */
    fun getNextBadgeLevel(): BadgeLevel? {
        return BadgeLevel.getNextLevel(badgeLevel)
    }
}
