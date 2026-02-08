package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.CachedContentDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.toVerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toDomain
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toEntity
import io.github.mehrdad_abdi.quranbookmarks.data.remote.api.QuranApiService
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.CachedContent
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepositoryImpl @Inject constructor(
    private val apiService: QuranApiService,
    private val cachedContentDao: CachedContentDao,
    private val offlineVerseRepository: OfflineVerseRepository
) : QuranRepository {

    override suspend fun getVerseMetadata(surahNumber: Int, ayahNumber: Int): Result<VerseMetadata> {
        val verse = offlineVerseRepository.getVerse(surahNumber, ayahNumber)
        return if (verse != null) {
            Result.success(verse.toVerseMetadata())
        } else {
            Result.failure(Exception("Verse not found in offline database: surah=$surahNumber, ayah=$ayahNumber"))
        }
    }

    override suspend fun getSurahMetadata(surahNumber: Int): Result<List<VerseMetadata>> {
        val verses = offlineVerseRepository.getVersesBySurah(surahNumber)
        return if (verses.isNotEmpty()) {
            Result.success(verses.map { it.toVerseMetadata() })
        } else {
            Result.failure(Exception("Surah not found in offline database: $surahNumber"))
        }
    }

    override suspend fun getAyahRangeMetadata(surahNumber: Int, startAyah: Int, endAyah: Int): Result<List<VerseMetadata>> {
        val verses = offlineVerseRepository.getVersesBySurah(surahNumber)
        val filtered = verses.filter { it.ayahInSurah in startAyah..endAyah }
        return if (filtered.isNotEmpty()) {
            Result.success(filtered.map { it.toVerseMetadata() })
        } else {
            Result.failure(Exception("No verses found in range: surah=$surahNumber, ayahs=$startAyah-$endAyah"))
        }
    }

    override suspend fun getPageMetadata(pageNumber: Int): Result<List<VerseMetadata>> {
        val verses = offlineVerseRepository.getVersesByPage(pageNumber)
        return if (verses.isNotEmpty()) {
            Result.success(verses.map { it.toVerseMetadata() })
        } else {
            Result.failure(Exception("No verses found on page: $pageNumber"))
        }
    }

    override suspend fun getAvailableReciters(): Result<List<ReciterData>> {
        return try {
            val response = apiService.getAudioEditions()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.data)
            } else {
                Result.failure(Exception("Failed to fetch reciters: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAyahWithAudio(ayahReference: String, reciterEdition: String): Result<io.github.mehrdad_abdi.quranbookmarks.domain.repository.AyahAudioData> {
        return try {
            val response = apiService.getAyahWithEdition(ayahReference, reciterEdition)
            if (response.isSuccessful && response.body() != null) {
                val ayahData = response.body()!!.data
                Result.success(io.github.mehrdad_abdi.quranbookmarks.domain.repository.AyahAudioData(
                    text = ayahData.text,
                    audio = ayahData.audio
                ))
            } else {
                Result.failure(Exception("Failed to fetch ayah audio: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getImageUrl(surah: Int, ayah: Int, highRes: Boolean): String {
        return QuranApiService.Companion.ImageUrls.getImageUrl(surah, ayah, highRes)
    }

    override fun getAudioUrl(reciter: String, ayahNumber: Int, bitrate: String): String {
        return QuranApiService.Companion.AudioUrls.getAudioUrl(reciter, ayahNumber, bitrate)
    }

    override suspend fun getCachedContent(surah: Int, ayah: Int): CachedContent? {
        return cachedContentDao.getCachedContent(surah, ayah)?.toDomain()
    }

    override suspend fun getCachedContentById(id: String): CachedContent? {
        return cachedContentDao.getCachedContentById(id)?.toDomain()
    }

    override suspend fun saveCachedContent(content: CachedContent) {
        cachedContentDao.insertCachedContent(content.toEntity())
    }

    override suspend fun updateCachedContent(content: CachedContent) {
        cachedContentDao.updateCachedContent(content.toEntity())
    }

    override suspend fun deleteCachedContent(surah: Int, ayah: Int) {
        cachedContentDao.deleteCachedContent(surah, ayah)
    }

    override fun getFullyCachedContent(): Flow<List<CachedContent>> {
        return cachedContentDao.getFullyCachedContent().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFullyCachedContentCount(): Int {
        return cachedContentDao.getFullyCachedContentCount()
    }

    override suspend fun cleanOldCachedContent(daysThreshold: Int) {
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysThreshold.toLong())
        cachedContentDao.deleteOldCachedContent(threshold)
    }
}