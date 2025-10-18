package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Data class representing all-time reading statistics.
 */
data class AllTimeStats(
    val totalAyahsRead: Int,
    val maxAyahsInDay: Int,
    val bestDay: BestDayInfo?
) {
    data class BestDayInfo(
        val date: LocalDate,
        val ayahsRead: Int,
        val badgeLevel: BadgeLevel
    )
}

/**
 * Use case to get all-time reading statistics.
 * Provides aggregate data across all recorded reading activities.
 */
class GetAllTimeStatsUseCase @Inject constructor(
    private val repository: ReadingActivityRepository
) {
    /**
     * Get all-time statistics including:
     * - Total ayahs read across all time
     * - Maximum ayahs read in a single day
     * - Best day details (date, count, badge)
     */
    suspend operator fun invoke(): Result<AllTimeStats> {
        return try {
            val totalAyahs = repository.getTotalAyahsAllTime()
            val maxAyahs = repository.getMaxAyahsInSingleDay()
            val bestDayActivity = repository.getBestDay()

            val bestDay = bestDayActivity?.let { activity ->
                AllTimeStats.BestDayInfo(
                    date = activity.date,
                    ayahsRead = activity.totalAyahsRead,
                    badgeLevel = activity.badgeLevel
                )
            }

            Result.success(
                AllTimeStats(
                    totalAyahsRead = totalAyahs,
                    maxAyahsInDay = maxAyahs,
                    bestDay = bestDay
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
