package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.random

import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

class GetRandomAyahUseCaseTest {

    @Mock
    private lateinit var quranRepository: QuranRepository

    private lateinit var getRandomAyahUseCase: GetRandomAyahUseCase

    private val testVerse = VerseMetadata(
        text = "Test ayah",
        surahName = "Al-Fatiha",
        surahNameEn = "The Opening",
        surahNumber = 1,
        revelationType = "Meccan",
        numberOfAyahs = 7,
        hizbQuarter = 1,
        rukuNumber = 1,
        page = 1,
        manzil = 1,
        sajda = false,
        globalAyahNumber = 1,
        ayahInSurah = 1
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getRandomAyahUseCase = GetRandomAyahUseCase(quranRepository)
    }

    @Test
    fun `invoke returns success with verse metadata`() = runTest {
        // Given
        `when`(quranRepository.getVerseMetadata(any(), any())).thenReturn(Result.success(testVerse))

        // When
        val result = getRandomAyahUseCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testVerse, result.getOrNull())
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Given
        val error = Exception("Network error")
        `when`(quranRepository.getVerseMetadata(any(), any())).thenReturn(Result.failure(error))

        // When
        val result = getRandomAyahUseCase()

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke generates ayah in valid range`() = runTest {
        // Given
        var capturedSurah = 0
        var capturedAyah = 0
        `when`(quranRepository.getVerseMetadata(any(), any())).thenAnswer { invocation ->
            capturedSurah = invocation.getArgument(0)
            capturedAyah = invocation.getArgument(1)
            Result.success(testVerse.copy(surahNumber = capturedSurah, ayahInSurah = capturedAyah))
        }

        // When
        repeat(100) {
            getRandomAyahUseCase()

            // Then - Check that surah and ayah are in valid ranges
            assertTrue("Surah number $capturedSurah is out of range", capturedSurah in 1..114)
            assertTrue("Ayah number $capturedAyah is out of range", capturedAyah >= 1)
        }
    }
}
