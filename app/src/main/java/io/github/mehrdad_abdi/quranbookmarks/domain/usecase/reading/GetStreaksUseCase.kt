package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Data class representing reading streaks at different badge levels.
 */
data class StreakInfo(
    val overallStreak: Int, // Consecutive days with >= 10 ayahs (GHAIR_GHAFIL minimum)
    val badgeLevelStreaks: Map<BadgeLevel, Int> // Consecutive days at each badge level
) {
    /**
     * Get the top N badge level streaks sorted by badge level (highest first).
     *
     * Rules:
     * - Only show badges with streak > 0
     * - Return top N highest-value badges (by threshold)
     * - If no badges have streaks > 0, return only GHAIR_GHAFIL with 0 days
     */
    fun getTopStreaks(count: Int): List<Pair<BadgeLevel, Int>> {
        val nonZeroStreaks = badgeLevelStreaks
            .filter { it.key != BadgeLevel.NONE && it.value > 0 }
            .toList()
            .sortedByDescending { it.first.threshold }
            .take(count)

        // If no streaks exist, show GHAIR_GHAFIL with 0 days
        return if (nonZeroStreaks.isEmpty()) {
            listOf(BadgeLevel.GHAIR_GHAFIL to 0)
        } else {
            nonZeroStreaks
        }
    }
}

/**
 * Use case to calculate reading streaks.
 * Calculates:
 * 1. Overall reading streak (consecutive days with >= 10 ayahs)
 * 2. Badge-level streaks (consecutive days achieving each badge threshold)
 */
class GetStreaksUseCase @Inject constructor(
    private val repository: ReadingActivityRepository
) {
    /**
     * Calculate current streaks ending at the specified date (default: today).
     *
     * Streaks are counted backwards from the given date.
     * A streak breaks when ayahs read < threshold for that level.
     */
    suspend operator fun invoke(endDate: LocalDate = LocalDate.now()): Result<StreakInfo> {
        return try {
            // Get all activities up to end date
            val allActivities = repository.getActivitiesByDateRange(
                startDate = endDate.minusYears(5), // Look back up to 5 years
                endDate = endDate
            ).sortedByDescending { it.date } // Most recent first

            // Calculate overall streak (>= 10 ayahs minimum)
            val overallStreak = calculateStreakForThreshold(
                activities = allActivities,
                endDate = endDate,
                threshold = BadgeLevel.GHAIR_GHAFIL.threshold
            )

            // Calculate streak for each badge level
            val badgeLevelStreaks = BadgeLevel.getAllLevels().associateWith { level ->
                calculateStreakForThreshold(
                    activities = allActivities,
                    endDate = endDate,
                    threshold = level.threshold
                )
            }

            Result.success(
                StreakInfo(
                    overallStreak = overallStreak,
                    badgeLevelStreaks = badgeLevelStreaks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate consecutive days streak for a specific threshold.
     * Counts backwards from endDate while ayahsRead >= threshold.
     */
    private fun calculateStreakForThreshold(
        activities: List<ReadingActivity>,
        endDate: LocalDate,
        threshold: Int
    ): Int {
        var streak = 0
        var currentDate = endDate

        // Create a map for quick lookup
        val activityMap = activities.associateBy { it.date }

        // Count backwards from endDate
        while (true) {
            val activity = activityMap[currentDate]

            if (activity == null || activity.totalAyahsRead < threshold) {
                // Streak broken
                break
            }

            streak++
            currentDate = currentDate.minusDays(1)

            // Safety check: don't go back more than 5 years
            if (currentDate.isBefore(endDate.minusYears(5))) {
                break
            }
        }

        return streak
    }
}
