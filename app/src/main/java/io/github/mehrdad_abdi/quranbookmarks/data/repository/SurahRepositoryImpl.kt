package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.mapper.mapToDomain
import io.github.mehrdad_abdi.quranbookmarks.data.mapper.toDomain
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SurahRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SurahRepositoryImpl @Inject constructor(
    private val offlineVerseRepository: OfflineVerseRepository
) : SurahRepository {

    override suspend fun getAllSurahs(): Result<List<Surah>> {
        return try {
            val surahs = offlineVerseRepository.getAllSurahs()
            Result.success(surahs.mapToDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurahByNumber(number: Int): Result<Surah?> {
        return try {
            val surah = offlineVerseRepository.getSurah(number)
            if (surah != null) {
                Result.success(surah.toDomain())
            } else {
                Result.failure(Exception("Surah $number not found in offline database"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}