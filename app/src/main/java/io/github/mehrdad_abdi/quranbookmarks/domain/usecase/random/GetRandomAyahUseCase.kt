package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random

import io.github.mehrdad_abdi.quranbookmarks.domain.model.SurahNames
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import javax.inject.Inject
import kotlin.random.Random

/**
 * Use case to get a random ayah from the Quran.
 * Total ayahs in Quran: 6236
 */
class GetRandomAyahUseCase @Inject constructor(
    private val quranRepository: QuranRepository
) {
    companion object {
        private const val TOTAL_AYAHS = 6236
    }

    /**
     * Generate a random surah and ayah, then fetch its metadata.
     * @return Result containing the verse metadata or error
     */
    suspend operator fun invoke(): Result<VerseMetadata> {
        return try {
            // Generate random surah number between 1 and 114
            val randomSurah = Random.nextInt(1, 115)

            // Get the number of ayahs in this surah
            val ayahCount = SurahNames.getAyahCount(randomSurah)

            // Generate random ayah within the surah
            val randomAyah = Random.nextInt(1, ayahCount + 1)

            // Fetch the verse metadata using surah:ayah format
            quranRepository.getVerseMetadata(randomSurah, randomAyah)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
