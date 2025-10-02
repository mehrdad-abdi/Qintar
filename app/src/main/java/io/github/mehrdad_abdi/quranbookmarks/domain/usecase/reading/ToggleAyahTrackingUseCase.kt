package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading

import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Use case to toggle ayah tracking status (mark as read/unread).
 * Updates the daily reading activity and recalculates badge level.
 */
class ToggleAyahTrackingUseCase @Inject constructor(
    private val repository: ReadingActivityRepository
) {
    /**
     * Toggle tracking for a specific ayah on a given date.
     * Creates a new activity if none exists for the date.
     *
     * @param date The date to track (default: today)
     * @param ayahId The unique identifier for the ayah (format: "bookmarkId:surah:ayah")
     * @return The updated ReadingActivity
     */
    suspend operator fun invoke(
        date: LocalDate = LocalDate.now(),
        ayahId: String
    ): Result<ReadingActivity> {
        return try {
            // Get existing activity or create empty one
            val currentActivity = repository.getActivityByDate(date)
                ?: ReadingActivity.empty(date)

            // Toggle the ayah tracking
            val updatedActivity = currentActivity.toggleAyahTracking(ayahId)

            // Save updated activity
            repository.saveActivity(updatedActivity)

            Result.success(updatedActivity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
