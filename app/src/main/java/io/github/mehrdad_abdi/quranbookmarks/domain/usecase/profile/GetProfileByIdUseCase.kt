package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import javax.inject.Inject

class GetProfileByIdUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    suspend operator fun invoke(id: Long): BookmarkGroup? {
        return repository.getGroupById(id)
    }
}