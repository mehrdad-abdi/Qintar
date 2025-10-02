package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class GetBookmarkByIdUseCaseTest {

    @Mock
    private lateinit var repository: BookmarkRepository

    private lateinit var getBookmarkByIdUseCase: GetBookmarkByIdUseCase

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
        getBookmarkByIdUseCase = GetBookmarkByIdUseCase(repository)
    }

    @Test
    fun `invoke should return success when bookmark exists`() = runTest {
        // Given
        val bookmarkId = 1L
        whenever(repository.getBookmarkById(bookmarkId)).thenReturn(sampleBookmark)

        // When
        val result = getBookmarkByIdUseCase(bookmarkId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(sampleBookmark, result.getOrNull())
        verify(repository).getBookmarkById(bookmarkId)
    }

    @Test
    fun `invoke should return failure when bookmark does not exist`() = runTest {
        // Given
        val bookmarkId = 999L
        whenever(repository.getBookmarkById(bookmarkId)).thenReturn(null)

        // When
        val result = getBookmarkByIdUseCase(bookmarkId)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Bookmark not found", result.exceptionOrNull()?.message)
        verify(repository).getBookmarkById(bookmarkId)
    }

    @Test
    fun `invoke should return failure when repository throws exception`() = runTest {
        // Given
        val bookmarkId = 1L
        val exception = RuntimeException("Database error")
        whenever(repository.getBookmarkById(bookmarkId)).thenThrow(exception)

        // When
        val result = getBookmarkByIdUseCase(bookmarkId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        verify(repository).getBookmarkById(bookmarkId)
    }
}