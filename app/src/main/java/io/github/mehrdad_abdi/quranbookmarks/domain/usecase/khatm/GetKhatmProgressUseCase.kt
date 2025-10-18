package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.khatm

import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.KhatmProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get the current Khatm reading progress
 */
class GetKhatmProgressUseCase @Inject constructor(
    private val repository: KhatmProgressRepository
) {
    operator fun invoke(): Flow<KhatmProgress> {
        return repository.getProgress()
    }

    suspend fun getSnapshot(): KhatmProgress {
        return repository.getProgressSnapshot()
    }
}
