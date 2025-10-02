package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.TemporaryBookmarkRepository
import javax.inject.Inject

class GetBookmarkByIdUseCase @Inject constructor(
    private val repository: BookmarkRepository,
    private val temporaryBookmarkRepository: TemporaryBookmarkRepository
) {
    suspend operator fun invoke(bookmarkId: Long): Result<Bookmark> {
        return try {
            // Check if this is a temporary bookmark (random reading)
            if (bookmarkId == TemporaryBookmarkRepository.TEMP_BOOKMARK_ID) {
                val tempBookmark = temporaryBookmarkRepository.getTemporaryBookmark()
                return if (tempBookmark != null) {
                    Result.success(tempBookmark)
                } else {
                    Result.failure(Exception("Temporary bookmark not found"))
                }
            }

            // Normal bookmark lookup
            val bookmark = repository.getBookmarkById(bookmarkId)
            if (bookmark != null) {
                Result.success(bookmark)
            } else {
                Result.failure(Exception("Bookmark not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}