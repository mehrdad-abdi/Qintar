package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reciter

import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import javax.inject.Inject

class GetAvailableRecitersUseCase @Inject constructor(
    private val repository: QuranRepository
) {
    suspend operator fun invoke(): Result<List<ReciterData>> {
        return repository.getAvailableReciters()
    }
}