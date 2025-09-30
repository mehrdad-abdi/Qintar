package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.profile

import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.NotificationSettings
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.GetProfileByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.profile.UpdateProfileUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.notification.ScheduleDailyNotificationUseCase
import io.github.mehrdad_abdi.quranbookmarks.presentation.theme.ProfileColors
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    @Mock
    private lateinit var getProfileByIdUseCase: GetProfileByIdUseCase

    @Mock
    private lateinit var updateProfileUseCase: UpdateProfileUseCase

    @Mock
    private lateinit var scheduleDailyNotificationUseCase: ScheduleDailyNotificationUseCase

    private lateinit var viewModel: EditProfileViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testProfile = BookmarkGroup(
        id = 1L,
        name = "Test Profile",
        description = "Test Description",
        color = ProfileColors[1].toArgb(), // Second color
        reciterEdition = "ar.alafasy",
        notificationSettings = NotificationSettings()
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = EditProfileViewModel(getProfileByIdUseCase, updateProfileUseCase, scheduleDailyNotificationUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val initialState = viewModel.uiState.first()

        assertEquals(false, initialState.isLoading)
        assertEquals(0L, initialState.profileId)
        assertEquals("", initialState.name)
        assertEquals("", initialState.description)
        assertEquals(0, initialState.selectedColorIndex)
        assertEquals(true, initialState.audioEnabled)
        assertNull(initialState.nameError)
        assertNull(initialState.error)
        assertNull(initialState.originalProfile)
    }

    @Test
    fun `loadProfile success updates UI state correctly`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(testProfile)

        // When
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.first()
        assertEquals(false, state.isLoading)
        assertEquals(1L, state.profileId)
        assertEquals("Test Profile", state.name)
        assertEquals("Test Description", state.description)
        assertEquals(1, state.selectedColorIndex) // Second color
        assertEquals(true, state.audioEnabled) // ar.alafasy means enabled
        assertNull(state.error)
        assertEquals(testProfile, state.originalProfile)

        verify(getProfileByIdUseCase).invoke(1L)
    }

    @Test
    fun `loadProfile with disabled audio sets audioEnabled to false`() = runTest {
        // Given
        val profileWithNoAudio = testProfile.copy(reciterEdition = "none")
        whenever(getProfileByIdUseCase(1L)).thenReturn(profileWithNoAudio)

        // When
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.first()
        assertEquals(false, state.audioEnabled)
    }

    @Test
    fun `loadProfile handles unknown color gracefully`() = runTest {
        // Given
        val profileWithUnknownColor = testProfile.copy(color = 0xFF123456.toInt()) // Custom color not in ProfileColors
        whenever(getProfileByIdUseCase(1L)).thenReturn(profileWithUnknownColor)

        // When
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.first()
        assertEquals(0, state.selectedColorIndex) // Should default to first color
    }

    @Test
    fun `loadProfile with null profile sets error state`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(null)

        // When
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.first()
        assertEquals(false, state.isLoading)
        assertEquals("Profile not found", state.error)
        assertNull(state.originalProfile)
    }

    @Test
    fun `loadProfile exception sets error state`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenThrow(RuntimeException("Network error"))

        // When
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.first()
        assertEquals(false, state.isLoading)
        assertEquals("Network error", state.error)
        assertNull(state.originalProfile)
    }

    @Test
    fun `updateName clears name error`() = runTest {
        // Given - set up initial state with loaded profile
        whenever(getProfileByIdUseCase(1L)).thenReturn(testProfile)
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // When
        viewModel.updateName("New Name")

        // Then
        val state = viewModel.uiState.first()
        assertEquals("New Name", state.name)
        assertNull(state.nameError)
    }

    @Test
    fun `updateDescription updates description`() = runTest {
        // When
        viewModel.updateDescription("New Description")

        // Then
        val state = viewModel.uiState.first()
        assertEquals("New Description", state.description)
    }

    @Test
    fun `updateColor updates selected color index`() = runTest {
        // When
        viewModel.updateColor(2)

        // Then
        val state = viewModel.uiState.first()
        assertEquals(2, state.selectedColorIndex)
    }

    @Test
    fun `updateAudioEnabled updates audio setting`() = runTest {
        // When
        viewModel.updateAudioEnabled(false)

        // Then
        val state = viewModel.uiState.first()
        assertEquals(false, state.audioEnabled)
    }

    @Test
    fun `updateProfile with empty name sets name error`() = runTest {
        // Given - set up initial state with loaded profile
        whenever(getProfileByIdUseCase(1L)).thenReturn(testProfile)
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // When
        viewModel.updateName("")
        var onSuccessCalled = false
        viewModel.updateProfile { onSuccessCalled = true }
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.first()
        assertEquals("Profile name is required", state.nameError)
        assertFalse(onSuccessCalled)
        verify(updateProfileUseCase, never()).invoke(any())
    }

    @Test
    fun `updateProfile success calls onSuccess and updates profile`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(testProfile)
        whenever(updateProfileUseCase(any())).thenReturn(Result.success(Unit))

        viewModel.loadProfile(1L)
        advanceUntilIdle()

        viewModel.updateName("Updated Name")
        viewModel.updateDescription("Updated Description")
        viewModel.updateColor(2)
        viewModel.updateAudioEnabled(false)

        // When
        var onSuccessCalled = false
        viewModel.updateProfile { onSuccessCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(onSuccessCalled)
        val state = viewModel.uiState.first()
        assertEquals(false, state.isLoading)
        assertNull(state.error)

        verify(updateProfileUseCase).invoke(
            testProfile.copy(
                name = "Updated Name",
                description = "Updated Description",
                color = ProfileColors[2].toArgb(),
                reciterEdition = "none" // Audio disabled
            )
        )
    }

    @Test
    fun `updateProfile with audio enabled sets correct reciter`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(testProfile)
        whenever(updateProfileUseCase(any())).thenReturn(Result.success(Unit))

        viewModel.loadProfile(1L)
        advanceUntilIdle()

        viewModel.updateAudioEnabled(true)

        // When
        var onSuccessCalled = false
        viewModel.updateProfile { onSuccessCalled = true }
        advanceUntilIdle()

        // Then
        verify(updateProfileUseCase).invoke(
            testProfile.copy(
                reciterEdition = "ar.alafasy" // Audio enabled with default reciter
            )
        )
    }

    @Test
    fun `updateProfile failure sets error state`() = runTest {
        // Given
        whenever(getProfileByIdUseCase(1L)).thenReturn(testProfile)
        whenever(updateProfileUseCase(any())).thenReturn(Result.failure(Exception("Update failed")))

        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // When
        var onSuccessCalled = false
        viewModel.updateProfile { onSuccessCalled = true }
        advanceUntilIdle()

        // Then
        assertFalse(onSuccessCalled)
        val state = viewModel.uiState.first()
        assertEquals(false, state.isLoading)
        assertEquals("Update failed", state.error)
    }

    @Test
    fun `updateProfile without original profile does nothing`() = runTest {
        // When - call updateProfile without loading a profile first
        var onSuccessCalled = false
        viewModel.updateProfile { onSuccessCalled = true }
        advanceUntilIdle()

        // Then
        assertFalse(onSuccessCalled)
        verify(updateProfileUseCase, never()).invoke(any())
    }

    @Test
    fun `clearError clears error state`() = runTest {
        // Given - set up error state
        whenever(getProfileByIdUseCase(1L)).thenThrow(RuntimeException("Error"))
        viewModel.loadProfile(1L)
        advanceUntilIdle()

        // Verify error is set
        assertEquals("Error", viewModel.uiState.first().error)

        // When
        viewModel.clearError()

        // Then
        val state = viewModel.uiState.first()
        assertNull(state.error)
    }
}