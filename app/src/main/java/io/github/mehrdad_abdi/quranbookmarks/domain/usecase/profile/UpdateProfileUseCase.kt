package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    suspend operator fun invoke(profile: BookmarkGroup): Result<Unit> {
        return try {
            if (profile.name.isBlank()) {
                Result.failure(IllegalArgumentException("Profile name cannot be empty"))
            } else {
                repository.updateGroup(profile)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}