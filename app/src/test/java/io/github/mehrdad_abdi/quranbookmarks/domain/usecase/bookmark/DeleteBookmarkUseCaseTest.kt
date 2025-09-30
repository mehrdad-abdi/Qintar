package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache.CleanupOrphanedCacheUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class DeleteBookmarkUseCaseTest {

    @Mock
    private lateinit var repository: BookmarkRepository

    @Mock
    private lateinit var cleanupOrphanedCacheUseCase: CleanupOrphanedCacheUseCase

    private lateinit var deleteBookmarkUseCase: DeleteBookmarkUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        deleteBookmarkUseCase = DeleteBookmarkUseCase(repository, cleanupOrphanedCacheUseCase)
    }

    @Test
    fun `invoke should return success when deletion succeeds`() = runTest {
        // Given
        val bookmarkId = 123L
        whenever(repository.deleteBookmarkById(bookmarkId)).thenReturn(Unit)

        // When
        val result = deleteBookmarkUseCase(bookmarkId)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).deleteBookmarkById(bookmarkId)
    }

    @Test
    fun `invoke should return failure when repository throws exception`() = runTest {
        // Given
        val bookmarkId = 123L
        val exception = RuntimeException("Database error")
        whenever(repository.deleteBookmarkById(bookmarkId)).thenThrow(exception)

        // When
        val result = deleteBookmarkUseCase(bookmarkId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        verify(repository).deleteBookmarkById(bookmarkId)
    }
}