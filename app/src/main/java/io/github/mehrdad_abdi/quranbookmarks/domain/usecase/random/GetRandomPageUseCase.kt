package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random

import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import javax.inject.Inject
import kotlin.random.Random

/**
 * Use case to get a random page from the Quran.
 * Total pages in standard Mushaf: 604
 */
class GetRandomPageUseCase @Inject constructor(
    private val quranRepository: QuranRepository
) {
    companion object {
        private const val TOTAL_PAGES = 604
    }

    /**
     * Generate a random page number and fetch its verses.
     * @return Result containing list of verse metadata for the page, or error
     */
    suspend operator fun invoke(): Result<List<VerseMetadata>> {
        return try {
            // Generate random page number between 1 and 604
            val randomPageNumber = Random.nextInt(1, TOTAL_PAGES + 1)

            // Fetch the page metadata
            quranRepository.getPageMetadata(randomPageNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
