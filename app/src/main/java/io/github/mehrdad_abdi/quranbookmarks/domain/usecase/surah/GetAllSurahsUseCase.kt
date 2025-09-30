package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.surah

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SurahRepository
import javax.inject.Inject

class GetAllSurahsUseCase @Inject constructor(
    private val repository: SurahRepository
) {
    suspend operator fun invoke(): Result<List<Surah>> {
        return repository.getAllSurahs()
    }
}