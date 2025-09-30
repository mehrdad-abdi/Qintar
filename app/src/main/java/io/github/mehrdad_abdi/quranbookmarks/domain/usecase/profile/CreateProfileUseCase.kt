package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import javax.inject.Inject

class CreateProfileUseCase @Inject constructor(
    private val repository: BookmarkRepository
) {
    suspend operator fun invoke(profile: BookmarkGroup): Result<Long> {
        return try {
            if (profile.name.isBlank()) {
                Result.failure(IllegalArgumentException("Profile name cannot be empty"))
            } else {
                val id = repository.insertGroup(profile)
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}