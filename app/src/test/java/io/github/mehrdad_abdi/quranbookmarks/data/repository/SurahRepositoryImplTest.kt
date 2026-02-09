package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.entities.SurahEntity
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class SurahRepositoryImplTest {

    @Mock
    private lateinit var offlineVerseRepository: OfflineVerseRepository

    private lateinit var repository: SurahRepositoryImpl

    private val sampleSurahEntity = SurahEntity(
        surahNumber = 1,
        surahName = "الفاتحة",
        surahNameEn = "Al-Fatihah",
        revelationType = "Meccan",
        numberOfAyahs = 7
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = SurahRepositoryImpl(offlineVerseRepository)
    }

    @Test
    fun `getAllSurahs should return success when DB query succeeds`() = runTest {
        // Given
        whenever(offlineVerseRepository.getAllSurahs()).thenReturn(listOf(sampleSurahEntity))

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
    fun `getAllSurahs should return failure when DB query fails`() = runTest {
        // Given
        doAnswer { throw RuntimeException("Database error") }.whenever(offlineVerseRepository).getAllSurahs()

        // When
        val result = repository.getAllSurahs()

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `getSurahByNumber should return correct surah when exists`() = runTest {
        // Given
        whenever(offlineVerseRepository.getSurah(1)).thenReturn(sampleSurahEntity)

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
    fun `getSurahByNumber should return failure when surah not found`() = runTest {
        // Given
        whenever(offlineVerseRepository.getSurah(999)).thenReturn(null)

        // When
        val result = repository.getSurahByNumber(999)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `getSurahByNumber should return failure when DB query fails`() = runTest {
        // Given
        doAnswer { throw RuntimeException("Database error") }.whenever(offlineVerseRepository).getSurah(1)

        // When
        val result = repository.getSurahByNumber(1)

        // Then
        assertTrue(result.isFailure)
    }
}
