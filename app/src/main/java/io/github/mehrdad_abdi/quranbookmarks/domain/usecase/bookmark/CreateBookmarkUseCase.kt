package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.ScheduleBookmarkCacheUseCase
import javax.inject.Inject

class CreateBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
    private val scheduleBookmarkCacheUseCase: ScheduleBookmarkCacheUseCase
) {
    suspend operator fun invoke(bookmark: Bookmark): Result<Long> {
        return try {
            // Validate bookmark data
            if (bookmark.startSurah < 1 || bookmark.startSurah > 114) {
                return Result.failure(Exception("Invalid surah number"))
            }

            if (bookmark.startAyah < 1) {
                return Result.failure(Exception("Invalid ayah number"))
            }

            // For range bookmarks, validate end ayah
            if (bookmark.type == io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType.RANGE) {
                if (bookmark.endAyah <= bookmark.startAyah) {
                    return Result.failure(Exception("End ayah must be greater than start ayah"))
                }
            }

            val bookmarkId = repository.insertBookmark(bookmark)

            // Schedule background caching for the bookmark
            scheduleBookmarkCacheUseCase(bookmarkId)

            Result.success(bookmarkId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}