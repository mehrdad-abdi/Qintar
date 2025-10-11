package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading

import android.util.Log
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.GetAyahsFromBookmarkUseCase
import javax.inject.Inject

class GetBookmarkContentUseCase @Inject constructor(
    private val quranRepository: QuranRepository,
    private val getAyahsFromBookmarkUseCase: GetAyahsFromBookmarkUseCase
) {
    companion object {
        private const val TAG = "GetBookmarkContent"
    }

    suspend operator fun invoke(bookmark: Bookmark, preferCache: Boolean = true): Result<List<VerseMetadata>> {
        return try {
            // If preferCache is true, try to get from cache first
            if (preferCache) {
                val cachedResult = tryGetFromCache(bookmark)
                if (cachedResult != null) {
                    Log.d(TAG, "Returning cached content for bookmark ${bookmark.id}")
                    return Result.success(cachedResult)
                }
                Log.d(TAG, "Cache miss for bookmark ${bookmark.id}, fetching from API")
            }

            // Fall back to API if cache is not available or not preferred
            when (bookmark.type) {
                BookmarkType.AYAH -> {
                    // Use surah:ayah format directly
                    val result = quranRepository.getVerseMetadata(bookmark.startSurah, bookmark.startAyah)
                    if (result.isSuccess) {
                        Result.success(listOf(result.getOrThrow()))
                    } else {
                        result.map { emptyList() }
                    }
                }
                BookmarkType.RANGE -> {
                    quranRepository.getAyahRangeMetadata(
                        bookmark.startSurah,
                        bookmark.startAyah,
                        bookmark.endAyah
                    )
                }
                BookmarkType.SURAH -> {
                    quranRepository.getSurahMetadata(bookmark.startSurah)
                }
                BookmarkType.PAGE -> {
                    quranRepository.getPageMetadata(bookmark.startAyah) // startAyah contains page number for PAGE type
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Try to get bookmark content from cache
     * Returns null if any ayah is not cached
     */
    private suspend fun tryGetFromCache(bookmark: Bookmark): List<VerseMetadata>? {
        return try {
            // Get all ayahs for this bookmark
            val ayahs = getAyahsFromBookmarkUseCase(bookmark)
            if (ayahs.isEmpty()) {
                return null
            }

            val cachedMetadata = mutableListOf<VerseMetadata>()

            // Check if all ayahs are cached
            for (ayah in ayahs) {
                val cached = quranRepository.getCachedContent(ayah.surah, ayah.ayah)
                if (cached?.metadata == null) {
                    // If any ayah is not cached, return null to trigger API fetch
                    Log.d(TAG, "Ayah ${ayah.surah}:${ayah.ayah} not cached")
                    return null
                }

                // Include cached file paths in metadata
                val metadataWithCache = cached.metadata.copy(
                    cachedAudioPath = cached.audioPath,
                    cachedImagePath = cached.imagePath
                )
                cachedMetadata.add(metadataWithCache)
            }

            Log.d(TAG, "All ${ayahs.size} ayahs found in cache with file paths")
            cachedMetadata

        } catch (e: Exception) {
            Log.e(TAG, "Error getting from cache", e)
            null
        }
    }
}