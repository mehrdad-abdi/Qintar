package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * @deprecated Replaced by GetAllBookmarksUseCase and GetBookmarksByTagUseCase
 * Kept for backward compatibility during migration
 */
@Deprecated("Use GetAllBookmarksUseCase instead")
class GetBookmarksByGroupUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    operator fun invoke(groupId: Long): Flow<List<Bookmark>> {
        // Return all bookmarks since groups no longer exist
        return repository.getAllBookmarks()
    }
}