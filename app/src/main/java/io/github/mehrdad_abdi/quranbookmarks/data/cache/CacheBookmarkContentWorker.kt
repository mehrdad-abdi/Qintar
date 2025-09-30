package io.github.mehrdad_abdi.quranbookmarks.data.cache

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.AyahReference
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.CacheAyahContentUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.GetAyahsFromBookmarkUseCase

/**
 * WorkManager worker for caching bookmark content in the background
 * This worker:
 * 1. Gets the bookmark by ID
 * 2. Extracts all unique ayahs from the bookmark
 * 3. Caches each ayah's text and audio
 */
@HiltWorker
class CacheBookmarkContentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkRepository: BookmarkRepository,
    private val getAyahsFromBookmarkUseCase: GetAyahsFromBookmarkUseCase,
    private val cacheAyahContentUseCase: CacheAyahContentUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val BOOKMARK_ID_KEY = "bookmark_id"
        const val RECITER_EDITION_KEY = "reciter_edition"
        const val WORK_NAME_PREFIX = "cache_bookmark_"
        private const val TAG = "CacheBookmarkWorker"

        fun getWorkName(bookmarkId: Long): String = "$WORK_NAME_PREFIX$bookmarkId"
    }

    override suspend fun doWork(): Result {
        return try {
            val bookmarkId = inputData.getLong(BOOKMARK_ID_KEY, -1L)
            if (bookmarkId == -1L) {
                Log.e(TAG, "No bookmark ID provided")
                return Result.failure()
            }

            val reciterEdition = inputData.getString(RECITER_EDITION_KEY)

            Log.d(TAG, "Starting cache for bookmark $bookmarkId with reciter: $reciterEdition")

            // Get the bookmark
            val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)
            if (bookmark == null) {
                Log.e(TAG, "Bookmark not found: $bookmarkId")
                return Result.failure()
            }

            // Extract all ayahs from the bookmark
            val ayahs = getAyahsFromBookmarkUseCase(bookmark).toMutableList()
            if (ayahs.isEmpty()) {
                Log.w(TAG, "No ayahs to cache for bookmark $bookmarkId")
                return Result.success()
            }

            // Check if any ayahs are first verses of surahs 2-114 (except 9) that need bismillah
            val needsBismillah = ayahs.any { ayahRef ->
                ayahRef.ayah == 1 && ayahRef.surah in 2..114 && ayahRef.surah != 9
            }

            if (needsBismillah) {
                // Add bismillah (Surah 1, Ayah 1 = global ayah number 1) to cache list
                val bismillahRef = AyahReference(
                    surah = 1,
                    ayah = 1,
                    globalAyahNumber = 1
                )
                if (!ayahs.contains(bismillahRef)) {
                    ayahs.add(bismillahRef)
                    Log.d(TAG, "Added bismillah to cache list for bookmark $bookmarkId")
                }
            }

            Log.d(TAG, "Caching ${ayahs.size} ayahs for bookmark $bookmarkId (bismillah: $needsBismillah)")

            // Cache all ayahs (with automatic deduplication)
            val result = cacheAyahContentUseCase.cacheMultiple(
                ayahs = ayahs,
                reciterEdition = reciterEdition,
                forceRefresh = false
            )

            if (result.isSuccess) {
                val cachedCount = result.getOrThrow().size
                Log.d(TAG, "Successfully cached $cachedCount/${ayahs.size} ayahs for bookmark $bookmarkId")
                Result.success()
            } else {
                Log.e(TAG, "Failed to cache content for bookmark $bookmarkId: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error caching bookmark content", e)
            Result.retry()
        }
    }
}