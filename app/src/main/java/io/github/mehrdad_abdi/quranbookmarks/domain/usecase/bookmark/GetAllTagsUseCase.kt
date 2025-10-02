package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import javax.inject.Inject

class GetAllTagsUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    suspend operator fun invoke(): List<String> {
        return repository.getAllTags()
    }
}
