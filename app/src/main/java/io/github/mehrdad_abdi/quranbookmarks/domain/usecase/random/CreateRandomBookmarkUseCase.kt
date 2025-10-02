package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import javax.inject.Inject
import kotlin.random.Random

/**
 * Use case to create a temporary in-memory bookmark for random reading.
 * These bookmarks are not persisted to database.
 */
class CreateRandomBookmarkUseCase @Inject constructor(
    private val getRandomAyahUseCase: GetRandomAyahUseCase,
    private val getRandomPageUseCase: GetRandomPageUseCase
) {
    companion object {
        private const val RANDOM_BOOKMARK_ID = -1L // Negative ID indicates temporary bookmark
    }

    /**
     * Create a random ayah bookmark.
     */
    suspend fun createRandomAyah(): Result<Bookmark> {
        return try {
            val result = getRandomAyahUseCase()
            if (result.isSuccess) {
                val verse = result.getOrThrow()
                Result.success(
                    Bookmark(
                        id = RANDOM_BOOKMARK_ID,
                        type = BookmarkType.AYAH,
                        startSurah = verse.surahNumber,
                        startAyah = verse.ayahInSurah,
                        description = "Random Ayah"
                    )
                )
            } else {
                result.exceptionOrNull()?.let { Result.failure(it) }
                    ?: Result.failure(Exception("Failed to get random ayah"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a random page bookmark.
     */
    suspend fun createRandomPage(): Result<Bookmark> {
        return try {
            val result = getRandomPageUseCase()
            if (result.isSuccess) {
                val verses = result.getOrThrow()
                if (verses.isNotEmpty()) {
                    val firstVerse = verses.first()
                    Result.success(
                        Bookmark(
                            id = RANDOM_BOOKMARK_ID,
                            type = BookmarkType.PAGE,
                            startSurah = firstVerse.surahNumber,
                            startAyah = firstVerse.page, // For PAGE type, startAyah holds the page number
                            description = "Random Page ${firstVerse.page}"
                        )
                    )
                } else {
                    Result.failure(Exception("Random page returned no verses"))
                }
            } else {
                result.exceptionOrNull()?.let { Result.failure(it) }
                    ?: Result.failure(Exception("Failed to get random page"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
