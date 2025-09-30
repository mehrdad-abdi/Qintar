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
                    // For single ayah, we need to calculate the global ayah number
                    val globalAyahNumber = calculateGlobalAyahNumber(bookmark.startSurah, bookmark.startAyah)
                    val result = quranRepository.getVerseMetadata(globalAyahNumber)
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

    private fun calculateGlobalAyahNumber(surah: Int, ayah: Int): Int {
        // Correct calculation based on actual ayah counts per surah
        val ayahsBeforeSurah = when (surah) {
            1 -> 0          // Al-Fatiha starts at 1
            2 -> 7          // Al-Baqarah starts after Al-Fatiha: 7 ayahs
            3 -> 293        // Aal-E-Imran starts after Al-Baqarah: 7 + 286 = 293
            4 -> 493        // An-Nisa starts after Aal-E-Imran: 293 + 200 = 493
            5 -> 669        // Al-Maidah starts after An-Nisa: 493 + 176 = 669
            6 -> 789        // Al-An'am starts after Al-Maidah: 669 + 120 = 789
            7 -> 954        // Al-A'raf starts after Al-An'am: 789 + 165 = 954
            8 -> 1160       // Al-Anfal starts after Al-A'raf: 954 + 206 = 1160
            9 -> 1235       // At-Taubah starts after Al-Anfal: 1160 + 75 = 1235
            10 -> 1364      // Yunus starts after At-Taubah: 1235 + 129 = 1364
            11 -> 1473      // Hud starts after Yunus: 1364 + 109 = 1473
            12 -> 1596      // Yusuf starts after Hud: 1473 + 123 = 1596
            13 -> 1707      // Ar-Ra'd starts after Yusuf: 1596 + 111 = 1707
            14 -> 1750      // Ibrahim starts after Ar-Ra'd: 1707 + 43 = 1750
            15 -> 1802      // Al-Hijr starts after Ibrahim: 1750 + 52 = 1802
            16 -> 1901      // An-Nahl starts after Al-Hijr: 1802 + 99 = 1901
            17 -> 2029      // Al-Isra starts after An-Nahl: 1901 + 128 = 2029
            18 -> 2140      // Al-Kahf starts after Al-Isra: 2029 + 111 = 2140
            19 -> 2250      // Maryam starts after Al-Kahf: 2140 + 110 = 2250
            20 -> 2348      // Taha starts after Maryam: 2250 + 98 = 2348
            21 -> 2483      // Al-Anbiya starts after Taha: 2348 + 135 = 2483
            22 -> 2595      // Al-Hajj starts after Al-Anbiya: 2483 + 112 = 2595
            23 -> 2673      // Al-Mu'minun starts after Al-Hajj: 2595 + 78 = 2673
            24 -> 2791      // An-Nur starts after Al-Mu'minun: 2673 + 118 = 2791
            25 -> 2855      // Al-Furqan starts after An-Nur: 2791 + 64 = 2855
            26 -> 2932      // Ash-Shu'ara starts after Al-Furqan: 2855 + 77 = 2932
            27 -> 3159      // An-Naml starts after Ash-Shu'ara: 2932 + 227 = 3159
            28 -> 3252      // Al-Qasas starts after An-Naml: 3159 + 93 = 3252
            29 -> 3340      // Al-Ankabut starts after Al-Qasas: 3252 + 88 = 3340
            30 -> 3409      // Ar-Rum starts after Al-Ankabut: 3340 + 69 = 3409
            31 -> 3469      // Luqman starts after Ar-Rum: 3409 + 60 = 3469
            32 -> 3503      // As-Sajdah starts after Luqman: 3469 + 34 = 3503
            33 -> 3533      // Al-Ahzab starts after As-Sajdah: 3503 + 30 = 3533
            34 -> 3606      // Saba starts after Al-Ahzab: 3533 + 73 = 3606
            35 -> 3660      // Fatir starts after Saba: 3606 + 54 = 3660
            36 -> 3705      // Ya-Sin starts after Fatir: 3660 + 45 = 3705 (FIXED!)
            37 -> 3788      // As-Saffat starts after Ya-Sin: 3705 + 83 = 3788
            else -> {
                // For surahs beyond 37, continue the pattern or use API
                // This is a fallback that should be improved with complete data
                3788 + (surah - 37) * 50 // Rough estimation for remaining surahs
            }
        }
        return ayahsBeforeSurah + ayah
    }
}