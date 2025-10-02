package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.ScheduleBookmarkCacheUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class UpdateBookmarkUseCaseTest {

    @Mock
    private lateinit var repository: BookmarkRepository

    @Mock
    private lateinit var scheduleBookmarkCacheUseCase: ScheduleBookmarkCacheUseCase

    private lateinit var updateBookmarkUseCase: UpdateBookmarkUseCase

    private val sampleBookmark = Bookmark(
        id = 1L,
        type = BookmarkType.AYAH,
        startSurah = 2,
        startAyah = 255,
        endSurah = 2,
        endAyah = 255,
        description = "Ayat al-Kursi",
        tags = listOf("important")
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        updateBookmarkUseCase = UpdateBookmarkUseCase(repository, scheduleBookmarkCacheUseCase)
    }

    @Test
    fun `invoke should return success when update succeeds with valid AYAH bookmark`() = runTest {
        // Given
        whenever(repository.updateBookmark(any())).thenReturn(Unit)

        // When
        val result = updateBookmarkUseCase(sampleBookmark)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).updateBookmark(argThat { bookmark ->
            bookmark.updatedAt > sampleBookmark.updatedAt
        })
    }

    @Test
    fun `invoke should return success when update succeeds with valid RANGE bookmark`() = runTest {
        // Given
        val rangeBookmark = sampleBookmark.copy(
            type = BookmarkType.RANGE,
            startAyah = 1,
            endAyah = 5
        )
        whenever(repository.updateBookmark(any())).thenReturn(Unit)

        // When
        val result = updateBookmarkUseCase(rangeBookmark)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).updateBookmark(any())
    }

    @Test
    fun `invoke should return success when update succeeds with valid SURAH bookmark`() = runTest {
        // Given
        val surahBookmark = sampleBookmark.copy(
            type = BookmarkType.SURAH,
            startAyah = 1,
            endAyah = 7
        )
        whenever(repository.updateBookmark(any())).thenReturn(Unit)

        // When
        val result = updateBookmarkUseCase(surahBookmark)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).updateBookmark(any())
    }

    @Test
    fun `invoke should return failure when surah number is invalid - too low`() = runTest {
        // Given
        val invalidBookmark = sampleBookmark.copy(startSurah = 0)

        // When
        val result = updateBookmarkUseCase(invalidBookmark)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Invalid surah number", result.exceptionOrNull()?.message)
        verify(repository, never()).updateBookmark(any())
    }

    @Test
    fun `invoke should return failure when surah number is invalid - too high`() = runTest {
        // Given
        val invalidBookmark = sampleBookmark.copy(startSurah = 115)

        // When
        val result = updateBookmarkUseCase(invalidBookmark)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Invalid surah number", result.exceptionOrNull()?.message)
        verify(repository, never()).updateBookmark(any())
    }

    @Test
    fun `invoke should return failure when ayah number is invalid`() = runTest {
        // Given
        val invalidBookmark = sampleBookmark.copy(startAyah = 0)

        // When
        val result = updateBookmarkUseCase(invalidBookmark)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Invalid ayah number", result.exceptionOrNull()?.message)
        verify(repository, never()).updateBookmark(any())
    }

    @Test
    fun `invoke should return failure when range end ayah is not greater than start`() = runTest {
        // Given
        val invalidRangeBookmark = sampleBookmark.copy(
            type = BookmarkType.RANGE,
            startAyah = 10,
            endAyah = 10 // Same as start, should be greater
        )

        // When
        val result = updateBookmarkUseCase(invalidRangeBookmark)

        // Then
        assertTrue(result.isFailure)
        assertEquals("End ayah must be greater than start ayah", result.exceptionOrNull()?.message)
        verify(repository, never()).updateBookmark(any())
    }

    @Test
    fun `invoke should return failure when range end ayah is less than start`() = runTest {
        // Given
        val invalidRangeBookmark = sampleBookmark.copy(
            type = BookmarkType.RANGE,
            startAyah = 10,
            endAyah = 5 // Less than start
        )

        // When
        val result = updateBookmarkUseCase(invalidRangeBookmark)

        // Then
        assertTrue(result.isFailure)
        assertEquals("End ayah must be greater than start ayah", result.exceptionOrNull()?.message)
        verify(repository, never()).updateBookmark(any())
    }

    @Test
    fun `invoke should return failure when repository throws exception`() = runTest {
        // Given
        val exception = RuntimeException("Database error")
        whenever(repository.updateBookmark(any())).thenThrow(exception)

        // When
        val result = updateBookmarkUseCase(sampleBookmark)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        verify(repository).updateBookmark(any())
    }

    @Test
    fun `invoke should update timestamp when updating bookmark`() = runTest {
        // Given
        val originalTimestamp = sampleBookmark.updatedAt
        whenever(repository.updateBookmark(any())).thenReturn(Unit)

        // When
        updateBookmarkUseCase(sampleBookmark)

        // Then
        verify(repository).updateBookmark(argThat { bookmark ->
            bookmark.updatedAt > originalTimestamp
        })
    }
}