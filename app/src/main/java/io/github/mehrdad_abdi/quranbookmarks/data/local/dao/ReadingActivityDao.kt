package io.github.mehrdad_abdi.quranbookmarks.data.local.dao

import androidx.room.*
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.ReadingActivityEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for reading activity operations.
 */
@Dao
interface ReadingActivityDao {

    /**
     * Insert or update a reading activity record for a specific date.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: ReadingActivityEntity)

    /**
     * Get reading activity for a specific date.
     */
    @Query("SELECT * FROM reading_activity WHERE date = :date")
    suspend fun getByDate(date: String): ReadingActivityEntity?

    /**
     * Get reading activity for a specific date as Flow (reactive).
     */
    @Query("SELECT * FROM reading_activity WHERE date = :date")
    fun getByDateFlow(date: String): Flow<ReadingActivityEntity?>

    /**
     * Get reading activities for a date range (inclusive).
     * Ordered by date descending (most recent first).
     */
    @Query("SELECT * FROM reading_activity WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<ReadingActivityEntity>

    /**
     * Get all reading activities ordered by date descending.
     */
    @Query("SELECT * FROM reading_activity ORDER BY date DESC")
    suspend fun getAll(): List<ReadingActivityEntity>

    /**
     * Get all reading activities as Flow for reactive updates.
     */
    @Query("SELECT * FROM reading_activity ORDER BY date DESC")
    fun getAllFlow(): Flow<List<ReadingActivityEntity>>

    /**
     * Get total ayahs read across all time.
     */
    @Query("SELECT SUM(totalAyahsRead) FROM reading_activity")
    suspend fun getTotalAyahsAllTime(): Int?

    /**
     * Get the maximum ayahs read in a single day.
     */
    @Query("SELECT MAX(totalAyahsRead) FROM reading_activity")
    suspend fun getMaxAyahsInSingleDay(): Int?

    /**
     * Get the date with maximum ayahs read.
     */
    @Query("SELECT * FROM reading_activity ORDER BY totalAyahsRead DESC LIMIT 1")
    suspend fun getBestDay(): ReadingActivityEntity?

    /**
     * Get count of days with at least minimum ayahs read.
     */
    @Query("SELECT COUNT(*) FROM reading_activity WHERE totalAyahsRead >= :minimumAyahs")
    suspend fun getDaysWithMinimumAyahs(minimumAyahs: Int): Int

    /**
     * Delete reading activity for a specific date.
     */
    @Query("DELETE FROM reading_activity WHERE date = :date")
    suspend fun deleteByDate(date: String)

    /**
     * Delete all reading activities.
     */
    @Query("DELETE FROM reading_activity")
    suspend fun deleteAll()
}
