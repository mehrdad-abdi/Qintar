package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.CreateBookmarkUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.surah.GetAllSurahsUseCase
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
class AddBookmarkViewModelTest {

    @Mock
    private lateinit var createBookmarkUseCase: CreateBookmarkUseCase

    @Mock
    private lateinit var getAllSurahsUseCase: GetAllSurahsUseCase

    private lateinit var viewModel: AddBookmarkViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    // Test data
    private val testSurah1 = Surah(number = 1, name = "الفاتحة", englishName = "Al-Fatiha", englishNameTranslation = "The Opening", numberOfAyahs = 7, revelationType = "Meccan")
    private val testSurah2 = Surah(number = 2, name = "البقرة", englishName = "Al-Baqarah", englishNameTranslation = "The Cow", numberOfAyahs = 286, revelationType = "Medinan")
    private val invalidSurah = Surah(number = 200, name = "Invalid", englishName = "Invalid", englishNameTranslation = "Invalid", numberOfAyahs = 1, revelationType = "Unknown")

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = AddBookmarkViewModel(createBookmarkUseCase, getAllSurahsUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should have default values`() {
        val state = viewModel.uiState.value
        assertNull(state.selectedSurah)
        assertEquals("", state.ayahNumber)
        assertEquals("", state.endAyahNumber)
        assertEquals("", state.description)
        assertEquals(BookmarkType.AYAH, state.bookmarkType)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.surahError)
        assertNull(state.ayahError)
        assertNull(state.endAyahError)
    }

    @Test
    fun `updateSelectedSurah should update selected surah and clear error`() {
        // Given
        val testSurah = Surah(number = 2, name = "البقرة", englishName = "Al-Baqarah", englishNameTranslation = "The Cow", numberOfAyahs = 286, revelationType = "Medinan")
        viewModel.updateSelectedSurah(testSurah)

        // Then
        val state = viewModel.uiState.value
        assertEquals(testSurah, state.selectedSurah)
        assertNull(state.surahError)
    }

    @Test
    fun `updateAyahNumber should update ayah number and clear error`() {
        // Given
        viewModel.updateAyahNumber("255")

        // Then
        val state = viewModel.uiState.value
        assertEquals("255", state.ayahNumber)
        assertNull(state.ayahError)
    }

    @Test
    fun `updateEndAyahNumber should update end ayah number and clear error`() {
        // Given
        viewModel.updateEndAyahNumber("257")

        // Then
        val state = viewModel.uiState.value
        assertEquals("257", state.endAyahNumber)
        assertNull(state.endAyahError)
    }

    @Test
    fun `updateDescription should update description`() {
        // Given
        viewModel.updateDescription("Beautiful verse about Allah's knowledge")

        // Then
        assertEquals("Beautiful verse about Allah's knowledge", viewModel.uiState.value.description)
    }

    @Test
    fun `updateBookmarkType should update type and clear endAyah when not RANGE`() {
        // Given - set end ayah first
        viewModel.updateEndAyahNumber("10")
        assertEquals("10", viewModel.uiState.value.endAyahNumber)

        // When - change to AYAH type
        viewModel.updateBookmarkType(BookmarkType.AYAH)

        // Then
        val state = viewModel.uiState.value
        assertEquals(BookmarkType.AYAH, state.bookmarkType)
        assertEquals("", state.endAyahNumber) // Should be cleared
    }

    @Test
    fun `updateBookmarkType to RANGE should preserve endAyah`() {
        // Given - set end ayah first
        viewModel.updateEndAyahNumber("10")

        // When - change to RANGE type
        viewModel.updateBookmarkType(BookmarkType.RANGE)

        // Then
        val state = viewModel.uiState.value
        assertEquals(BookmarkType.RANGE, state.bookmarkType)
        assertEquals("10", state.endAyahNumber) // Should be preserved
    }

    @Test
    fun `createBookmark should fail with invalid surah number`() = runTest {
        // Given
        viewModel.updateSelectedSurah(invalidSurah) // Invalid surah number
        viewModel.updateAyahNumber("1")

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.surahError)
        assertTrue(state.surahError!!.contains("between 1 and 114"))
        assertFalse(successCalled)
        verify(createBookmarkUseCase, never()).invoke(any())
    }

    @Test
    fun `createBookmark should fail with invalid ayah number for AYAH type`() = runTest {
        // Given
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("0") // Invalid ayah number
        viewModel.updateBookmarkType(BookmarkType.AYAH)

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.ayahError)
        assertTrue(state.ayahError!!.contains("at least 1"))
        assertFalse(successCalled)
    }

    @Test
    fun `createBookmark should fail with invalid range - end ayah less than start`() = runTest {
        // Given
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("10")
        viewModel.updateEndAyahNumber("5") // End less than start
        viewModel.updateBookmarkType(BookmarkType.RANGE)

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.endAyahError)
        assertTrue(state.endAyahError!!.contains("greater than start ayah"))
        assertFalse(successCalled)
    }

    @Test
    fun `createBookmark should succeed with valid AYAH bookmark`() = runTest {
        // Given
        whenever(createBookmarkUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("255")
        viewModel.updateDescription("Ayat al-Kursi")
        viewModel.updateBookmarkType(BookmarkType.AYAH)

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)
        advanceUntilIdle()

        // Then
        assertTrue(successCalled)
        verify(createBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.groupId == 1L &&
            bookmark.type == BookmarkType.AYAH &&
            bookmark.startSurah == 2 &&
            bookmark.startAyah == 255 &&
            bookmark.endSurah == 2 &&
            bookmark.endAyah == 255 &&
            bookmark.description == "Ayat al-Kursi"
        })
    }

    @Test
    fun `createBookmark should succeed with valid RANGE bookmark`() = runTest {
        // Given
        whenever(createBookmarkUseCase(any())).thenReturn(Result.success(2L))
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("1")
        viewModel.updateEndAyahNumber("5")
        viewModel.updateBookmarkType(BookmarkType.RANGE)

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)
        advanceUntilIdle()

        // Then
        assertTrue(successCalled)
        verify(createBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.type == BookmarkType.RANGE &&
            bookmark.startSurah == 2 &&
            bookmark.startAyah == 1 &&
            bookmark.endAyah == 5
        })
    }

    @Test
    fun `createBookmark should succeed with valid SURAH bookmark`() = runTest {
        // Given
        whenever(createBookmarkUseCase(any())).thenReturn(Result.success(3L))
        viewModel.updateSelectedSurah(testSurah1)
        viewModel.updateBookmarkType(BookmarkType.SURAH)

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)
        advanceUntilIdle()

        // Then
        assertTrue(successCalled)
        verify(createBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.type == BookmarkType.SURAH &&
            bookmark.startSurah == 1 &&
            bookmark.startAyah == 1 &&
            bookmark.endAyah == 7 // Al-Fatihah has 7 ayahs
        })
    }

    @Test
    fun `createBookmark should handle use case failure`() = runTest {
        // Given
        whenever(createBookmarkUseCase(any())).thenReturn(Result.failure(Exception("Database error")))
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("255")

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals("Database error", state.error)
        assertFalse(state.isLoading)
        assertFalse(successCalled)
    }

    @Test
    fun `createBookmark should show loading state during operation`() = runTest {
        // Given
        whenever(createBookmarkUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("255")

        // When
        viewModel.createBookmark(1L) { }
        advanceUntilIdle()

        // Then - loading state should be false after completion
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `createBookmark should trim description whitespace`() = runTest {
        // Given
        whenever(createBookmarkUseCase(any())).thenReturn(Result.success(1L))
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("255")
        viewModel.updateDescription("  Beautiful verse  ")

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.createBookmark(1L, onSuccess)
        advanceUntilIdle()

        // Then
        verify(createBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.description == "Beautiful verse"
        })
    }

    @Test
    fun `clearError should clear error state`() = runTest {
        // Given - set an error state by creating invalid bookmark
        viewModel.updateSelectedSurah(invalidSurah) // Invalid
        viewModel.createBookmark(1L) { }
        assertTrue(viewModel.uiState.value.surahError != null || viewModel.uiState.value.error != null)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `constants should be correct`() {
        assertEquals(114, AddBookmarkViewModel.TOTAL_SURAHS)
        assertEquals(286, AddBookmarkViewModel.MAX_AYAH_IN_LONGEST_SURAH)
    }
}