package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Data class representing a day's reading summary for display in charts/calendar.
 */
data class DailyReadingSummary(
    val date: LocalDate,
    val ayahsRead: Int,
    val badgeLevel: BadgeLevel,
    val badgeEmoji: String
)

/**
 * Use case to get daily reading history for charts and calendar views.
 */
class GetDailyHistoryUseCase @Inject constructor(
    private val repository: ReadingActivityRepository
) {
    /**
     * Get reading history for the last N days (default: 30).
     * Fills in missing days with zero values.
     */
    suspend fun getLastNDays(days: Int = 30): Result<List<DailyReadingSummary>> {
        return try {
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong() - 1)

            val activities = repository.getActivitiesByDateRange(startDate, endDate)
            val activityMap = activities.associateBy { it.date }

            // Fill in all days in range, including missing ones
            val history = mutableListOf<DailyReadingSummary>()
            var currentDate = startDate

            while (currentDate <= endDate) {
                val activity = activityMap[currentDate]
                history.add(
                    DailyReadingSummary(
                        date = currentDate,
                        ayahsRead = activity?.totalAyahsRead ?: 0,
                        badgeLevel = activity?.badgeLevel ?: BadgeLevel.NONE,
                        badgeEmoji = activity?.badgeLevel?.emoji ?: ""
                    )
                )
                currentDate = currentDate.plusDays(1)
            }

            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get reading history for a specific month.
     * Returns all days in the month with their reading data.
     */
    suspend fun getForMonth(yearMonth: YearMonth): Result<List<DailyReadingSummary>> {
        return try {
            val startDate = yearMonth.atDay(1)
            val endDate = yearMonth.atEndOfMonth()

            val activities = repository.getActivitiesByDateRange(startDate, endDate)
            val activityMap = activities.associateBy { it.date }

            // Fill in all days in the month
            val history = mutableListOf<DailyReadingSummary>()
            var currentDate = startDate

            while (currentDate <= endDate) {
                val activity = activityMap[currentDate]
                history.add(
                    DailyReadingSummary(
                        date = currentDate,
                        ayahsRead = activity?.totalAyahsRead ?: 0,
                        badgeLevel = activity?.badgeLevel ?: BadgeLevel.NONE,
                        badgeEmoji = activity?.badgeLevel?.emoji ?: ""
                    )
                )
                currentDate = currentDate.plusDays(1)
            }

            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get reading history for a custom date range.
     * Returns only dates with recorded activities (no filling).
     */
    suspend fun getForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyReadingSummary>> {
        return try {
            val activities = repository.getActivitiesByDateRange(startDate, endDate)

            val history = activities.map { activity ->
                DailyReadingSummary(
                    date = activity.date,
                    ayahsRead = activity.totalAyahsRead,
                    badgeLevel = activity.badgeLevel,
                    badgeEmoji = activity.badgeLevel.emoji
                )
            }

            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
