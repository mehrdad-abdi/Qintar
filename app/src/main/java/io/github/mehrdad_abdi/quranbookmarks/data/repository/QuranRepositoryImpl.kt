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
                    surahNumber = verseData.surah.number,
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
                        surahNumber = surahData.number,
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
                        surahNumber = surahData.number,
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
                        surahNumber = surahInfo?.number ?: surahNumber,
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
                    // Use surah info from API if available, otherwise fall back to lookup
                    val surahInfo = ayahData.surah
                    val surahNumber = surahInfo?.number ?: getSurahNumberFromGlobalAyah(ayahData.number)

                    VerseMetadata(
                        text = ayahData.text,
                        surahName = surahInfo?.name ?: "",
                        surahNameEn = surahInfo?.englishName ?: "",
                        surahNumber = surahNumber,
                        revelationType = surahInfo?.revelationType ?: "",
                        numberOfAyahs = surahInfo?.numberOfAyahs ?: 0,
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

    /**
     * Get surah number from global ayah number (1-6236)
     * Uses cumulative ayah counts to determine which surah the ayah belongs to
     */
    private fun getSurahNumberFromGlobalAyah(globalAyahNumber: Int): Int {
        return when {
            globalAyahNumber <= 7 -> 1
            globalAyahNumber <= 293 -> 2
            globalAyahNumber <= 493 -> 3
            globalAyahNumber <= 669 -> 4
            globalAyahNumber <= 789 -> 5
            globalAyahNumber <= 954 -> 6
            globalAyahNumber <= 1160 -> 7
            globalAyahNumber <= 1235 -> 8
            globalAyahNumber <= 1364 -> 9
            globalAyahNumber <= 1473 -> 10
            globalAyahNumber <= 1596 -> 11
            globalAyahNumber <= 1707 -> 12
            globalAyahNumber <= 1750 -> 13
            globalAyahNumber <= 1802 -> 14
            globalAyahNumber <= 1901 -> 15
            globalAyahNumber <= 2029 -> 16
            globalAyahNumber <= 2140 -> 17
            globalAyahNumber <= 2250 -> 18
            globalAyahNumber <= 2348 -> 19
            globalAyahNumber <= 2483 -> 20
            globalAyahNumber <= 2595 -> 21
            globalAyahNumber <= 2673 -> 22
            globalAyahNumber <= 2791 -> 23
            globalAyahNumber <= 2855 -> 24
            globalAyahNumber <= 2932 -> 25
            globalAyahNumber <= 3159 -> 26
            globalAyahNumber <= 3252 -> 27
            globalAyahNumber <= 3340 -> 28
            globalAyahNumber <= 3409 -> 29
            globalAyahNumber <= 3469 -> 30
            globalAyahNumber <= 3503 -> 31
            globalAyahNumber <= 3533 -> 32
            globalAyahNumber <= 3606 -> 33
            globalAyahNumber <= 3660 -> 34
            globalAyahNumber <= 3705 -> 35
            globalAyahNumber <= 3788 -> 36
            globalAyahNumber <= 4070 -> 37
            globalAyahNumber <= 4154 -> 38
            globalAyahNumber <= 4229 -> 39
            globalAyahNumber <= 4314 -> 40
            globalAyahNumber <= 4368 -> 41
            globalAyahNumber <= 4421 -> 42
            globalAyahNumber <= 4510 -> 43
            globalAyahNumber <= 4569 -> 44
            globalAyahNumber <= 4606 -> 45
            globalAyahNumber <= 4641 -> 46
            globalAyahNumber <= 4679 -> 47
            globalAyahNumber <= 4708 -> 48
            globalAyahNumber <= 4726 -> 49
            globalAyahNumber <= 4771 -> 50
            globalAyahNumber <= 4831 -> 51
            globalAyahNumber <= 4880 -> 52
            globalAyahNumber <= 4942 -> 53
            globalAyahNumber <= 4997 -> 54
            globalAyahNumber <= 5075 -> 55
            globalAyahNumber <= 5171 -> 56
            globalAyahNumber <= 5200 -> 57
            globalAyahNumber <= 5222 -> 58
            globalAyahNumber <= 5246 -> 59
            globalAyahNumber <= 5259 -> 60
            globalAyahNumber <= 5273 -> 61
            globalAyahNumber <= 5284 -> 62
            globalAyahNumber <= 5295 -> 63
            globalAyahNumber <= 5313 -> 64
            globalAyahNumber <= 5325 -> 65
            globalAyahNumber <= 5337 -> 66
            globalAyahNumber <= 5367 -> 67
            globalAyahNumber <= 5419 -> 68
            globalAyahNumber <= 5471 -> 69
            globalAyahNumber <= 5515 -> 70
            globalAyahNumber <= 5543 -> 71
            globalAyahNumber <= 5571 -> 72
            globalAyahNumber <= 5591 -> 73
            globalAyahNumber <= 5647 -> 74
            globalAyahNumber <= 5687 -> 75
            globalAyahNumber <= 5718 -> 76
            globalAyahNumber <= 5768 -> 77
            globalAyahNumber <= 5808 -> 78
            globalAyahNumber <= 5854 -> 79
            globalAyahNumber <= 5896 -> 80
            globalAyahNumber <= 5925 -> 81
            globalAyahNumber <= 5944 -> 82
            globalAyahNumber <= 5980 -> 83
            globalAyahNumber <= 6005 -> 84
            globalAyahNumber <= 6027 -> 85
            globalAyahNumber <= 6044 -> 86
            globalAyahNumber <= 6063 -> 87
            globalAyahNumber <= 6089 -> 88
            globalAyahNumber <= 6119 -> 89
            globalAyahNumber <= 6139 -> 90
            globalAyahNumber <= 6154 -> 91
            globalAyahNumber <= 6175 -> 92
            globalAyahNumber <= 6186 -> 93
            globalAyahNumber <= 6194 -> 94
            globalAyahNumber <= 6202 -> 95
            globalAyahNumber <= 6221 -> 96
            globalAyahNumber <= 6226 -> 97
            globalAyahNumber <= 6234 -> 98
            globalAyahNumber <= 6242 -> 99
            globalAyahNumber <= 6253 -> 100
            globalAyahNumber <= 6264 -> 101
            globalAyahNumber <= 6272 -> 102
            globalAyahNumber <= 6275 -> 103
            globalAyahNumber <= 6284 -> 104
            globalAyahNumber <= 6289 -> 105
            globalAyahNumber <= 6293 -> 106
            globalAyahNumber <= 6300 -> 107
            globalAyahNumber <= 6303 -> 108
            globalAyahNumber <= 6309 -> 109
            globalAyahNumber <= 6312 -> 110
            globalAyahNumber <= 6317 -> 111
            globalAyahNumber <= 6321 -> 112
            globalAyahNumber <= 6326 -> 113
            globalAyahNumber <= 6236 -> 114
            else -> 114 // Default to last surah if out of range
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