package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.CreateProfileUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.notification.ScheduleDailyNotificationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class AddProfileViewModelTest {

    @Mock
    private lateinit var createProfileUseCase: CreateProfileUseCase

    @Mock
    private lateinit var scheduleDailyNotificationUseCase: ScheduleDailyNotificationUseCase

    private lateinit var viewModel: AddProfileViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = AddProfileViewModel(createProfileUseCase, scheduleDailyNotificationUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should have default values`() {
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals("", state.description)
        assertEquals(0, state.selectedColorIndex)
        assertTrue(state.audioEnabled) // Default to true
        assertFalse(state.notificationEnabled) // Default to false
        assertEquals("07:00", state.notificationTime) // Default time
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.nameError)
    }

    @Test
    fun `updateName should update name and clear name error`() = runTest {
        // Given
        viewModel.updateName("Test Profile")

        // Then
        val state = viewModel.uiState.value
        assertEquals("Test Profile", state.name)
        assertNull(state.nameError)
    }

    @Test
    fun `updateDescription should update description`() = runTest {
        // Given
        viewModel.updateDescription("Test Description")

        // Then
        assertEquals("Test Description", viewModel.uiState.value.description)
    }

    @Test
    fun `updateColor should update selected color index`() = runTest {
        // Given
        viewModel.updateColor(3)

        // Then
        assertEquals(3, viewModel.uiState.value.selectedColorIndex)
    }

    @Test
    fun `updateAudioEnabled should update audio enabled state`() = runTest {
        // Given - initially true by default
        assertTrue(viewModel.uiState.value.audioEnabled)

        // When
        viewModel.updateAudioEnabled(false)

        // Then
        assertFalse(viewModel.uiState.value.audioEnabled)

        // When - toggle back
        viewModel.updateAudioEnabled(true)

        // Then
        assertTrue(viewModel.uiState.value.audioEnabled)
    }

    @Test
    fun `createProfile should fail with blank name`() = runTest {
        // Given
        var successCalled = false
        val onSuccess: (Long) -> Unit = { successCalled = true }

        // When
        viewModel.createProfile(onSuccess)

        // Then
        val state = viewModel.uiState.value
        assertEquals("Profile name is required", state.nameError)
        assertFalse(state.isLoading)
        assertFalse(successCalled)
        verify(createProfileUseCase, never()).invoke(any())
    }

    @Test
    fun `createProfile should succeed with valid name and audio enabled`() = runTest {
        // Given
        whenever(createProfileUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateName("Test Profile")
        viewModel.updateDescription("Test Description")
        viewModel.updateColor(2)
        viewModel.updateAudioEnabled(true)

        var successProfileId: Long? = null
        val onSuccess: (Long) -> Unit = { successProfileId = it }

        // When
        viewModel.createProfile(onSuccess)
        advanceUntilIdle()

        // Then
        assertEquals(1L, successProfileId)
        verify(createProfileUseCase).invoke(argThat { profile ->
            profile.name == "Test Profile" &&
            profile.description == "Test Description" &&
            profile.reciterEdition == AddProfileViewModel.DEFAULT_RECITER
        })
    }

    @Test
    fun `createProfile should succeed with valid name and audio disabled`() = runTest {
        // Given
        whenever(createProfileUseCase(any())).thenReturn(Result.success(2L))
        viewModel.updateName("Test Profile")
        viewModel.updateAudioEnabled(false)

        var successProfileId: Long? = null
        val onSuccess: (Long) -> Unit = { successProfileId = it }

        // When
        viewModel.createProfile(onSuccess)
        advanceUntilIdle()

        // Then
        assertEquals(2L, successProfileId)
        verify(createProfileUseCase).invoke(argThat { profile ->
            profile.name == "Test Profile" &&
            profile.reciterEdition == "none"
        })
    }

    @Test
    fun `createProfile should handle failure from use case`() = runTest {
        // Given
        whenever(createProfileUseCase(any())).thenReturn(Result.failure(Exception("Test error")))
        viewModel.updateName("Test Profile")

        var successCalled = false
        val onSuccess: (Long) -> Unit = { successCalled = true }

        // When
        viewModel.createProfile(onSuccess)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals("Test error", state.error)
        assertFalse(state.isLoading)
        assertFalse(successCalled)
    }

    @Test
    fun `createProfile should trim whitespace from name and description`() = runTest {
        // Given
        whenever(createProfileUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateName("  Test Profile  ")
        viewModel.updateDescription("  Test Description  ")

        var successCalled = false
        val onSuccess: (Long) -> Unit = { successCalled = true }

        // When
        viewModel.createProfile(onSuccess)
        advanceUntilIdle()

        // Then
        assertTrue(successCalled)
        verify(createProfileUseCase).invoke(argThat { profile ->
            profile.name == "Test Profile" &&
            profile.description == "Test Description"
        })
    }

    @Test
    fun `createProfile should show loading state during operation`() = runTest {
        // Given
        whenever(createProfileUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateName("Test Profile")

        // When
        viewModel.createProfile { }
        advanceUntilIdle()

        // Then - loading state should be false after completion
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `clearError should clear error state`() = runTest {
        // Given - set an error state
        whenever(createProfileUseCase(any())).thenReturn(Result.failure(Exception("Test error")))
        viewModel.updateName("Test")
        viewModel.createProfile { }
        advanceUntilIdle()

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `updateNotificationEnabled should update notification enabled state`() = runTest {
        // Given - initially false by default
        assertFalse(viewModel.uiState.value.notificationEnabled)

        // When
        viewModel.updateNotificationEnabled(true)

        // Then
        assertTrue(viewModel.uiState.value.notificationEnabled)

        // When - toggle back
        viewModel.updateNotificationEnabled(false)

        // Then
        assertFalse(viewModel.uiState.value.notificationEnabled)
    }

    @Test
    fun `updateNotificationTime should update notification time`() = runTest {
        // Given
        assertEquals("07:00", viewModel.uiState.value.notificationTime)

        // When
        viewModel.updateNotificationTime("14:30")

        // Then
        assertEquals("14:30", viewModel.uiState.value.notificationTime)
    }

    @Test
    fun `createProfile should schedule notification when enabled`() = runTest {
        // Given
        whenever(createProfileUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateName("Test Profile")
        viewModel.updateNotificationEnabled(true)
        viewModel.updateNotificationTime("09:00")

        var successProfileId: Long? = null
        val onSuccess: (Long) -> Unit = { successProfileId = it }

        // When
        viewModel.createProfile(onSuccess)
        advanceUntilIdle()

        // Then
        assertEquals(1L, successProfileId)
        verify(createProfileUseCase).invoke(argThat { profile ->
            profile.name == "Test Profile" &&
            profile.notificationSettings.enabled &&
            profile.notificationSettings.time == "09:00"
        })
        verify(scheduleDailyNotificationUseCase).invoke(argThat { profile ->
            profile.id == 1L &&
            profile.notificationSettings.enabled &&
            profile.notificationSettings.time == "09:00"
        })
    }

    @Test
    fun `createProfile should not schedule notification when disabled`() = runTest {
        // Given
        whenever(createProfileUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateName("Test Profile")
        viewModel.updateNotificationEnabled(false)

        var successProfileId: Long? = null
        val onSuccess: (Long) -> Unit = { successProfileId = it }

        // When
        viewModel.createProfile(onSuccess)
        advanceUntilIdle()

        // Then
        assertEquals(1L, successProfileId)
        verify(createProfileUseCase).invoke(argThat { profile ->
            profile.name == "Test Profile" &&
            !profile.notificationSettings.enabled
        })
        verify(scheduleDailyNotificationUseCase, never()).invoke(any())
    }

    @Test
    fun `default constants should be correct`() {
        assertEquals("ar.alafasy", AddProfileViewModel.DEFAULT_RECITER)
        assertEquals(64, AddProfileViewModel.DEFAULT_BITRATE)
    }
}