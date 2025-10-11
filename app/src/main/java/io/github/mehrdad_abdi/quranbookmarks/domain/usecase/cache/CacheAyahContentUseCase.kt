package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache

import android.util.Log
import io.github.mehrdad_abdi.quranbookmarks.data.cache.FileDownloadManager
import io.github.mehrdad_abdi.quranbookmarks.domain.model.CachedContent
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case to cache ayah content (text and audio) for offline access
 * This use case:
 * 1. Checks if content is already cached
 * 2. Fetches metadata from API if not cached
 * 3. Downloads image and audio files to local storage
 * 4. Saves local file paths to database
 */
class CacheAyahContentUseCase @Inject constructor(
    private val quranRepository: QuranRepository,
    private val settingsRepository: SettingsRepository,
    private val fileDownloadManager: FileDownloadManager
) {
    companion object {
        private const val TAG = "CacheAyahContentUseCase"
        private const val DEFAULT_RECITER = "ar.alafasy"
    }

    suspend operator fun invoke(
        ayahReference: AyahReference,
        reciterEdition: String? = null,
        forceRefresh: Boolean = false
    ): Result<CachedContent> {
        return try {
            val cacheId = ayahReference.getCacheId()

            // Check if already cached
            if (!forceRefresh) {
                val existingCache = quranRepository.getCachedContentById(cacheId)
                if (existingCache != null) {
                    Log.d(TAG, "Content already cached for $cacheId")
                    return Result.success(existingCache)
                }
            }

            Log.d(TAG, "Caching content for ${ayahReference.surah}:${ayahReference.ayah}")

            // Fetch verse metadata from API using surah:ayah format
            val metadataResult = quranRepository.getVerseMetadata(ayahReference.surah, ayahReference.ayah)
            if (metadataResult.isFailure) {
                return Result.failure(
                    metadataResult.exceptionOrNull()
                        ?: Exception("Failed to fetch verse metadata")
                )
            }

            val metadata = metadataResult.getOrThrow()

            // Get settings for bitrate
            val settings = settingsRepository.getSettings().first()

            // Generate URLs for downloading
            val imageUrl = quranRepository.getImageUrl(
                ayahReference.surah,
                ayahReference.ayah,
                highRes = false
            )

            val audioUrl = if (reciterEdition != null && reciterEdition != "none") {
                quranRepository.getAudioUrl(
                    reciterEdition,
                    ayahReference.globalAyahNumber,
                    settings.reciterBitrate
                )
            } else {
                // Use default reciter and bitrate from settings
                quranRepository.getAudioUrl(
                    settings.reciterEdition,
                    ayahReference.globalAyahNumber,
                    settings.reciterBitrate
                )
            }

            // Download image file to local storage
            Log.d(TAG, "Downloading image for ${ayahReference.surah}:${ayahReference.ayah}")
            val localImagePath = fileDownloadManager.downloadImageFile(
                imageUrl,
                ayahReference.surah,
                ayahReference.ayah
            )

            if (localImagePath == null) {
                Log.w(TAG, "Failed to download image for ${ayahReference.surah}:${ayahReference.ayah}")
            }

            // Download audio file to local storage (only if reciter is specified)
            var localAudioPath: String? = null
            if (reciterEdition != null && reciterEdition != "none") {
                Log.d(TAG, "Downloading audio for ${ayahReference.surah}:${ayahReference.ayah}")
                localAudioPath = fileDownloadManager.downloadAudioFile(
                    audioUrl,
                    ayahReference.surah,
                    ayahReference.ayah
                )

                if (localAudioPath == null) {
                    Log.w(TAG, "Failed to download audio for ${ayahReference.surah}:${ayahReference.ayah}")
                }
            }

            // Create cached content entry with local file paths
            val cachedContent = CachedContent(
                id = cacheId,
                surah = ayahReference.surah,
                ayah = ayahReference.ayah,
                imagePath = localImagePath,  // Local file path
                audioPath = localAudioPath,  // Local file path
                metadata = metadata,
                downloadedAt = System.currentTimeMillis()
            )

            // Save to database
            quranRepository.saveCachedContent(cachedContent)

            Log.d(TAG, "Successfully cached content for $cacheId")
            Result.success(cachedContent)

        } catch (e: Exception) {
            Log.e(TAG, "Error caching ayah content", e)
            Result.failure(e)
        }
    }

    /**
     * Cache multiple ayahs with deduplication
     * Returns list of successfully cached ayahs
     */
    suspend fun cacheMultiple(
        ayahs: List<AyahReference>,
        reciterEdition: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<CachedContent>> {
        return try {
            // Deduplicate based on cache ID
            val uniqueAyahs = ayahs.distinctBy { it.getCacheId() }

            Log.d(TAG, "Caching ${uniqueAyahs.size} unique ayahs (${ayahs.size} total requested)")

            val cachedContents = mutableListOf<CachedContent>()
            val failures = mutableListOf<String>()

            for (ayah in uniqueAyahs) {
                val result = invoke(ayah, reciterEdition, forceRefresh)
                if (result.isSuccess) {
                    cachedContents.add(result.getOrThrow())
                } else {
                    failures.add("${ayah.surah}:${ayah.ayah}")
                    Log.w(TAG, "Failed to cache ${ayah.surah}:${ayah.ayah}: ${result.exceptionOrNull()?.message}")
                }
            }

            if (failures.isNotEmpty()) {
                Log.w(TAG, "Some ayahs failed to cache: ${failures.joinToString(", ")}")
            }

            Log.d(TAG, "Successfully cached ${cachedContents.size}/${uniqueAyahs.size} ayahs")
            Result.success(cachedContents)

        } catch (e: Exception) {
            Log.e(TAG, "Error caching multiple ayahs", e)
            Result.failure(e)
        }
    }
}