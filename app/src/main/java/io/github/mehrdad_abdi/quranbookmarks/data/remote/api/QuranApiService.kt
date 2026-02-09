package io.github.mehrdad_abdi.quranbookmarks.data.remote.api

import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterResponse
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.VerseResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface QuranApiService {
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