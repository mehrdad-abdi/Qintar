package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile

import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import javax.inject.Inject

class DeleteProfileUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    suspend operator fun invoke(profileId: Long): Result<Unit> {
        return try {
            repository.deleteGroupById(profileId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}