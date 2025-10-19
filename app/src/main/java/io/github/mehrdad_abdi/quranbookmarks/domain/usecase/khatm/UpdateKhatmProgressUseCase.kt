package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.khatm

import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.KhatmProgressRepository
import javax.inject.Inject

/**
 * Use case to update the last page read in Khatm reading
 * Only called when user marks an ayah as read (manual or audio completion)
 */
class UpdateKhatmProgressUseCase @Inject constructor(
    private val repository: KhatmProgressRepository
) {
    suspend operator fun invoke(pageNumber: Int) {
        require(pageNumber in KhatmProgress.MIN_PAGE..KhatmProgress.MAX_PAGE) {
            "Page number must be between ${KhatmProgress.MIN_PAGE} and ${KhatmProgress.MAX_PAGE}"
        }
        repository.updateLastPage(pageNumber)
    }
}
