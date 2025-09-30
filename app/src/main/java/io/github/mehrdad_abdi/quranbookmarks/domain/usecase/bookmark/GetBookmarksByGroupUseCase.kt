package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookmarksByGroupUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    operator fun invoke(groupId: Long): Flow<List<Bookmark>> {
        return repository.getBookmarksByGroupId(groupId)
    }
}