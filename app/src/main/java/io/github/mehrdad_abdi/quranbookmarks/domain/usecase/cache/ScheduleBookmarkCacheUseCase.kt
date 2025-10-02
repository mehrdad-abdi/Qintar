package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache

import android.util.Log
import androidx.work.*
import io.github.mehrdad_abdi.quranbookmarks.data.cache.CacheBookmarkContentWorker
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import javax.inject.Inject

/**
 * Use case to schedule background caching for a bookmark
 * This schedules a WorkManager job to cache the bookmark's content
 */
class ScheduleBookmarkCacheUseCase @Inject constructor(
    private val workManager: WorkManager,
    private val bookmarkRepository: BookmarkRepository
) {
    companion object {
        private const val TAG = "ScheduleBookmarkCache"
    }

    suspend operator fun invoke(bookmarkId: Long): Result<Unit> {
        return try {
            // Get bookmark to verify it exists
            val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
            if (bookmark == null) {
                Log.e(TAG, "Bookmark not found: $bookmarkId")
                return Result.failure(Exception("Bookmark not found"))
            }

            // Use default reciter (global app setting)
            // TODO: Replace with actual settings repository when implemented
            val reciterEdition = "ar.alafasy"

            Log.d(TAG, "Scheduling cache for bookmark $bookmarkId with reciter: $reciterEdition")

            // Create work request with constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                CacheBookmarkContentWorker.BOOKMARK_ID_KEY to bookmarkId,
                CacheBookmarkContentWorker.RECITER_EDITION_KEY to reciterEdition
            )

            val workRequest = OneTimeWorkRequestBuilder<CacheBookmarkContentWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("cache_bookmark")
                .addTag("bookmark_$bookmarkId")
                .build()

            // Use unique work to avoid duplicate caching
            val workName = CacheBookmarkContentWorker.getWorkName(bookmarkId)
            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "Scheduled cache work for bookmark $bookmarkId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling cache for bookmark", e)
            Result.failure(e)
        }
    }

    /**
     * Schedule caching for multiple bookmarks
     */
    suspend fun scheduleMultiple(bookmarkIds: List<Long>): Result<Unit> {
        return try {
            var successCount = 0
            val failures = mutableListOf<Long>()

            for (bookmarkId in bookmarkIds) {
                val result = invoke(bookmarkId)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failures.add(bookmarkId)
                }
            }

            Log.d(TAG, "Scheduled caching for $successCount/${bookmarkIds.size} bookmarks")
            if (failures.isNotEmpty()) {
                Log.w(TAG, "Failed to schedule: ${failures.joinToString(", ")}")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling multiple caches", e)
            Result.failure(e)
        }
    }

    /**
     * Cancel scheduled caching for a bookmark
     */
    fun cancelCache(bookmarkId: Long) {
        val workName = CacheBookmarkContentWorker.getWorkName(bookmarkId)
        workManager.cancelUniqueWork(workName)
        Log.d(TAG, "Cancelled cache work for bookmark $bookmarkId")
    }
}