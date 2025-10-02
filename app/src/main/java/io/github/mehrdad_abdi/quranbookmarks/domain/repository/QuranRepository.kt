package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.CachedContent
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import kotlinx.coroutines.flow.Flow

data class AyahAudioData(
    val text: String,
    val audio: String?
)

interface QuranRepository {

    // API operations
    suspend fun getVerseMetadata(ayahNumber: Int): Result<VerseMetadata>
    suspend fun getSurahMetadata(surahNumber: Int): Result<List<VerseMetadata>>
    suspend fun getAyahRangeMetadata(surahNumber: Int, startAyah: Int, endAyah: Int): Result<List<VerseMetadata>>
    suspend fun getPageMetadata(pageNumber: Int): Result<List<VerseMetadata>>
    suspend fun getAvailableReciters(): Result<List<ReciterData>>
    suspend fun getAyahWithAudio(ayahReference: String, reciterEdition: String): Result<AyahAudioData>

    // Content URLs
    fun getImageUrl(surah: Int, ayah: Int, highRes: Boolean = false): String
    fun getAudioUrl(reciter: String, ayahNumber: Int, bitrate: String = "128"): String

    // Cached content operations
    suspend fun getCachedContent(surah: Int, ayah: Int): CachedContent?
    suspend fun getCachedContentById(id: String): CachedContent?
    suspend fun saveCachedContent(content: CachedContent)
    suspend fun updateCachedContent(content: CachedContent)
    suspend fun deleteCachedContent(surah: Int, ayah: Int)
    fun getFullyCachedContent(): Flow<List<CachedContent>>
    suspend fun getFullyCachedContentCount(): Int
    suspend fun cleanOldCachedContent(daysThreshold: Int = 30)
}