package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class CreateProfileUseCaseTest {

    @Mock
    private lateinit var repository: BookmarkRepository

    private lateinit var useCase: CreateProfileUseCase

    private val testProfile = BookmarkGroup(
        id = 0L,
        name = "Test Profile",
        description = "Test Description",
        color = 0xFF6B73FF.toInt(),
        reciterEdition = "ar.alafasy"
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        useCase = CreateProfileUseCase(repository)
    }

    @Test
    fun `should return success when repository succeeds`() = runTest {
        // Given
        whenever(repository.insertGroup(testProfile)).thenReturn(1L)

        // When
        val result = useCase(testProfile)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        verify(repository).insertGroup(testProfile)
    }

    @Test
    fun `should return failure when profile name is blank`() = runTest {
        // Given
        val blankNameProfile = testProfile.copy(name = "")

        // When
        val result = useCase(blankNameProfile)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Profile name cannot be empty", result.exceptionOrNull()?.message)
        verify(repository, never()).insertGroup(any())
    }

    @Test
    fun `should return failure when profile name is whitespace only`() = runTest {
        // Given
        val whitespaceNameProfile = testProfile.copy(name = "   ")

        // When
        val result = useCase(whitespaceNameProfile)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Profile name cannot be empty", result.exceptionOrNull()?.message)
        verify(repository, never()).insertGroup(any())
    }

    @Test
    fun `should return failure when repository throws exception`() = runTest {
        // Given
        whenever(repository.insertGroup(testProfile)).thenThrow(RuntimeException("Database error"))

        // When
        val result = useCase(testProfile)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Database error", result.exceptionOrNull()?.message)
        verify(repository).insertGroup(testProfile)
    }

    @Test
    fun `should accept profile with valid name`() = runTest {
        // Given
        whenever(repository.insertGroup(testProfile)).thenReturn(1L)

        // When
        val result = useCase(testProfile)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).insertGroup(testProfile)
    }
}