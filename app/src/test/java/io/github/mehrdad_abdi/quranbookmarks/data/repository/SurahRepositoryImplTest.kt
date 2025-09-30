package io.github.mehrdad_abdi.quranbookmarks.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import io.github.mehrdad_abdi.quranbookmarks.data.remote.api.QuranApiService
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.SurahDto
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.SurahListResponseDto
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import retrofit2.Response

class SurahRepositoryImplTest {

    @Mock
    private lateinit var apiService: QuranApiService

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var gson: Gson
    private lateinit var repository: SurahRepositoryImpl

    private val sampleSurahDto = SurahDto(
        number = 1,
        name = "الفاتحة",
        englishName = "Al-Fatihah",
        englishNameTranslation = "The Opening",
        numberOfAyahs = 7,
        revelationType = "Meccan"
    )

    private val sampleResponse = SurahListResponseDto(
        code = 200,
        status = "OK",
        data = listOf(sampleSurahDto)
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        gson = Gson()

        whenever(context.getSharedPreferences("surah_cache", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.apply()).then { }

        repository = SurahRepositoryImpl(apiService, context, gson)
    }

    @Test
    fun `getAllSurahs should return success when API call succeeds`() = runTest {
        // Given
        whenever(sharedPreferences.getString(any(), any())).thenReturn(null)
        whenever(sharedPreferences.getLong(any(), eq(0))).thenReturn(0)
        whenever(apiService.getAllSurahs()).thenReturn(Response.success(sampleResponse))

        // When
        val result = repository.getAllSurahs()

        // Then
        assertTrue(result.isSuccess)
        val surahs = result.getOrNull()
        assertNotNull(surahs)
        assertEquals(1, surahs!!.size)
        assertEquals("Al-Fatihah", surahs[0].englishName)
        assertEquals("الفاتحة", surahs[0].name)
        assertEquals(7, surahs[0].numberOfAyahs)
    }

    @Test
    fun `getAllSurahs should return failure when API call fails`() = runTest {
        // Given
        whenever(sharedPreferences.getString(any(), any())).thenReturn(null)
        whenever(sharedPreferences.getLong(any(), eq(0))).thenReturn(0)
        whenever(apiService.getAllSurahs()).thenReturn(Response.error(404, mock()))

        // When
        val result = repository.getAllSurahs()

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `getAllSurahs should return cached data when cache is valid`() = runTest {
        // Given
        val cachedJson = gson.toJson(listOf(sampleSurahDto))
        val validTimestamp = System.currentTimeMillis() - 1000 // 1 second ago

        whenever(sharedPreferences.getString("cached_surahs", null)).thenReturn(cachedJson)
        whenever(sharedPreferences.getLong("cache_timestamp", 0)).thenReturn(validTimestamp)

        // When
        val result = repository.getAllSurahs()

        // Then
        assertTrue(result.isSuccess)
        val surahs = result.getOrNull()
        assertNotNull(surahs)
        assertEquals(1, surahs!!.size)
        assertEquals("Al-Fatihah", surahs[0].englishName)

        // Verify API was not called
        verify(apiService, never()).getAllSurahs()
    }

    @Test
    fun `getSurahByNumber should return correct surah when exists`() = runTest {
        // Given
        whenever(sharedPreferences.getString(any(), any())).thenReturn(null)
        whenever(sharedPreferences.getLong(any(), eq(0))).thenReturn(0)
        whenever(apiService.getAllSurahs()).thenReturn(Response.success(sampleResponse))

        // When
        val result = repository.getSurahByNumber(1)

        // Then
        assertTrue(result.isSuccess)
        val surah = result.getOrNull()
        assertNotNull(surah)
        assertEquals("Al-Fatihah", surah!!.englishName)
        assertEquals(1, surah.number)
    }

    @Test
    fun `getSurahByNumber should return null when surah does not exist`() = runTest {
        // Given
        whenever(sharedPreferences.getString(any(), any())).thenReturn(null)
        whenever(sharedPreferences.getLong(any(), eq(0))).thenReturn(0)
        whenever(apiService.getAllSurahs()).thenReturn(Response.success(sampleResponse))

        // When
        val result = repository.getSurahByNumber(999)

        // Then
        assertTrue(result.isSuccess)
        val surah = result.getOrNull()
        assertNull(surah)
    }
}