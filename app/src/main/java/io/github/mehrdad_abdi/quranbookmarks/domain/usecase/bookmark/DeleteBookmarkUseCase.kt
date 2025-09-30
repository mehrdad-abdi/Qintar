package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.CleanupOrphanedCacheUseCase
import javax.inject.Inject

class DeleteBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
    private val cleanupOrphanedCacheUseCase: CleanupOrphanedCacheUseCase
) {
    suspend operator fun invoke(bookmarkId: Long): Result<Unit> {
        return try {
            // Cancel any scheduled cache work for this bookmark
            cleanupOrphanedCacheUseCase.cancelBookmarkCache(bookmarkId)

            // Delete the bookmark
            repository.deleteBookmarkById(bookmarkId)

            // Clean up orphaned cache entries (ayahs no longer referenced by any bookmark)
            cleanupOrphanedCacheUseCase(bookmarkId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}