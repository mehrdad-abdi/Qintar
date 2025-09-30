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

    override suspend fun getVerseMetadata(ayahNumber: Int): Result<VerseMetadata> {
        return try {
            val response = apiService.getAyah(ayahNumber)
            if (response.isSuccessful && response.body() != null) {
                val verseData = response.body()!!.data
                val metadata = VerseMetadata(
                    text = verseData.text,
                    surahName = verseData.surah.name,
                    surahNameEn = verseData.surah.englishName,
                    revelationType = verseData.surah.revelationType,
                    numberOfAyahs = verseData.surah.numberOfAyahs,
                    hizbQuarter = verseData.hizbQuarter,
                    rukuNumber = verseData.ruku,
                    page = verseData.page,
                    manzil = verseData.manzil,
                    sajda = verseData.sajda,
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
                        revelationType = surahData.revelationType,
                        numberOfAyahs = surahData.numberOfAyahs,
                        hizbQuarter = ayahData.hizbQuarter,
                        rukuNumber = ayahData.ruku,
                        page = ayahData.page,
                        manzil = ayahData.manzil,
                        sajda = ayahData.sajda,
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
                        revelationType = surahData.revelationType,
                        numberOfAyahs = surahData.numberOfAyahs,
                        hizbQuarter = ayahData.hizbQuarter,
                        rukuNumber = ayahData.ruku,
                        page = ayahData.page,
                        manzil = ayahData.manzil,
                        sajda = ayahData.sajda,
                        globalAyahNumber = ayahData.number,
                        ayahInSurah = ayahData.numberInSurah
                    )
                }
                return Result.success(metadataList)
            }

            // Fallback: Get individual ayahs and combine them
            val metadataList = mutableListOf<VerseMetadata>()
            var surahInfo: SurahData? = null

            for (ayah in startAyah..endAyah) {
                // Calculate global ayah number for this surah:ayah
                val globalAyahNumber = calculateGlobalAyahNumber(surahNumber, ayah)
                val ayahResponse = apiService.getAyah(globalAyahNumber)

                if (ayahResponse.isSuccessful && ayahResponse.body() != null) {
                    val ayahData = ayahResponse.body()!!.data

                    // Get surah info from first ayah if we don't have it yet
                    if (surahInfo == null) {
                        val surahResponse = apiService.getSurah(surahNumber)
                        surahInfo = if (surahResponse.isSuccessful && surahResponse.body() != null) {
                            val surahDetailData = surahResponse.body()!!.data
                            SurahData(
                                number = surahDetailData.number,
                                name = surahDetailData.name,
                                englishName = surahDetailData.englishName,
                                englishNameTranslation = surahDetailData.englishNameTranslation,
                                numberOfAyahs = surahDetailData.numberOfAyahs,
                                revelationType = surahDetailData.revelationType
                            )
                        } else {
                            // Fallback surah info
                            SurahData(
                                number = surahNumber,
                                name = "Unknown",
                                englishName = "Unknown",
                                englishNameTranslation = "Unknown",
                                numberOfAyahs = 0,
                                revelationType = "Unknown"
                            )
                        }
                    }

                    val verseMetadata = VerseMetadata(
                        text = ayahData.text,
                        surahName = surahInfo?.name ?: "Unknown",
                        surahNameEn = surahInfo?.englishName ?: "Unknown",
                        revelationType = surahInfo?.revelationType ?: "Unknown",
                        numberOfAyahs = surahInfo?.numberOfAyahs ?: 0,
                        hizbQuarter = ayahData.hizbQuarter,
                        rukuNumber = ayahData.ruku,
                        page = ayahData.page,
                        manzil = ayahData.manzil,
                        sajda = ayahData.sajda,
                        globalAyahNumber = ayahData.number,
                        ayahInSurah = ayahData.numberInSurah
                    )
                    metadataList.add(verseMetadata)
                }
            }

            if (metadataList.isNotEmpty()) {
                Result.success(metadataList)
            } else {
                Result.failure(Exception("Failed to fetch any ayahs in range $surahNumber:$startAyah-$endAyah"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper function to calculate global ayah number (same as in GetBookmarkContentUseCase)
    private fun calculateGlobalAyahNumber(surah: Int, ayah: Int): Int {
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
            36 -> 3705      // Ya-Sin starts after Fatir: 3660 + 45 = 3705
            37 -> 3788      // As-Saffat starts after Ya-Sin: 3705 + 83 = 3788
            else -> {
                // For surahs beyond 37, continue the pattern or use API
                3788 + (surah - 37) * 50 // Rough estimation for remaining surahs
            }
        }
        return ayahsBeforeSurah + ayah
    }

    override suspend fun getPageMetadata(pageNumber: Int): Result<List<VerseMetadata>> {
        return try {
            val response = apiService.getPage(pageNumber)
            if (response.isSuccessful && response.body() != null) {
                val pageData = response.body()!!.data
                val metadataList = pageData.ayahs.map { ayahData ->
                    VerseMetadata(
                        text = ayahData.text,
                        surahName = "", // Page doesn't have surah name directly
                        surahNameEn = "", // Page doesn't have surah name directly
                        revelationType = "", // Page doesn't have revelation type
                        numberOfAyahs = 0, // Page doesn't have total ayahs count
                        hizbQuarter = ayahData.hizbQuarter,
                        rukuNumber = ayahData.ruku,
                        page = ayahData.page,
                        manzil = ayahData.manzil,
                        sajda = ayahData.sajda,
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