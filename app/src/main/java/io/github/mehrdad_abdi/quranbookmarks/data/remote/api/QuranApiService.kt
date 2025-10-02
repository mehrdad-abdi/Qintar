package io.github.mehrdad_abdi.quranbookmarks.data.remote.api

import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface QuranApiService {
    @GET("v1/surah")
    suspend fun getAllSurahs(): Response<SurahListResponseDto>

    @GET("v1/ayah/{ayahNumber}")
    suspend fun getAyah(@Path("ayahNumber") ayahNumber: Int): Response<VerseResponse>

    @GET("v1/surah/{surahNumber}")
    suspend fun getSurah(@Path("surahNumber") surahNumber: Int): Response<SurahResponse>

    @GET("v1/surah/{surahNumber}")
    suspend fun getAyahRange(
        @Path("surahNumber") surahNumber: Int,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): Response<SurahResponse>

    @GET("v1/page/{pageNumber}")
    suspend fun getPage(@Path("pageNumber") pageNumber: Int): Response<PageResponse>

    @GET("v1/edition/format/audio")
    suspend fun getAudioEditions(): Response<ReciterResponse>

    @GET("v1/ayah/{ayahReference}/{edition}")
    suspend fun getAyahWithEdition(
        @Path("ayahReference") ayahReference: String,
        @Path("edition") edition: String
    ): Response<VerseResponse>

    companion object {
        const val BASE_URL = "https://api.alquran.cloud/"

        object ImageUrls {
            fun getImageUrl(surah: Int, ayah: Int, highRes: Boolean = false): String {
                val resolution = if (highRes) "high" else "medium"
                return "https://api.quran.com/api/v4/quran/images/${resolution}/${surah}/${ayah}"
            }
        }

        object AudioUrls {
            fun getAudioUrl(reciter: String, ayahNumber: Int, bitrate: String = "128"): String {
                // Use alquran.cloud CDN for direct MP3 files
                // Format: https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{number}.mp3
                return "https://cdn.islamic.network/quran/audio/${bitrate}/${reciter}/${ayahNumber}.mp3"
            }
        }
    }
}