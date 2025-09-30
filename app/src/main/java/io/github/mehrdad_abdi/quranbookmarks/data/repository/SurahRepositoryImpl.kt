package io.github.mehrdad_abdi.quranbookmarks.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mehrdad_abdi.quranbookmarks.data.mapper.toDomain
import io.github.mehrdad_abdi.quranbookmarks.data.remote.api.QuranApiService
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.SurahDto
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SurahRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SurahRepositoryImpl @Inject constructor(
    private val apiService: QuranApiService,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : SurahRepository {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("surah_cache", Context.MODE_PRIVATE)

    private var cachedSurahs: List<Surah>? = null

    companion object {
        private const val SURAHS_CACHE_KEY = "cached_surahs"
        private const val CACHE_TIMESTAMP_KEY = "cache_timestamp"
        private const val CACHE_VALIDITY_DURATION = 7 * 24 * 60 * 60 * 1000L // 7 days
    }

    override suspend fun getAllSurahs(): Result<List<Surah>> = withContext(Dispatchers.IO) {
        try {
            // Return cached data if available and valid
            cachedSurahs?.let { return@withContext Result.success(it) }

            // Check SharedPreferences cache
            val cachedJson = sharedPreferences.getString(SURAHS_CACHE_KEY, null)
            val cacheTimestamp = sharedPreferences.getLong(CACHE_TIMESTAMP_KEY, 0)

            if (cachedJson != null && isCacheValid(cacheTimestamp)) {
                val type = object : TypeToken<List<SurahDto>>() {}.type
                val cachedDtos: List<SurahDto> = gson.fromJson(cachedJson, type)
                val surahs = cachedDtos.toDomain()
                cachedSurahs = surahs
                return@withContext Result.success(surahs)
            }

            // Fetch from API
            val response = apiService.getAllSurahs()
            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                val surahs = responseBody.data.toDomain()

                // Cache the data
                cacheSurahs(responseBody.data)
                cachedSurahs = surahs

                Result.success(surahs)
            } else {
                Result.failure(Exception("Failed to fetch surahs: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurahByNumber(number: Int): Result<Surah?> {
        return try {
            val allSurahs = getAllSurahs()
            if (allSurahs.isSuccess) {
                val surah = allSurahs.getOrNull()?.find { it.number == number }
                Result.success(surah)
            } else {
                Result.failure(allSurahs.exceptionOrNull() ?: Exception("Failed to get surahs"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isCacheValid(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < CACHE_VALIDITY_DURATION
    }

    private fun cacheSurahs(surahs: List<SurahDto>) {
        val json = gson.toJson(surahs)
        sharedPreferences.edit()
            .putString(SURAHS_CACHE_KEY, json)
            .putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
            .apply()
    }
}