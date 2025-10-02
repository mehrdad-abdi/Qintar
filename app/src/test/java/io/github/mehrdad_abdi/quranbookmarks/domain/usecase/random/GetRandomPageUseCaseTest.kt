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

class GetRandomPageUseCaseTest {

    @Mock
    private lateinit var quranRepository: QuranRepository

    private lateinit var getRandomPageUseCase: GetRandomPageUseCase

    private val testVerses = listOf(
        VerseMetadata(
            text = "Test ayah 1",
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
        ),
        VerseMetadata(
            text = "Test ayah 2",
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
            globalAyahNumber = 2,
            ayahInSurah = 2
        )
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getRandomPageUseCase = GetRandomPageUseCase(quranRepository)
    }

    @Test
    fun `invoke returns success with list of verses`() = runTest {
        // Given
        `when`(quranRepository.getPageMetadata(any())).thenReturn(Result.success(testVerses))

        // When
        val result = getRandomPageUseCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testVerses, result.getOrNull())
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Given
        val error = Exception("Network error")
        `when`(quranRepository.getPageMetadata(any())).thenReturn(Result.failure(error))

        // When
        val result = getRandomPageUseCase()

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke generates page number in valid range`() = runTest {
        // Given
        var capturedPageNumber = 0
        `when`(quranRepository.getPageMetadata(any())).thenAnswer { invocation ->
            capturedPageNumber = invocation.getArgument(0)
            Result.success(testVerses.map { it.copy(page = capturedPageNumber) })
        }

        // When
        repeat(100) {
            getRandomPageUseCase()

            // Then
            assertTrue("Page number $capturedPageNumber is out of range", capturedPageNumber in 1..604)
        }
    }

    @Test
    fun `invoke returns empty list when page has no verses`() = runTest {
        // Given
        `when`(quranRepository.getPageMetadata(any())).thenReturn(Result.success(emptyList()))

        // When
        val result = getRandomPageUseCase()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }
}
