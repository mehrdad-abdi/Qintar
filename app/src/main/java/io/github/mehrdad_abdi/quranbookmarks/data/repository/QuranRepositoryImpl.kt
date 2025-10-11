package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.CachedContentDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toDomain
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toEntity
import io.github.mehrdad_abdi.quranbookmarks.data.remote.api.QuranApiService
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.SurahData
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
    private val cachedContentDao: CachedContentDao
) : QuranRepository {

    override suspend fun getVerseMetadata(surahNumber: Int, ayahNumber: Int): Result<VerseMetadata> {
        return try {
            // Use surah:ayah format for the API call
            val ayahReference = "$surahNumber:$ayahNumber"
            val response = apiService.getAyahWithEdition(ayahReference, "quran-uthmani-quran-academy")
            if (response.isSuccessful && response.body() != null) {
                val verseData = response.body()!!.data
                val metadata = VerseMetadata(
                    text = verseData.text,
                    surahName = verseData.surah.name,
                    surahNameEn = verseData.surah.englishName,
                    surahNumber = verseData.surah.number,
                    revelationType = verseData.surah.revelationType,
                    numberOfAyahs = verseData.surah.numberOfAyahs,
                    hizbQuarter = verseData.hizbQuarter,
                    rukuNumber = verseData.ruku,
                    page = verseData.page,
                    manzil = verseData.manzil,
                    sajda = verseData.hasSajda(),
                    globalAyahNumber = verseData.number,
                    ayahInSurah = verseData.numberInSurah
                )
                Result.success(metadata)
            } else {
                Result.failure(Exception("Failed to fetch verse metadata: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurahMetadata(surahNumber: Int): Result<List<VerseMetadata>> {
        return try {
            val response = apiService.getSurah(surahNumber)
            if (response.isSuccessful && response.body() != null) {
                val surahData = response.body()!!.data
                val metadataList = surahData.ayahs.map { ayahData ->
                    VerseMetadata(
                        text = ayahData.text,
                        surahName = surahData.name,
                        surahNameEn = surahData.englishName,
                        surahNumber = surahData.number,
                        revelationType = surahData.revelationType,
                        numberOfAyahs = surahData.numberOfAyahs,
                        hizbQuarter = ayahData.hizbQuarter,
                        rukuNumber = ayahData.ruku,
                        page = ayahData.page,
                        manzil = ayahData.manzil,
                        sajda = ayahData.hasSajda(),
                        globalAyahNumber = ayahData.number,
                        ayahInSurah = ayahData.numberInSurah
                    )
                }
                Result.success(metadataList)
            } else {
                Result.failure(Exception("Failed to fetch surah metadata: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAyahRangeMetadata(surahNumber: Int, startAyah: Int, endAyah: Int): Result<List<VerseMetadata>> {
        return try {
            // Use the correct API format: offset and limit
            // offset is 0-based, so startAyah-1 gives us the correct offset
            // limit is how many ayahs we want (endAyah - startAyah + 1)
            val offset = startAyah - 1
            val limit = endAyah - startAyah + 1

            val response = apiService.getAyahRange(surahNumber, offset, limit)
            if (response.isSuccessful && response.body() != null) {
                val surahData = response.body()!!.data
                val metadataList = surahData.ayahs.map { ayahData ->
                    VerseMetadata(
                        text = ayahData.text,
                        surahName = surahData.name,
                        surahNameEn = surahData.englishName,
                        surahNumber = surahData.number,
                        revelationType = surahData.revelationType,
                        numberOfAyahs = surahData.numberOfAyahs,
                        hizbQuarter = ayahData.hizbQuarter,
                        rukuNumber = ayahData.ruku,
                        page = ayahData.page,
                        manzil = ayahData.manzil,
                        sajda = ayahData.hasSajda(),
                        globalAyahNumber = ayahData.number,
                        ayahInSurah = ayahData.numberInSurah
                    )
                }
                return Result.success(metadataList)
            }

            // Fallback failed, return error
            Result.failure(Exception("Failed to fetch ayah range $surahNumber:$startAyah-$endAyah"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPageMetadata(pageNumber: Int): Result<List<VerseMetadata>> {
        return try {
            val response = apiService.getPage(pageNumber)
            if (response.isSuccessful && response.body() != null) {
                val pageData = response.body()!!.data
                val metadataList = pageData.ayahs.map { ayahData ->
                    // The API provides surah info for each ayah in page responses
                    val surahInfo = ayahData.surah

                    VerseMetadata(
                        text = ayahData.text,
                        surahName = surahInfo?.name ?: "",
                        surahNameEn = surahInfo?.englishName ?: "",
                        surahNumber = surahInfo?.number ?: 0,
                        revelationType = surahInfo?.revelationType ?: "",
                        numberOfAyahs = surahInfo?.numberOfAyahs ?: 0,
                        hizbQuarter = ayahData.hizbQuarter,
                        rukuNumber = ayahData.ruku,
                        page = ayahData.page,
                        manzil = ayahData.manzil,
                        sajda = ayahData.hasSajda(),
                        globalAyahNumber = ayahData.number,
                        ayahInSurah = ayahData.numberInSurah
                    )
                }
                Result.success(metadataList)
            } else {
                Result.failure(Exception("Failed to fetch page metadata: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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