package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import javax.inject.Inject

class GetBookmarkByIdUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    suspend operator fun invoke(bookmarkId: Long): Result<Bookmark> {
        return try {
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