package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookmarksByTagUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    operator fun invoke(tag: String): Flow<List<Bookmark>> {
        return repository.getBookmarksByTag(tag)
    }
}
