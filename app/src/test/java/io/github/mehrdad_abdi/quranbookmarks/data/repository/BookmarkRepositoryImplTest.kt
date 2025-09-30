package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkGroupDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.BookmarkGroupEntity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class BookmarkRepositoryImplTest {

    @Mock
    private lateinit var bookmarkGroupDao: BookmarkGroupDao

    @Mock
    private lateinit var bookmarkDao: BookmarkDao

    private lateinit var repository: BookmarkRepositoryImpl

    private val testGroupEntity = BookmarkGroupEntity(
        id = 1L,
        name = "Test Group",
        description = "Test Description",
        color = 0xFF6B73FF.toInt(),
        reciterEdition = "ar.alafasy",
        notificationEnabled = false,
        notificationSchedule = "DAILY",
        notificationTime = "07:00",
        notificationDays = "",
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private val testGroup = BookmarkGroup(
        id = 1L,
        name = "Test Group",
        description = "Test Description",
        color = 0xFF6B73FF.toInt(),
        reciterEdition = "ar.alafasy",
        notificationSettings = NotificationSettings(),
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = BookmarkRepositoryImpl(bookmarkGroupDao, bookmarkDao)
    }

    @Test
    fun `getAllGroups should return mapped domain objects`() = runTest {
        // Given
        whenever(bookmarkGroupDao.getAllGroups()).thenReturn(flowOf(listOf(testGroupEntity)))

        // When
        val result = repository.getAllGroups().first()

        // Then
        assertEquals(1, result.size)
        assertEquals(testGroup.name, result.first().name)
        assertEquals(testGroup.description, result.first().description)
        verify(bookmarkGroupDao).getAllGroups()
    }

    @Test
    fun `getGroupById should return mapped domain object`() = runTest {
        // Given
        whenever(bookmarkGroupDao.getGroupById(1L)).thenReturn(testGroupEntity)

        // When
        val result = repository.getGroupById(1L)

        // Then
        assertNotNull(result)
        assertEquals(testGroup.name, result?.name)
        verify(bookmarkGroupDao).getGroupById(1L)
    }

    @Test
    fun `getGroupById should return null when not found`() = runTest {
        // Given
        whenever(bookmarkGroupDao.getGroupById(1L)).thenReturn(null)

        // When
        val result = repository.getGroupById(1L)

        // Then
        assertNull(result)
        verify(bookmarkGroupDao).getGroupById(1L)
    }

    @Test
    fun `insertGroup should set timestamps for new group`() = runTest {
        // Given
        val newGroup = testGroup.copy(id = 0L, createdAt = 0L, updatedAt = 0L)
        whenever(bookmarkGroupDao.insertGroup(any())).thenReturn(1L)

        // When
        val result = repository.insertGroup(newGroup)

        // Then
        assertEquals(1L, result)
        verify(bookmarkGroupDao).insertGroup(argThat { entity ->
            entity.id == 0L &&
            entity.createdAt > 0L &&
            entity.updatedAt > 0L &&
            entity.createdAt == entity.updatedAt
        })
    }

    @Test
    fun `insertGroup should preserve creation time for existing group`() = runTest {
        // Given
        val existingGroup = testGroup.copy(createdAt = 1000L, updatedAt = 1000L)
        whenever(bookmarkGroupDao.insertGroup(any())).thenReturn(1L)

        // When
        val result = repository.insertGroup(existingGroup)

        // Then
        assertEquals(1L, result)
        verify(bookmarkGroupDao).insertGroup(argThat { entity ->
            entity.createdAt == 1000L &&
            entity.updatedAt > 1000L
        })
    }

    @Test
    fun `updateGroup should update timestamp`() = runTest {
        // Given
        val groupToUpdate = testGroup.copy(updatedAt = 1000L)

        // When
        repository.updateGroup(groupToUpdate)

        // Then
        verify(bookmarkGroupDao).updateGroup(argThat { entity ->
            entity.updatedAt > 1000L
        })
    }

    @Test
    fun `deleteGroup should call dao deleteGroup`() = runTest {
        // When
        repository.deleteGroup(testGroup)

        // Then
        verify(bookmarkGroupDao).deleteGroup(any())
    }

    @Test
    fun `deleteGroupById should call dao deleteGroupById`() = runTest {
        // When
        repository.deleteGroupById(1L)

        // Then
        verify(bookmarkGroupDao).deleteGroupById(1L)
    }

    @Test
    fun `getGroupCount should return dao count`() = runTest {
        // Given
        whenever(bookmarkGroupDao.getGroupCount()).thenReturn(5)

        // When
        val result = repository.getGroupCount()

        // Then
        assertEquals(5, result)
        verify(bookmarkGroupDao).getGroupCount()
    }
}