package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random

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
     * Generate a random ayah number and fetch its metadata.
     * @return Result containing the verse metadata or error
     */
    suspend operator fun invoke(): Result<VerseMetadata> {
        return try {
            // Generate random ayah number between 1 and 6236
            val randomAyahNumber = Random.nextInt(1, TOTAL_AYAHS + 1)

            // Fetch the verse metadata
            quranRepository.getVerseMetadata(randomAyahNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
