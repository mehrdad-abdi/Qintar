package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for reading activity operations.
 */
interface ReadingActivityRepository {

    /**
     * Save or update reading activity for a specific date.
     */
    suspend fun saveActivity(activity: ReadingActivity)

    /**
     * Get reading activity for a specific date.
     * Returns null if no activity exists for that date.
     */
    suspend fun getActivityByDate(date: LocalDate): ReadingActivity?

    /**
     * Get reading activity for a specific date as Flow (reactive).
     */
    fun getActivityByDateFlow(date: LocalDate): Flow<ReadingActivity?>

    /**
     * Get reading activities for a date range (inclusive).
     * Returns activities ordered by date descending (most recent first).
     */
    suspend fun getActivitiesByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ReadingActivity>

    /**
     * Get all reading activities ordered by date descending.
     */
    suspend fun getAllActivities(): List<ReadingActivity>

    /**
     * Get total ayahs read across all time.
     */
    suspend fun getTotalAyahsAllTime(): Int

    /**
     * Get the maximum ayahs read in a single day.
     */
    suspend fun getMaxAyahsInSingleDay(): Int

    /**
     * Get the activity with the most ayahs read (best day).
     * Returns null if no activities exist.
     */
    suspend fun getBestDay(): ReadingActivity?

    /**
     * Get count of days with at least minimum ayahs read.
     */
    suspend fun getDaysWithMinimumAyahs(minimumAyahs: Int): Int

    /**
     * Delete reading activity for a specific date.
     */
    suspend fun deleteActivityByDate(date: LocalDate)

    /**
     * Delete all reading activities.
     */
    suspend fun deleteAllActivities()
}
